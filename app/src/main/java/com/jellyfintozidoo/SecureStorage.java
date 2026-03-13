package com.jellyfintozidoo;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

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
