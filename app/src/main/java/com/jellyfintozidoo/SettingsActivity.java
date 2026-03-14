package com.jellyfintozidoo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
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
    private static SettingsFragment settingsFragment;
    private static String settingsRootKey;
    private static String scrollToPreference;
    private static final int PERMISSION_REQUEST_IMPORT = 1;
    private static final int PERMISSION_REQUEST_EXPORT = 2;
    private final String[] permissions = {Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
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
            settingsFragment = this;
            settingsRootKey = rootKey;

            showRootSettings(null);
        }

        @SuppressLint("RestrictedApi")
        @Override
        protected void onBindPreferences()
        {
            super.onBindPreferences();

            if(scrollToPreference != null)
            {
                scrollToPreference(scrollToPreference);
                scrollToPreference = null;
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

    public static void showRootSettings(@Nullable String scrollToPref)
    {
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

    private static void setJellyfinPasswordPreference()
    {
        EditTextPreference passwordPref = settingsFragment.findPreference("jellyfin_password");
        if (passwordPref == null) return;

        Context context = settingsFragment.requireContext();
        SharedPreferences securePrefs = SecureStorage.getInstance(context);

        // Load existing value from secure storage
        String existing = securePrefs.getString("jellyfin_password", "");
        passwordPref.setText(existing);
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

    private static void setJellyfinServerUrlPreference()
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

    private static void setLoginPreference()
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

    public static void setSmbPasswordPreference()
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

            Toast.makeText(getApplicationContext(), "Settings imported successfully from " + backupFile, Toast.LENGTH_LONG).show();
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
                passwordPref.setText(value);
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
     * Builds the export JSON string from a preferences map.
     * Excludes sensitive keys: jellyfin_access_token, jellyfin_user_id.
     * Package-private for testability.
     *
     * @param prefsMap Map of preference key-value pairs
     * @return Pretty-printed JSON string
     */
    static String buildExportJson(Map<String, ?> prefsMap)
    {
        LinkedHashMap<String, Object> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : prefsMap.entrySet())
        {
            String key = entry.getKey();
            if ("jellyfin_access_token".equals(key) || "jellyfin_user_id".equals(key))
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

            // Include password from SecureStorage (not in default SharedPreferences)
            String jellyfinPassword = SecureStorage.getInstance(getApplicationContext()).getString("jellyfin_password", "");
            if(jellyfinPassword != null && !jellyfinPassword.isEmpty())
            {
                prefsMap.put("jellyfin_password", jellyfinPassword);
            }

            String json = buildExportJson(prefsMap);
            output.write(json.getBytes(StandardCharsets.UTF_8));

            Toast.makeText(getApplicationContext(), "Settings successfully exported to " + backupFile, Toast.LENGTH_LONG).show();
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
        if(requestCode == PERMISSION_REQUEST_IMPORT || requestCode == PERMISSION_REQUEST_EXPORT)
        {
            if(grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED)
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