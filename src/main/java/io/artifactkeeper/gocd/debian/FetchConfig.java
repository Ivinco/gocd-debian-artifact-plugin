package io.artifactkeeper.gocd.debian;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * "Fetch artifact" task configuration for a downstream job pulling the
 * `.deb` files a previous job published through this store back down onto
 * an agent (e.g. to run a package install smoke test).
 */
final class FetchConfig {

    static final List<ConfigProperty> METADATA = List.of(
            new ConfigProperty("DestinationOnAgent", false, false)
    );

    final String destinationOnAgent;

    FetchConfig(String destinationOnAgent) {
        this.destinationOnAgent = (destinationOnAgent == null || destinationOnAgent.isBlank())
                ? "."
                : destinationOnAgent.trim();
    }

    static FetchConfig fromMap(Map<String, String> config) {
        return new FetchConfig(config.get("DestinationOnAgent"));
    }

    static List<ValidationError> validate(Map<String, String> config) {
        List<ValidationError> errors = new ArrayList<>();
        String dest = config.get("DestinationOnAgent");
        if (dest != null && dest.startsWith("/")) {
            errors.add(new ValidationError("DestinationOnAgent", "DestinationOnAgent must be relative to the agent working directory"));
        }
        return errors;
    }
}
