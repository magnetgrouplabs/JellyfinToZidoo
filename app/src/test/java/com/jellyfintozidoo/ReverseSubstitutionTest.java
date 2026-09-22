package com.jellyfintozidoo;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for reverse path substitution logic.
 * Converts Zidoo SMB paths back to server-side paths for Jellyfin item lookup.
 */
public class ReverseSubstitutionTest {

    // Helper to build rules array: each rule is {pathToReplace, replacedWith}
    // Forward substitution: pathToReplace -> replacedWith
    // Reverse: replacedWith -> pathToReplace
    private String[][] rules(String[]... pairs) {
        return pairs;
    }

    private String[] rule(String pathToReplace, String replacedWith) {
        return new String[]{pathToReplace, replacedWith};
    }

    @Test
    public void reverseSubstitution_withCredentials_stripsAndReverses() {
        String[][] r = rules(rule("/media", "smb://192.168.1.10/share/media"));
        String result = JellyfinApi.reverseSubstitution(
                "smb://user:pass@192.168.1.10/share/media/Shows/Breaking Bad/S01E01.mkv", r);
        assertEquals("/media/Shows/Breaking Bad/S01E01.mkv", result);
    }

    @Test
    public void reverseSubstitution_withoutCredentials_reverses() {
        String[][] r = rules(rule("/media", "smb://192.168.1.10/share/media"));
        String result = JellyfinApi.reverseSubstitution(
                "smb://192.168.1.10/share/media/Shows/ep.mkv", r);
        assertEquals("/media/Shows/ep.mkv", result);
    }

    @Test
    public void reverseSubstitution_noMatchingRule_returnsNull() {
        String[][] r = rules(rule("/media", "smb://host/share"));
        String result = JellyfinApi.reverseSubstitution(
                "smb://user:pass@host/data/Movies/film.mkv", r);
        assertNull(result);
    }

    @Test
    public void reverseSubstitution_nullInput_returnsNull() {
        String[][] r = rules(rule("/media", "smb://host/share"));
        assertNull(JellyfinApi.reverseSubstitution(null, r));
    }

    @Test
    public void reverseSubstitution_emptyInput_returnsNull() {
        String[][] r = rules(rule("/media", "smb://host/share"));
        assertNull(JellyfinApi.reverseSubstitution("", r));
    }

    @Test
    public void reverseSubstitution_uriEncodedPath_decodesSpaces() {
        String[][] r = rules(rule("/media", "smb://host/share"));
        String result = JellyfinApi.reverseSubstitution(
                "smb://user:pass@host/share/Shows/My%20Show/ep.mkv", r);
        assertEquals("/media/Shows/My Show/ep.mkv", result);
    }

    @Test
    public void reverseSubstitution_multipleRules_firstMatchWins() {
        String[][] r = rules(
                rule("/movies", "smb://host/movies"),
                rule("/media", "smb://host/share")
        );
        String result = JellyfinApi.reverseSubstitution(
                "smb://host/share/Shows/ep.mkv", r);
        assertEquals("/media/Shows/ep.mkv", result);
    }

    @Test
    public void reverseSubstitution_multipleRules_matchesFirstRule() {
        String[][] r = rules(
                rule("/movies", "smb://host/movies"),
                rule("/media", "smb://host/share")
        );
        String result = JellyfinApi.reverseSubstitution(
                "smb://host/movies/film.mkv", r);
        assertEquals("/movies/film.mkv", result);
    }

    @Test
    public void reverseSubstitution_emptyRules_returnsNull() {
        String[][] r = new String[0][];
        assertNull(JellyfinApi.reverseSubstitution("smb://host/share/file.mkv", r));
    }

    // extractSearchName tests

    @Test
    public void extractSearchName_normalPath_returnsFilenameStem() {
        assertEquals("S01E01", JellyfinApi.extractSearchName("/media/Shows/Breaking Bad/S01E01.mkv"));
    }

    @Test
    public void extractSearchName_noExtension_returnsFilename() {
        assertEquals("S01E01", JellyfinApi.extractSearchName("/media/Shows/S01E01"));
    }

    @Test
    public void extractSearchName_nullInput_returnsNull() {
        assertNull(JellyfinApi.extractSearchName(null));
    }

    @Test
    public void extractSearchName_emptyInput_returnsNull() {
        assertNull(JellyfinApi.extractSearchName(""));
    }

    @Test
    public void extractSearchName_filenameWithSpaces_returnsCorrectStem() {
        assertEquals("My Episode", JellyfinApi.extractSearchName("/media/Shows/My Episode.mkv"));
    }

    // Zidoo mount-path normalization tests

    @Test
    public void reverseSubstitution_mountedForm_maps() {
        String[][] r = rules(rule("/media", "smb://192.168.0.154/data/media"));
        String result = JellyfinApi.reverseSubstitution(
                "/data/system/smb/192.168.0.154#data/media/movies/Hercules (1997)/Hercules (1997) Bluray-1080p.mkv", r);
        assertEquals("/media/movies/Hercules (1997)/Hercules (1997) Bluray-1080p.mkv", result);
    }

    @Test
    public void reverseSubstitution_mountedFormWithUrlEncoding_decodesAndMaps() {
        String[][] r = rules(rule("/media", "smb://192.168.0.154/data/media"));
        String result = JellyfinApi.reverseSubstitution(
                "/data/system/smb/192.168.0.154#data/media/movies/My%20Movie.mkv", r);
        assertEquals("/media/movies/My Movie.mkv", result);
    }

    @Test
    public void normalizeZidooPath_launchUriAndMountedForm_areEqual() {
        String launchUri = "smb://user:pass@192.168.0.154/data/media/movies/Hercules (1997)/Hercules (1997) Bluray-1080p.mkv";
        String mountedForm = "/data/system/smb/192.168.0.154#data/media/movies/Hercules (1997)/Hercules (1997) Bluray-1080p.mkv";
        assertEquals(JellyfinApi.normalizeZidooPath(launchUri), JellyfinApi.normalizeZidooPath(mountedForm));
    }

    @Test
    public void isSameZidooFile_launchUriAndMountedForm_trueForSameFile() {
        String launchUri = "smb://user:pass@192.168.0.154/data/media/movies/Hercules (1997)/Hercules (1997) Bluray-1080p.mkv";
        String mountedForm = "/data/system/smb/192.168.0.154#data/media/movies/Hercules (1997)/Hercules (1997) Bluray-1080p.mkv";
        assertTrue(JellyfinApi.isSameZidooFile(launchUri, mountedForm));
    }

    @Test
    public void isSameZidooFile_differentFilesInMountedForm_returnsFalse() {
        String fileA = "/data/system/smb/192.168.0.154#data/media/movies/Hercules (1997)/Hercules (1997) Bluray-1080p.mkv";
        String fileB = "/data/system/smb/192.168.0.154#data/media/movies/Other Movie/Other Movie.mkv";
        assertFalse(JellyfinApi.isSameZidooFile(fileA, fileB));
    }

    @Test
    public void isSameZidooFile_eitherSideNull_returnsFalse() {
        String mountedForm = "/data/system/smb/192.168.0.154#data/media/movies/Hercules (1997)/Hercules (1997) Bluray-1080p.mkv";
        assertFalse(JellyfinApi.isSameZidooFile(null, mountedForm));
        assertFalse(JellyfinApi.isSameZidooFile(mountedForm, null));
        assertFalse(JellyfinApi.isSameZidooFile(null, null));
    }

    @Test
    public void isZidooFileChange_emptyToRealPath_returnsFalse() {
        String mountedForm = "/data/system/smb/192.168.0.154#data/media/movies/Hercules (1997)/Hercules (1997) Bluray-1080p.mkv";
        assertFalse(JellyfinApi.isZidooFileChange(mountedForm, ""));
    }

    @Test
    public void isZidooFileChange_realToEmptyPath_returnsFalse() {
        String mountedForm = "/data/system/smb/192.168.0.154#data/media/movies/Hercules (1997)/Hercules (1997) Bluray-1080p.mkv";
        assertFalse(JellyfinApi.isZidooFileChange("", mountedForm));
    }

    @Test
    public void isZidooFileChange_eitherSideNull_returnsFalse() {
        String mountedForm = "/data/system/smb/192.168.0.154#data/media/movies/Hercules (1997)/Hercules (1997) Bluray-1080p.mkv";
        assertFalse(JellyfinApi.isZidooFileChange(null, mountedForm));
        assertFalse(JellyfinApi.isZidooFileChange(mountedForm, null));
        assertFalse(JellyfinApi.isZidooFileChange(null, null));
    }

    @Test
    public void isZidooFileChange_launchUriAndMountedFormOfSameFile_returnsFalse() {
        String launchUri = "smb://192.168.0.154/data/media/movies/Hercules (1997)/Hercules (1997) Bluray-1080p.mkv";
        String mountedForm = "/data/system/smb/192.168.0.154#data/media/movies/Hercules (1997)/Hercules (1997) Bluray-1080p.mkv";
        assertFalse(JellyfinApi.isZidooFileChange(mountedForm, launchUri));
        assertFalse(JellyfinApi.isZidooFileChange(launchUri, mountedForm));
    }

    @Test
    public void isZidooFileChange_credentialsVersusNoneForSameFile_returnsFalse() {
        String withCredentials = "smb://user:pass@192.168.0.154/data/media/movies/Hercules (1997)/Hercules (1997) Bluray-1080p.mkv";
        String withoutCredentials = "smb://192.168.0.154/data/media/movies/Hercules (1997)/Hercules (1997) Bluray-1080p.mkv";
        assertFalse(JellyfinApi.isZidooFileChange(withoutCredentials, withCredentials));
    }

    @Test
    public void isZidooFileChange_differentFiles_returnsTrue() {
        String launchUri = "smb://user:pass@192.168.0.154/data/media/tv/Show/Season 01/Show S01E01.mkv";
        String nextFile = "/data/system/smb/192.168.0.154#data/media/tv/Show/Season 01/Show S01E02.mkv";
        assertTrue(JellyfinApi.isZidooFileChange(nextFile, launchUri));
    }

    @Test
    public void isZidooFileChange_localStoragePaths_trueOnlyWhenDifferent() {
        String fileA = "/storage/emulated/0/Movies/Film A.mkv";
        String fileB = "/storage/emulated/0/Movies/Film B.mkv";
        assertTrue(JellyfinApi.isZidooFileChange(fileB, fileA));
        assertFalse(JellyfinApi.isZidooFileChange(fileA, fileA));
    }

    @Test
    public void normalizeZidooPath_mountedFormWithNoHash_doesNotThrowAndReturnsSmbPrefixed() {
        String result = JellyfinApi.normalizeZidooPath("/data/system/smb/192.168.0.154");
        assertNotNull(result);
        assertTrue(result.startsWith("smb://"));
    }
}
