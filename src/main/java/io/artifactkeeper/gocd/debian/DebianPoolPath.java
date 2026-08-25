package io.artifactkeeper.gocd.debian;

/**
 * dpkg pool layout helpers: derives the package name and the pool letter
 * directory from a `.deb` filename, per the standard Debian archive
 * convention (e.g. reprepro, Nexus, ArtifactKeeper all lay pools out the
 * same way): {@code pool/<component>/<letter>/<name>/<name>_<version>_<arch>.deb}.
 */
final class DebianPoolPath {

    private DebianPoolPath() {
    }

    /**
     * Extracts the source package name from a `.deb` filename, i.e.
     * everything before the first underscore: {@code name_version_arch.deb}
     * -&gt; {@code name}.
     */
    static String packageNameFromFilename(String filename) {
        String base = filename;
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        int underscore = base.indexOf('_');
        if (underscore <= 0) {
            throw new IllegalArgumentException(
                    "'" + filename + "' does not look like a .deb filename (expected name_version_arch.deb)");
        }
        return base.substring(0, underscore);
    }

    /**
     * The pool letter directory for a package name: the first letter, except
     * names starting with "lib" use "lib" plus the next letter (e.g.
     * "libssl" -&gt; "libs"), matching dpkg-scanpackages/reprepro/ftpmaster
     * convention.
     */
    static String poolLetterFor(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            throw new IllegalArgumentException("package name must not be empty");
        }
        String lower = packageName.toLowerCase();
        if (lower.startsWith("lib") && lower.length() > 3) {
            return lower.substring(0, 4);
        }
        return lower.substring(0, 1);
    }
}
