package io.artifactkeeper.gocd.debian;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Resolves a job's {@code Pattern} config (e.g. {@code target/*.deb}, {@code **}{@code /*.deb}) against files on the agent. */
final class ArtifactResolver {

    private ArtifactResolver() {
    }

    static List<Path> resolve(Path workingDirectory, String pattern) {
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        List<Path> matches = new ArrayList<>();
        try {
            Files.walk(workingDirectory)
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        Path relative = workingDirectory.relativize(path);
                        if (matcher.matches(relative)) {
                            matches.add(path);
                        }
                    });
        } catch (IOException e) {
            throw new ArtifactKeeperException(
                    "Failed to scan '" + workingDirectory + "' for pattern '" + pattern + "': " + e.getMessage(), e);
        }
        matches.sort(Comparator.comparing(Path::toString));
        if (matches.isEmpty()) {
            throw new ArtifactKeeperException(
                    "Pattern '" + pattern + "' matched no files under " + workingDirectory);
        }
        return matches;
    }
}
