package io.artifactkeeper.gocd.debian;

/** Any failure talking to ArtifactKeeper (network, auth, unexpected status). */
final class ArtifactKeeperException extends RuntimeException {
    ArtifactKeeperException(String message) {
        super(message);
    }

    ArtifactKeeperException(String message, Throwable cause) {
        super(message, cause);
    }
}
