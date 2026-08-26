package io.artifactkeeper.gocd.debian;

/** One `.deb` file published to the pool, as recorded in the artifact metadata. */
final class PublishedFile {
    final String filename;
    final String packageName;
    final String poolLetter;
    final String url;
    final String sha256;
    final long sizeBytes;
    /** True when the pool already had this exact filename (HTTP 409) and the upload was skipped. */
    final boolean alreadyExisted;

    PublishedFile(String filename, String packageName, String poolLetter, String url, String sha256, long sizeBytes, boolean alreadyExisted) {
        this.filename = filename;
        this.packageName = packageName;
        this.poolLetter = poolLetter;
        this.url = url;
        this.sha256 = sha256;
        this.sizeBytes = sizeBytes;
        this.alreadyExisted = alreadyExisted;
    }
}
