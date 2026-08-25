package io.artifactkeeper.gocd.debian;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Per-job "artifacts: external:" configuration: which built files to publish
 * and which pool component to publish them into. One store can be reused by
 * many jobs, each with its own {@code Pattern}/{@code Component}.
 */
final class PublishConfig {

    static final String DEFAULT_COMPONENT = "main";

    static final List<ConfigProperty> METADATA = List.of(
            new ConfigProperty("Pattern", true, false),
            new ConfigProperty("Component", false, false)
    );

    final String pattern;
    final String component;

    PublishConfig(String pattern, String component) {
        this.pattern = pattern;
        this.component = (component == null || component.isBlank()) ? DEFAULT_COMPONENT : component.trim();
    }

    static PublishConfig fromMap(Map<String, String> config) {
        return new PublishConfig(config.get("Pattern"), config.get("Component"));
    }

    static List<ValidationError> validate(Map<String, String> config) {
        List<ValidationError> errors = new ArrayList<>();
        String pattern = config.get("Pattern");
        if (pattern == null || pattern.isBlank()) {
            errors.add(new ValidationError("Pattern", "Pattern must not be blank, e.g. '*.deb'"));
        }
        String component = config.get("Component");
        if (component != null && component.contains("/")) {
            errors.add(new ValidationError("Component", "Component must be a single path segment (e.g. 'main'), not a path"));
        }
        return errors;
    }
}
