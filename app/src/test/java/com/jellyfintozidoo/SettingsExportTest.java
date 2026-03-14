package com.jellyfintozidoo;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Unit tests for settings export token exclusion.
 * Verifies that buildExportJson excludes jellyfin_access_token and jellyfin_user_id.
 */
public class SettingsExportTest {

    @Test
    public void buildExportJson_excludesAccessToken() {
        Map<String, Object> prefs = new LinkedHashMap<>();
        prefs.put("jellyfin_server_url", "http://server:8096");
        prefs.put("jellyfin_access_token", "secret-token-value");

        String json = SettingsActivity.buildExportJson(prefs);
        assertFalse("JSON should not contain jellyfin_access_token key",
                json.contains("jellyfin_access_token"));
        assertTrue("JSON should contain jellyfin_server_url",
                json.contains("jellyfin_server_url"));
    }

    @Test
    public void buildExportJson_excludesUserId() {
        Map<String, Object> prefs = new LinkedHashMap<>();
        prefs.put("jellyfin_server_url", "http://server:8096");
        prefs.put("jellyfin_user_id", "user-id-12345");

        String json = SettingsActivity.buildExportJson(prefs);
        assertFalse("JSON should not contain jellyfin_user_id key",
                json.contains("jellyfin_user_id"));
        assertTrue("JSON should contain jellyfin_server_url",
                json.contains("jellyfin_server_url"));
    }

    @Test
    public void buildExportJson_preservesNonTokenKeys() {
        Map<String, Object> prefs = new LinkedHashMap<>();
        prefs.put("jellyfin_server_url", "http://server:8096");
        prefs.put("skip_intros", true);
        prefs.put("skip_credits", true);

        String json = SettingsActivity.buildExportJson(prefs);
        assertTrue(json.contains("jellyfin_server_url"));
        assertTrue(json.contains("skip_intros"));
        assertTrue(json.contains("skip_credits"));
    }

    @Test
    public void buildExportJson_mixedKeys_tokensExcludedOthersPreserved() {
        Map<String, Object> prefs = new LinkedHashMap<>();
        prefs.put("jellyfin_server_url", "http://server:8096");
        prefs.put("jellyfin_username", "testuser");
        prefs.put("jellyfin_access_token", "secret-token");
        prefs.put("jellyfin_user_id", "user-123");
        prefs.put("skip_intros", true);
        prefs.put("useZidooPlayer", true);

        String json = SettingsActivity.buildExportJson(prefs);
        assertFalse(json.contains("jellyfin_access_token"));
        assertFalse(json.contains("jellyfin_user_id"));
        assertFalse(json.contains("secret-token"));
        assertFalse(json.contains("user-123"));
        assertTrue(json.contains("jellyfin_server_url"));
        assertTrue(json.contains("jellyfin_username"));
        assertTrue(json.contains("skip_intros"));
        assertTrue(json.contains("useZidooPlayer"));
    }
}
