package com.jellyfintozidoo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreference;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class SettingsActivity extends AppCompatActivity
{
    private final String backupFile = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/JellyfinToZidooSettings.txt";
    // Instance fields, so the fragment and the activity are released when the screen closes
    private SettingsFragment settingsFragment;
    private String settingsRootKey;
    private String scrollToPreference;
    private static final int PERMISSION_REQUEST_IMPORT = 1;
    private static final int PERMISSION_REQUEST_EXPORT = 2;
    private final String[] permissions = {Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        // Drop any plaintext password an older build left in the default preferences
        SecureStorage.removeLegacyPlaintextPassword(getApplicationContext());

        // Stable per device id for the Jellyfin client identity
        String deviceId = SecureStorage.getDeviceId(getApplicationContext());
        JellyfinApi.setClientIdentity(Build.MODEL, deviceId, BuildConfig.VERSION_NAME);

        setContentView(R.layout.settings_activity);
        if (savedInstanceState == null)
        {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat
    {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
        {
            SettingsActivity activity = (SettingsActivity) requireActivity();
            activity.settingsFragment = this;
            activity.settingsRootKey = rootKey;

            activity.showRootSettings(null);
        }

        @SuppressLint("RestrictedApi")
        @Override
        protected void onBindPreferences()
        {
            super.onBindPreferences();

            SettingsActivity activity = (SettingsActivity) getActivity();
            if(activity != null && activity.scrollToPreference != null)
            {
                scrollToPreference(activity.scrollToPreference);
                activity.scrollToPreference = null;
            }
        }
    }

    @Override
    protected void onStart()
    {
        super.onStart();
    }

    @Override
    public void onBackPressed()
    {
        if (settingsFragment == null)
        {
            super.onBackPressed();
            return;
        }

        Preference substitutionLink = settingsFragment.findPreference("substitution_link");
        if (substitutionLink != null)
        {
            super.onBackPressed();
        }
        else
        {
            showRootSettings("substitution_link");
        }
    }

    public void showRootSettings(@Nullable String scrollToPref)
    {
        if (settingsFragment == null)
        {
            return;
        }

        scrollToPreference = scrollToPref;
        settingsFragment.setPreferencesFromResource(R.xml.root_preferences, settingsRootKey);
        setSmbPasswordPreference();
        setJellyfinPasswordPreference();
        setJellyfinServerUrlPreference();
        setLoginPreference();

        Preference substitutionLink = settingsFragment.findPreference("substitution_link");
        if (substitutionLink != null)
        {
            substitutionLink.setOnPreferenceClickListener(preference ->
            {
                settingsFragment.setPreferencesFromResource(R.xml.substitution_preferences, settingsRootKey);
                setSmbPasswordPreference();
                return true;
            });
        }
    }

    private void setJellyfinPasswordPreference()
    {
        EditTextPreference passwordPref = settingsFragment.findPreference("jellyfin_password");
        if (passwordPref == null) return;

        Context context = settingsFragment.requireContext();
        SharedPreferences securePrefs = SecureStorage.getInstance(context);

        // The real password stays in secure storage and is never written into the
        // preference, which would put it in plaintext in the default preferences file.
        // Only the summary reflects whether one is set.
        String existing = securePrefs.getString("jellyfin_password", "");
        passwordPref.setSummary((existing != null && !existing.isEmpty()) ? "********" : "Not set");

        // Mask input in the edit dialog
        passwordPref.setOnBindEditTextListener(editText ->
                editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD));

        // Write to secure storage instead of default SharedPreferences
        passwordPref.setOnPreferenceChangeListener((preference, newValue) ->
        {
            String value = (newValue != null) ? newValue.toString().trim() : "";
            securePrefs.edit().putString("jellyfin_password", value).apply();
            passwordPref.setSummary(value.isEmpty() ? "Not set" : "********");
            return false;
        });

        // Show login status
        Preference loginPref = settingsFragment.findPreference("jellyfin_login");
        if (loginPref != null)
        {
            String token = securePrefs.getString("jellyfin_access_token", "");
            if (token != null && !token.isEmpty())
            {
                loginPref.setSummary("Logged in");
            }
        }
    }

    private void setJellyfinServerUrlPreference()
    {
        EditTextPreference serverUrlPref = settingsFragment.findPreference("jellyfin_server_url");
        if (serverUrlPref == null) return;

        // Strip trailing slash on save
        serverUrlPref.setOnPreferenceChangeListener((preference, newValue) ->
        {
            String value = (newValue != null) ? newValue.toString().trim() : "";
            // Strip trailing slash(es)
            while (value.endsWith("/"))
            {
                value = value.substring(0, value.length() - 1);
            }
            // Set the cleaned value directly
            serverUrlPref.setText(value);
            serverUrlPref.setSummary(value);
            // Return false because we set it manually above
            return false;
        });
    }

    private void setLoginPreference()
    {
        Preference loginPref = settingsFragment.findPreference("jellyfin_login");
        if (loginPref == null) return;

        loginPref.setOnPreferenceClickListener(preference ->
        {
            Context context = settingsFragment.requireContext();
            SharedPreferences defaultPrefs = PreferenceManager.getDefaultSharedPreferences(context);
            SharedPreferences securePrefs = SecureStorage.getInstance(context);
            String serverUrl = defaultPrefs.getString("jellyfin_server_url", "");
            String username = defaultPrefs.getString("jellyfin_username", "");
            String password = securePrefs.getString("jellyfin_password", "");

            if (serverUrl == null || serverUrl.isEmpty() || username == null || username.isEmpty()
                    || password == null || password.isEmpty())
            {
                Toast.makeText(context, "Please configure Server URL, Username and Password first", Toast.LENGTH_LONG).show();
                return true;
            }

            loginPref.setSummary("Logging in...");
            JellyfinApi.authenticate(serverUrl, username, password, new JellyfinApi.AuthCallback()
            {
                @Override
                public void onSuccess(String accessToken, String userId, String serverName)
                {
                    securePrefs.edit()
                            .putString("jellyfin_access_token", accessToken)
                            .putString("jellyfin_user_id", userId)
                            .apply();
                    Toast.makeText(context, "Login successful", Toast.LENGTH_LONG).show();
                    loginPref.setSummary("Logged in");
                }

                @Override
                public void onError(String error)
                {
                    Toast.makeText(context, "Login failed: " + error, Toast.LENGTH_LONG).show();
                    loginPref.setSummary("Authenticate with Jellyfin server");
                }
            });

            return true;
        });
    }

    public void setSmbPasswordPreference()
    {
        String[] pref_index = {"", "_02", "_03", "_04", "_05", "_06", "_07", "_08", "_09", "_10"};
        for (String s: pref_index)
        {
            EditTextPreference smbPasswordPreference = settingsFragment.findPreference("smbPassword" + s);
            if (smbPasswordPreference != null)
            {
                smbPasswordPreference.setOnBindEditTextListener(editText -> editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD));
            }
        }
    }

    public void onSubstitutionBackClickMethod(View view)
    {
        showRootSettings("substitution_link");
    }

    public void onImportClickMethod(View view)
    {
        // If we had to request permissions, if we did then we'll do the import when we get notified that the access was granted
        if (checkAndRequestPermissions(PERMISSION_REQUEST_IMPORT))
        {
            return;
        }

        importSettings();
    }

    public void importSettings()
    {
        FileInputStream input = null;
        BufferedReader reader = null;
        try
        {
            input = new FileInputStream(backupFile);
            reader = new BufferedReader(new InputStreamReader(input));

            Gson gson = new Gson();
            Type mapType = new TypeToken<Map<String, ?>>(){}.getType();
            Map<String, ?> map = gson.fromJson(reader, mapType);

            for (Map.Entry<String, ?> entry : map.entrySet())
            {
                try
                {
                    setPreferenceOnImport(entry);
                }
                catch (Exception e)
                {
                    // Ignore bad data
                }
            }

            // Need to set the preferences on the substitution page
            settingsFragment.setPreferencesFromResource(R.xml.substitution_preferences, settingsRootKey);
            for (Map.Entry<String, ?> entry : map.entrySet())
            {
                try
                {
                    setPreferenceOnImport(entry);
                }
                catch (Exception e)
                {
                    // Ignore bad data
                }
            }

            showRootSettings("import_export");

            // Auto-authenticate with restored credentials to get a fresh token
            SharedPreferences defaultPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            String serverUrl = defaultPrefs.getString("jellyfin_server_url", "");
            String username = defaultPrefs.getString("jellyfin_username", "");
            String password = SecureStorage.getInstance(getApplicationContext()).getString("jellyfin_password", "");

            if (serverUrl != null && !serverUrl.isEmpty()
                    && username != null && !username.isEmpty()
                    && password != null && !password.isEmpty())
            {
                JellyfinApi.authenticate(serverUrl, username, password, new JellyfinApi.AuthCallback()
                {
                    @Override
                    public void onSuccess(String accessToken, String userId, String serverName)
                    {
                        SecureStorage.getInstance(getApplicationContext()).edit()
                                .putString("jellyfin_access_token", accessToken)
                                .putString("jellyfin_user_id", userId)
                                .apply();
                        runOnUiThread(() ->
                                Toast.makeText(getApplicationContext(),
                                        "Settings imported and logged in successfully",
                                        Toast.LENGTH_LONG).show());
                    }

                    @Override
                    public void onError(String error)
                    {
                        runOnUiThread(() ->
                                Toast.makeText(getApplicationContext(),
                                        "Settings imported. Login failed: " + error + ", please log in manually",
                                        Toast.LENGTH_LONG).show());
                    }
                });
            }
            else
            {
                Toast.makeText(getApplicationContext(), "Settings imported successfully from " + backupFile, Toast.LENGTH_LONG).show();
            }
        }
        catch (Exception e)
        {
            Toast.makeText(getApplicationContext(), "Failed to import settings from " + backupFile, Toast.LENGTH_LONG).show();
        }
        finally
        {
            try
            {
                if (reader != null)
                {
                    reader.close();
                }
                if (input != null)
                {
                    input.close();
                }
            }
            catch (IOException ex)
            {
                Toast.makeText(getApplicationContext(), "Failed to close import file", Toast.LENGTH_LONG).show();
            }
        }
    }

    public void setPreferenceOnImport(Map.Entry<String, ?> entry)
    {
        // Handle password import — write directly to SecureStorage
        if(entry.getKey().equals("jellyfin_password"))
        {
            String value = (entry.getValue() != null) ? entry.getValue().toString().trim() : "";
            SecureStorage.getInstance(getApplicationContext()).edit().putString("jellyfin_password", value).apply();
            EditTextPreference passwordPref = settingsFragment.findPreference("jellyfin_password");
            if(passwordPref != null)
            {
                // Summary only, the value itself stays in secure storage
                passwordPref.setSummary(value.isEmpty() ? "Not set" : "********");
            }
            return;
        }

        Preference pref = settingsFragment.findPreference(entry.getKey());
        if(pref instanceof EditTextPreference)
        {
            if(entry.getValue().toString().isEmpty())
            {
                ((EditTextPreference)pref).setText(null);
            }
            else
            {
                ((EditTextPreference)pref).setText(entry.getValue().toString());
            }
        }
        else if(pref instanceof SwitchPreference)
        {
            if(entry.getValue().toString().isEmpty())
            {
                ((SwitchPreference)pref).setChecked(false);
            }
            else
            {
                ((SwitchPreference)pref).setChecked(Boolean.parseBoolean(entry.getValue().toString()));
            }
        }
    }

    public void onExportClickMethod(View view)
    {
        // If we had to request permissions, if we did then we'll do the export when we get notified that the access was granted
        if (checkAndRequestPermissions(PERMISSION_REQUEST_EXPORT))
        {
            return;
        }

        exportSettings();
    }

    /**
     * True for any preference key that holds a password: the Jellyfin password and every
     * SMB password slot. Package-private for testability.
     *
     * @param key Preference key
     * @return true when the key holds a password
     */
    static boolean isPasswordKey(String key)
    {
        return key != null && ("jellyfin_password".equals(key) || key.startsWith("smbPassword"));
    }

    /**
     * Builds the export JSON string from a preferences map.
     * Always excludes jellyfin_access_token and jellyfin_user_id. Passwords are excluded
     * unless includePasswords is true, because the export file is plain text in a folder
     * any app with storage access can read.
     * Package-private for testability.
     *
     * @param prefsMap         Map of preference key-value pairs
     * @param includePasswords Whether the Jellyfin and SMB passwords go into the file
     * @return Pretty-printed JSON string
     */
    static String buildExportJson(Map<String, ?> prefsMap, boolean includePasswords)
    {
        LinkedHashMap<String, Object> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : prefsMap.entrySet())
        {
            String key = entry.getKey();
            if ("jellyfin_access_token".equals(key) || "jellyfin_user_id".equals(key))
            {
                continue;
            }
            if (!includePasswords && isPasswordKey(key))
            {
                continue;
            }
            filtered.put(key, entry.getValue());
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(filtered);
    }

    public void exportSettings()
    {
        FileOutputStream output = null;
        try
        {
            output = new FileOutputStream(backupFile);

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            Map<String,Object> prefsMap = prefs.getAll().entrySet()
                                                   .stream()
                                                   .sorted(Map.Entry.comparingByKey())
                                                   .collect(Collectors.toMap(Map.Entry::getKey,
                                                                             e -> (Object) e.getValue(),
                                                                             (oldValue, newValue) -> oldValue, LinkedHashMap::new));

            // Audit: access_token and user_id live in SecureStorage, NOT in default
            // SharedPreferences, so prefs.getAll() should never contain them.
            // buildExportJson() defensively excludes them regardless.

            // Passwords are left out unless the user has turned the option on
            boolean includePasswords = prefs.getBoolean("export_include_passwords", false);

            if(includePasswords)
            {
                // The Jellyfin password lives in SecureStorage, not in the default preferences
                String jellyfinPassword = SecureStorage.getInstance(getApplicationContext()).getString("jellyfin_password", "");
                if(jellyfinPassword != null && !jellyfinPassword.isEmpty())
                {
                    prefsMap.put("jellyfin_password", jellyfinPassword);
                }
            }

            String json = buildExportJson(prefsMap, includePasswords);
            output.write(json.getBytes(StandardCharsets.UTF_8));

            String exportedMessage = includePasswords
                    ? "Settings and passwords exported to " + backupFile
                    : "Settings exported to " + backupFile + " (passwords excluded)";
            Toast.makeText(getApplicationContext(), exportedMessage, Toast.LENGTH_LONG).show();
        }
        catch (Exception e)
        {
            Toast.makeText(getApplicationContext(), e.toString()/*"Failed to export settings to " + backupFile*/, Toast.LENGTH_LONG).show();
        }
        finally
        {
            try
            {
                if (output != null)
                {
                    output.flush();
                    output.close();
                }
            }
            catch (IOException ex)
            {
                Toast.makeText(getApplicationContext(), "Failed to close export file", Toast.LENGTH_LONG).show();
            }
        }
    }

    // Return true if we have to request the permissions, that way the caller can wait until we were granted the permissions before continuing
    public Boolean checkAndRequestPermissions(int requestCode)
    {
        if(checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
        {
            requestPermissions(permissions, requestCode);
            return true;
        }

        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if(requestCode == PERMISSION_REQUEST_IMPORT || requestCode == PERMISSION_REQUEST_EXPORT)
        {
            if(grantResults.length >= 2
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED
                    && grantResults[1] == PackageManager.PERMISSION_GRANTED)
            {
                if(requestCode == PERMISSION_REQUEST_IMPORT)
                {
                    importSettings();
                }
                else //if(requestCode == PERMISSION_REQUEST_EXPORT)
                {
                    exportSettings();
                }
            }
            else
            {
                Toast.makeText(getApplicationContext(), "Please give storage access in order to import/export settings", Toast.LENGTH_LONG).show();
            }
        }
    }
}