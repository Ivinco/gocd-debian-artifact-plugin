package io.artifactkeeper.gocd.debian;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtifactKeeperClientTest {

    private MockWebServer server;
    private ArtifactKeeperClient client;
    private final List<String> logLines = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new ArtifactKeeperClient();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private StoreConfig storeConfig() {
        return new StoreConfig(server.url("/").toString(), "custom", "svc-gocd", "s3cr3t-token");
    }

    @Test
    void publish_puts_the_file_at_the_dpkg_pool_path_with_basic_auth() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(201));

        Path deb = Files.createTempFile("sample-app_1.1-1_all", ".deb");
        Files.writeString(deb, "fake-deb-contents");
        Path renamed = deb.resolveSibling("sample-app_1.1-1_all.deb");
        Files.move(deb, renamed);

        PublishedFile result = client.publish(storeConfig(), "main", renamed, logLines::add);

        RecordedRequest recorded = server.takeRequest(5, TimeUnit.SECONDS);
        assertTrue(recorded != null, "expected exactly one HTTP request");
        assertEquals("PUT", recorded.getMethod());
        assertEquals("/debian/custom/pool/main/s/sample-app/sample-app_1.1-1_all.deb", recorded.getPath());
        assertEquals("application/vnd.debian.binary-package", recorded.getHeader("Content-Type"));
        String expectedAuth = "Basic " + Base64.getEncoder().encodeToString("svc-gocd:s3cr3t-token".getBytes());
        assertEquals(expectedAuth, recorded.getHeader("Authorization"));

        assertEquals("sample-app_1.1-1_all.deb", result.filename);
        assertEquals("sample-app", result.packageName);
        assertEquals("s", result.poolLetter);
        assertTrue(result.url.endsWith("/debian/custom/pool/main/s/sample-app/sample-app_1.1-1_all.deb"));
        assertEquals(64, result.sha256.length());
        assertFalse(result.alreadyExisted);
        assertTrue(logLines.stream().anyMatch(l -> l.contains("Uploading")));

        Files.deleteIfExists(renamed);
    }

    @Test
    void publish_treats_409_as_already_existing_not_a_failure() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(409).setBody("Package already exists"));

        Path deb = Files.createTempFile("sample-app_1.1-1_all", ".deb");
        Path renamed = deb.resolveSibling("sample-app_1.1-1_all.deb");
        Files.move(deb, renamed);

        PublishedFile result = client.publish(storeConfig(), "main", renamed, logLines::add);

        assertTrue(result.alreadyExisted);
        assertEquals("sample-app_1.1-1_all.deb", result.filename);
        assertTrue(logLines.stream().anyMatch(l -> l.contains("already exists") && l.contains("not re-uploaded")));

        Files.deleteIfExists(renamed);
    }

    @Test
    void publish_raises_on_a_non_2xx_response() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(403).setBody("forbidden"));

        Path deb = Files.createTempFile("pkg_1.0-1_all", ".deb");
        Path renamed = deb.resolveSibling("pkg_1.0-1_all.deb");
        Files.move(deb, renamed);

        ArtifactKeeperException ex = assertThrows(ArtifactKeeperException.class,
                () -> client.publish(storeConfig(), "main", renamed, logLines::add));
        assertTrue(ex.getMessage().contains("403"));
        assertTrue(ex.getMessage().contains("forbidden"));

        Files.deleteIfExists(renamed);
    }

    @Test
    void fetch_downloads_the_file_to_the_destination_directory() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("deb-bytes"));

        Path destDir = Files.createTempDirectory("fetch-dest");
        PublishedFile pf = new PublishedFile(
                "pkg_1.0-1_all.deb", "pkg", "p",
                server.url("/debian/custom/pool/main/p/pkg/pkg_1.0-1_all.deb").toString(),
                "irrelevant", 9L, false);

        client.fetch(storeConfig(), pf, destDir, logLines::add);

        Path fetched = destDir.resolve("pkg_1.0-1_all.deb");
        assertTrue(Files.exists(fetched));
        assertEquals("deb-bytes", Files.readString(fetched));

        RecordedRequest recorded = server.takeRequest(5, TimeUnit.SECONDS);
        assertEquals("GET", recorded.getMethod());
    }
}
