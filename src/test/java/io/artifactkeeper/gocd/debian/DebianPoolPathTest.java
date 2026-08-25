package io.artifactkeeper.gocd.debian;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DebianPoolPathTest {

    @Test
    void extracts_package_name_before_first_underscore() {
        assertEquals("sample-app", DebianPoolPath.packageNameFromFilename("sample-app_1.1-1_all.deb"));
    }

    @Test
    void strips_directory_components_first() {
        assertEquals("sample-app", DebianPoolPath.packageNameFromFilename("target/sample-app_1.1-1_all.deb"));
    }

    @Test
    void rejects_filenames_without_an_underscore() {
        assertThrows(IllegalArgumentException.class, () -> DebianPoolPath.packageNameFromFilename("noversion.deb"));
    }

    @Test
    void ordinary_package_uses_first_letter() {
        assertEquals("s", DebianPoolPath.poolLetterFor("sample-app"));
        assertEquals("n", DebianPoolPath.poolLetterFor("nginx"));
    }

    @Test
    void lib_prefixed_package_uses_first_four_letters() {
        assertEquals("libs", DebianPoolPath.poolLetterFor("libssl1.1"));
        assertEquals("libc", DebianPoolPath.poolLetterFor("libc6"));
    }

    @Test
    void a_package_literally_named_lib_does_not_overrun() {
        // length 3, the ">3" guard must stop this from taking a 4th
        // character that doesn't exist.
        assertEquals("l", DebianPoolPath.poolLetterFor("lib"));
    }
}
