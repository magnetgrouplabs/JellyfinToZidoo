package com.jellyfintozidoo;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.UUID;

/**
 * Singleton wrapper around EncryptedSharedPreferences for secure credential storage.
 * Falls back to regular SharedPreferences if encryption is unavailable.
 */
public class SecureStorage {

    private static final String TAG = "SecureStorage";
    private static final String PREFS_FILE = "jellyfin_secure_prefs";
    private static final String FALLBACK_PREFS_FILE = "jellyfin_secure_prefs_fallback";

    private static volatile SharedPreferences instance;

    private SecureStorage() {
        // Prevent instantiation
    }

    /**
     * Returns a singleton SharedPreferences instance backed by EncryptedSharedPreferences.
     * If encryption fails, falls back to regular SharedPreferences.
     *
     * @param context Application or activity context
     * @return SharedPreferences instance for storing credentials
     */
    public static SharedPreferences getInstance(Context context) {
        if (instance == null) {
            synchronized (SecureStorage.class) {
                if (instance == null) {
                    instance = createPreferences(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    /**
     * Returns a stable per device identifier, generating and storing one on first use.
     * Kept in secure storage so it survives across sessions but never lands in the
     * exportable default preferences.
     *
     * @param context Application or activity context
     * @return A stable device id string
     */
    public static String getDeviceId(Context context) {
        SharedPreferences prefs = getInstance(context);
        String deviceId = prefs.getString("device_id", "");
        if (deviceId == null || deviceId.isEmpty()) {
            deviceId = UUID.randomUUID().toString().replace("-", "");
            prefs.edit().putString("device_id", deviceId).apply();
        }
        return deviceId;
    }

    /**
     * Removes any plaintext Jellyfin password left in the default SharedPreferences by
     * older builds. The real value lives in secure storage; this only cleans up the
     * plaintext copy and is a no op once it has run.
     *
     * @param context Application or activity context
     */
    public static void removeLegacyPlaintextPassword(Context context) {
        try {
            SharedPreferences defaultPrefs =
                    PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
            if (defaultPrefs.contains("jellyfin_password")) {
                defaultPrefs.edit().remove("jellyfin_password").apply();
                Log.i(TAG, "Removed a leftover plaintext password from default preferences");
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not clean up the legacy password entry: " + e.getMessage());
        }
    }

    private static SharedPreferences createPreferences(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            return EncryptedSharedPreferences.create(
                    context,
                    PREFS_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Failed to create encrypted preferences, using fallback", e);
            return context.getSharedPreferences(FALLBACK_PREFS_FILE, Context.MODE_PRIVATE);
        }
    }
}
