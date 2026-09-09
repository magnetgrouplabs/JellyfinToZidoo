package com.jellyfintozidoo;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Unit tests for settings export filtering.
 * Verifies that buildExportJson always excludes jellyfin_access_token and jellyfin_user_id,
 * and excludes the Jellyfin and SMB passwords unless the caller opts in.
 */
public class SettingsExportTest {

    @Test
    public void buildExportJson_excludesAccessToken() {
        Map<String, Object> prefs = new LinkedHashMap<>();
        prefs.put("jellyfin_server_url", "http://server:8096");
        prefs.put("jellyfin_access_token", "secret-token-value");

        String json = SettingsActivity.buildExportJson(prefs, false);
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

        String json = SettingsActivity.buildExportJson(prefs, false);
        assertFalse("JSON should not contain jellyfin_user_id key",
                json.contains("jellyfin_user_id"));
        assertTrue("JSON should contain jellyfin_server_url",
                json.contains("jellyfin_server_url"));
    }

    @Test
    public void buildExportJson_excludesAccessTokenEvenWhenPasswordsIncluded() {
        Map<String, Object> prefs = new LinkedHashMap<>();
        prefs.put("jellyfin_server_url", "http://server:8096");
        prefs.put("jellyfin_access_token", "secret-token-value");
        prefs.put("jellyfin_user_id", "user-id-12345");

        String json = SettingsActivity.buildExportJson(prefs, true);
        assertFalse(json.contains("jellyfin_access_token"));
        assertFalse(json.contains("jellyfin_user_id"));
        assertFalse(json.contains("secret-token-value"));
    }

    @Test
    public void buildExportJson_preservesNonTokenKeys() {
        Map<String, Object> prefs = new LinkedHashMap<>();
        prefs.put("jellyfin_server_url", "http://server:8096");
        prefs.put("skip_intros", true);
        prefs.put("skip_credits", true);

        String json = SettingsActivity.buildExportJson(prefs, false);
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

        String json = SettingsActivity.buildExportJson(prefs, false);
        assertFalse(json.contains("jellyfin_access_token"));
        assertFalse(json.contains("jellyfin_user_id"));
        assertFalse(json.contains("secret-token"));
        assertFalse(json.contains("user-123"));
        assertTrue(json.contains("jellyfin_server_url"));
        assertTrue(json.contains("jellyfin_username"));
        assertTrue(json.contains("skip_intros"));
        assertTrue(json.contains("useZidooPlayer"));
    }

    @Test
    public void buildExportJson_excludesPasswordsByDefault() {
        Map<String, Object> prefs = new LinkedHashMap<>();
        prefs.put("jellyfin_server_url", "http://server:8096");
        prefs.put("jellyfin_password", "jellyfin-secret");
        prefs.put("smbPassword", "smb-secret-01");
        prefs.put("smbPassword_02", "smb-secret-02");
        prefs.put("smbPassword_10", "smb-secret-10");
        prefs.put("smbUsername", "smbuser");

        String json = SettingsActivity.buildExportJson(prefs, false);
        assertFalse("Jellyfin password key must be excluded", json.contains("jellyfin_password"));
        assertFalse(json.contains("jellyfin-secret"));
        assertFalse("SMB password keys must be excluded", json.contains("smbPassword"));
        assertFalse(json.contains("smb-secret-01"));
        assertFalse(json.contains("smb-secret-02"));
        assertFalse(json.contains("smb-secret-10"));
        assertTrue("Non password keys stay in the export", json.contains("smbUsername"));
        assertTrue(json.contains("jellyfin_server_url"));
    }

    @Test
    public void buildExportJson_includesPasswordsWhenRequested() {
        Map<String, Object> prefs = new LinkedHashMap<>();
        prefs.put("jellyfin_server_url", "http://server:8096");
        prefs.put("jellyfin_password", "jellyfin-secret");
        prefs.put("smbPassword_03", "smb-secret-03");

        String json = SettingsActivity.buildExportJson(prefs, true);
        assertTrue(json.contains("jellyfin_password"));
        assertTrue(json.contains("jellyfin-secret"));
        assertTrue(json.contains("smbPassword_03"));
        assertTrue(json.contains("smb-secret-03"));
    }

    @Test
    public void isPasswordKey_identifiesPasswordSlots() {
        assertTrue(SettingsActivity.isPasswordKey("jellyfin_password"));
        assertTrue(SettingsActivity.isPasswordKey("smbPassword"));
        assertTrue(SettingsActivity.isPasswordKey("smbPassword_02"));
        assertTrue(SettingsActivity.isPasswordKey("smbPassword_10"));
        assertFalse(SettingsActivity.isPasswordKey("smbUsername"));
        assertFalse(SettingsActivity.isPasswordKey("jellyfin_username"));
        assertFalse(SettingsActivity.isPasswordKey(null));
    }
}
