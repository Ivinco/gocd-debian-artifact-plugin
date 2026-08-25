package io.artifactkeeper.gocd.debian;

/**
 * One field of a store/publish/fetch config form, as GoCD's
 * {@code *.get-metadata} responses expect: {@code {"key": ..., "metadata":
 * {"required": ..., "secure": ...}}}.
 */
final class ConfigProperty {
    final String key;
    final boolean required;
    final boolean secure;

    ConfigProperty(String key, boolean required, boolean secure) {
        this.key = key;
        this.required = required;
        this.secure = secure;
    }
}
