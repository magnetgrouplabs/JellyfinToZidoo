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
}
