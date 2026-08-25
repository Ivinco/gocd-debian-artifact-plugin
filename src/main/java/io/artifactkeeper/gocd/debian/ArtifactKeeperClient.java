package io.artifactkeeper.gocd.debian;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.function.Consumer;

/**
 * Talks to ArtifactKeeper's native Debian repository protocol directly
 * (the same {@code PUT /debian/{repo}/pool/...} contract ArtifactKeeper's
 * own end-to-end tests use) — no ArtifactKeeper-specific SDK exists, this
 * is plain HTTP.
 */
final class ArtifactKeeperClient {

    private static final String DEB_CONTENT_TYPE = "application/vnd.debian.binary-package";
    private static final Duration TIMEOUT = Duration.ofMinutes(5);

    private final HttpClient httpClient;

    ArtifactKeeperClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Uploads one `.deb` file to the store's pool and returns the metadata
     * to record for this publish. Throws {@link ArtifactKeeperException} on
     * any non-2xx response or transport failure.
     */
    PublishedFile publish(StoreConfig store, String component, Path file, Consumer<String> logger) {
        String filename = file.getFileName().toString();
        String packageName = DebianPoolPath.packageNameFromFilename(filename);
        String poolLetter = DebianPoolPath.poolLetterFor(packageName);
        String url = store.debianRepoBaseUrl() + "/pool/" + component + "/" + poolLetter + "/" + packageName + "/" + filename;

        long size;
        String sha256;
        try {
            size = Files.size(file);
            sha256 = sha256Hex(file);
        } catch (IOException e) {
            throw new ArtifactKeeperException("Failed to read '" + file + "': " + e.getMessage(), e);
        }

        logger.accept("Uploading " + filename + " (" + size + " bytes) to " + url);

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("Authorization", basicAuth(store.username, store.password))
                    .header("Content-Type", DEB_CONTENT_TYPE)
                    .PUT(HttpRequest.BodyPublishers.ofFile(file))
                    .build();
        } catch (java.io.FileNotFoundException e) {
            throw new ArtifactKeeperException("File '" + file + "' disappeared before it could be uploaded: " + e.getMessage(), e);
        }

        HttpResponse<String> response = send(request);
        if (response.statusCode() != 200 && response.statusCode() != 201) {
            throw new ArtifactKeeperException(
                    "Upload of '" + filename + "' to " + url + " failed: HTTP " + response.statusCode()
                            + " - " + truncate(response.body()));
        }

        logger.accept("Uploaded " + filename + " (HTTP " + response.statusCode() + ")");
        return new PublishedFile(filename, packageName, poolLetter, url, sha256, size);
    }

    /** Downloads a previously published file to {@code destDir}, keeping its filename. */
    void fetch(StoreConfig store, PublishedFile publishedFile, Path destDir, Consumer<String> logger) {
        try {
            Files.createDirectories(destDir);
        } catch (IOException e) {
            throw new ArtifactKeeperException("Failed to create destination directory '" + destDir + "': " + e.getMessage(), e);
        }

        Path dest = destDir.resolve(publishedFile.filename);
        logger.accept("Fetching " + publishedFile.url + " -> " + dest);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(publishedFile.url))
                .timeout(TIMEOUT)
                .header("Authorization", basicAuth(store.username, store.password))
                .GET()
                .build();

        HttpResponse<Path> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(dest));
        } catch (IOException e) {
            throw new ArtifactKeeperException("Failed to fetch '" + publishedFile.url + "': " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ArtifactKeeperException("Interrupted while fetching '" + publishedFile.url + "'", e);
        }

        if (response.statusCode() != 200) {
            throw new ArtifactKeeperException(
                    "Fetch of '" + publishedFile.url + "' failed: HTTP " + response.statusCode());
        }
        logger.accept("Fetched " + publishedFile.filename);
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ArtifactKeeperException("Request to " + request.uri() + " failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ArtifactKeeperException("Interrupted while calling " + request.uri(), e);
        }
    }

    private static String basicAuth(String username, String password) {
        String raw = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Hex(Path file) throws IOException {
        try (var in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            int read;
            while ((read = in.read(buf)) != -1) {
                digest.update(buf, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed available on every JDK; this cannot happen.
            throw new UncheckedIOException(new IOException(e));
        }
    }

    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > 500 ? body.substring(0, 500) + "..." : body;
    }
}
