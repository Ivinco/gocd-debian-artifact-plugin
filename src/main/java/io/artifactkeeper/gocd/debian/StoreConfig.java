package io.artifactkeeper.gocd.debian;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Artifact Store configuration: one ArtifactKeeper server + hosted Debian
 * repository + publishing credential, shared by every job that publishes
 * through this store (mirrors {@code cd.go.artifact.docker.registry}'s
 * RegistryURL/Username/Password store fields).
 */
final class StoreConfig {

    static final List<ConfigProperty> METADATA = List.of(
            new ConfigProperty("RegistryUrl", true, false),
            new ConfigProperty("RepositoryKey", true, false),
            new ConfigProperty("Username", true, false),
            new ConfigProperty("Password", true, true)
    );

    final String registryUrl;
    final String repositoryKey;
    final String username;
    final String password;

    StoreConfig(String registryUrl, String repositoryKey, String username, String password) {
        this.registryUrl = registryUrl;
        this.repositoryKey = repositoryKey;
        this.username = username;
        this.password = password;
    }

    static StoreConfig fromMap(Map<String, String> config) {
        return new StoreConfig(
                trimOrNull(config.get("RegistryUrl")),
                trimOrNull(config.get("RepositoryKey")),
                trimOrNull(config.get("Username")),
                config.get("Password")
        );
    }

    private static String trimOrNull(String value) {
        return value == null ? null : value.trim();
    }

    /** The base URL for this store's repository, with no trailing slash. */
    String debianRepoBaseUrl() {
        String base = registryUrl.endsWith("/") ? registryUrl.substring(0, registryUrl.length() - 1) : registryUrl;
        return base + "/debian/" + repositoryKey;
    }

    static List<ValidationError> validate(Map<String, String> config) {
        List<ValidationError> errors = new ArrayList<>();

        String registryUrl = trimOrNull(config.get("RegistryUrl"));
        if (registryUrl == null || registryUrl.isEmpty()) {
            errors.add(new ValidationError("RegistryUrl", "RegistryUrl must not be blank"));
        } else {
            try {
                URI uri = new URI(registryUrl);
                if (uri.getScheme() == null || !uri.getScheme().startsWith("http")) {
                    errors.add(new ValidationError("RegistryUrl", "RegistryUrl must be an http(s) URL"));
                }
            } catch (URISyntaxException e) {
                errors.add(new ValidationError("RegistryUrl", "RegistryUrl is not a valid URL: " + e.getMessage()));
            }
        }

        String repositoryKey = trimOrNull(config.get("RepositoryKey"));
        if (repositoryKey == null || repositoryKey.isEmpty()) {
            errors.add(new ValidationError("RepositoryKey", "RepositoryKey must not be blank"));
        }

        String username = trimOrNull(config.get("Username"));
        if (username == null || username.isEmpty()) {
            errors.add(new ValidationError("Username", "Username must not be blank"));
        }

        String password = config.get("Password");
        if (password == null || password.isEmpty()) {
            errors.add(new ValidationError("Password", "Password must not be blank"));
        }

        return errors;
    }
}
