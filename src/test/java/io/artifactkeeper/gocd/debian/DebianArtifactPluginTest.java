package io.artifactkeeper.gocd.debian;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.thoughtworks.go.plugin.api.GoApplicationAccessor;
import com.thoughtworks.go.plugin.api.request.GoApiRequest;
import com.thoughtworks.go.plugin.api.response.GoApiResponse;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebianArtifactPluginTest {

    private final DebianArtifactPlugin plugin = new DebianArtifactPlugin();
    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        // publish-artifact/fetch-artifact only need a live accessor when the
        // best-effort console logger actually fires; this one always throws,
        // to prove ConsoleLogger really swallows the failure rather than
        // letting it escape and fail the publish/fetch it was narrating.
        plugin.initializeGoApplicationAccessor(new GoApplicationAccessor() {
            @Override
            public GoApiResponse submit(GoApiRequest request) {
                throw new RuntimeException("no server in this test, console-log must be swallowed");
            }
        });
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void get_icon_returns_the_bundled_svg() throws Exception {
        GoPluginApiResponse response = plugin.handle(new FakeGoPluginApiRequest("cd.go.artifact.get-icon", ""));
        assertEquals(200, response.responseCode());
        JsonObject body = JsonUtil.GSON.fromJson(response.responseBody(), JsonObject.class);
        assertEquals("image/svg+xml", body.get("content_type").getAsString());
        assertTrue(body.get("data").getAsString().length() > 0);
    }

    @Test
    void get_capabilities_is_an_empty_object() throws Exception {
        GoPluginApiResponse response = plugin.handle(new FakeGoPluginApiRequest("cd.go.artifact.get-capabilities", ""));
        assertEquals(200, response.responseCode());
        assertEquals("{}", response.responseBody());
    }

    @Test
    void store_get_metadata_lists_the_four_fields() throws Exception {
        GoPluginApiResponse response = plugin.handle(new FakeGoPluginApiRequest("cd.go.artifact.store.get-metadata", ""));
        assertEquals(200, response.responseCode());
        JsonArray array = JsonUtil.GSON.fromJson(response.responseBody(), JsonArray.class);
        assertEquals(4, array.size());
        assertEquals("RegistryUrl", array.get(0).getAsJsonObject().get("key").getAsString());
        assertTrue(array.get(3).getAsJsonObject().getAsJsonObject("metadata").get("secure").getAsBoolean());
    }

    @Test
    void store_validate_flags_a_blank_registry_url() throws Exception {
        String body = "{\"RegistryUrl\":\"\",\"RepositoryKey\":\"custom\",\"Username\":\"u\",\"Password\":\"p\"}";
        GoPluginApiResponse response = plugin.handle(new FakeGoPluginApiRequest("cd.go.artifact.store.validate", body));
        JsonArray errors = JsonUtil.GSON.fromJson(response.responseBody(), JsonArray.class);
        assertEquals(1, errors.size());
        assertEquals("RegistryUrl", errors.get(0).getAsJsonObject().get("key").getAsString());
    }

    @Test
    void store_validate_accepts_a_complete_config() throws Exception {
        String body = "{\"RegistryUrl\":\"https://artifactkeeper.example.com\",\"RepositoryKey\":\"custom\",\"Username\":\"u\",\"Password\":\"p\"}";
        GoPluginApiResponse response = plugin.handle(new FakeGoPluginApiRequest("cd.go.artifact.store.validate", body));
        JsonArray errors = JsonUtil.GSON.fromJson(response.responseBody(), JsonArray.class);
        assertEquals(0, errors.size());
    }

    @Test
    void publish_validate_requires_a_pattern() throws Exception {
        GoPluginApiResponse response = plugin.handle(new FakeGoPluginApiRequest("cd.go.artifact.publish.validate", "{}"));
        JsonArray errors = JsonUtil.GSON.fromJson(response.responseBody(), JsonArray.class);
        assertEquals(1, errors.size());
        assertEquals("Pattern", errors.get(0).getAsJsonObject().get("key").getAsString());
    }

    @Test
    void unknown_request_is_unhandled() {
        assertTrue(assertThrowsUnhandled());
    }

    private boolean assertThrowsUnhandled() {
        try {
            plugin.handle(new FakeGoPluginApiRequest("cd.go.artifact.something-new", ""));
            return false;
        } catch (com.thoughtworks.go.plugin.api.exceptions.UnhandledRequestTypeException e) {
            return true;
        }
    }

    @Test
    void publish_artifact_uploads_matched_files_and_returns_pool_metadata() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(201));

        Path workDir = Files.createTempDirectory("agent-work");
        Files.writeString(workDir.resolve("sample-app_1.1-1_all.deb"), "deb-bytes");
        Files.writeString(workDir.resolve("readme.txt"), "not a deb");

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("agent_working_directory", workDir.toString());

        JsonObject artifactStore = new JsonObject();
        artifactStore.addProperty("id", "artifact-keeper");
        JsonObject storeConfiguration = new JsonObject();
        storeConfiguration.addProperty("RegistryUrl", server.url("/").toString());
        storeConfiguration.addProperty("RepositoryKey", "custom");
        storeConfiguration.addProperty("Username", "svc-gocd");
        storeConfiguration.addProperty("Password", "token");
        artifactStore.add("configuration", storeConfiguration);
        requestBody.add("artifact_store", artifactStore);

        JsonObject artifactPlan = new JsonObject();
        artifactPlan.addProperty("id", "consumer-deb");
        artifactPlan.addProperty("storeId", "artifact-keeper");
        JsonObject planConfiguration = new JsonObject();
        planConfiguration.addProperty("Pattern", "*.deb");
        artifactPlan.add("configuration", planConfiguration);
        requestBody.add("artifact_plan", artifactPlan);

        GoPluginApiResponse response = plugin.handle(
                new FakeGoPluginApiRequest("cd.go.artifact.publish-artifact", JsonUtil.GSON.toJson(requestBody)));

        assertEquals(200, response.responseCode());
        JsonObject responseBody = JsonUtil.GSON.fromJson(response.responseBody(), JsonObject.class);
        JsonObject metadata = responseBody.getAsJsonObject("metadata");
        assertEquals("main", metadata.get("component").getAsString());
        JsonArray files = metadata.getAsJsonArray("files");
        assertEquals(1, files.size());
        JsonObject file = files.get(0).getAsJsonObject();
        assertEquals("sample-app_1.1-1_all.deb", file.get("filename").getAsString());
        assertEquals("sample-app", file.get("packageName").getAsString());
        assertTrue(file.get("url").getAsString().endsWith("/debian/custom/pool/main/s/sample-app/sample-app_1.1-1_all.deb"));

        assertEquals("PUT", server.takeRequest().getMethod());
    }
}
