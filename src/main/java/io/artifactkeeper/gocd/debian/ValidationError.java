package io.artifactkeeper.gocd.debian;

/** One {@code *.validate} response entry: {@code {"key": ..., "message": ...}}. */
final class ValidationError {
    final String key;
    final String message;

    ValidationError(String key, String message) {
        this.key = key;
        this.message = message;
    }
}
