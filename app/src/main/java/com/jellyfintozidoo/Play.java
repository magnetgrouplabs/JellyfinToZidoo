package com.jellyfintozidoo;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class Play extends AppCompatActivity
{
    private static final int UP_NEXT_REQUEST_CODE = 99;

    private static volatile okhttp3.OkHttpClient localClient;
    private static okhttp3.OkHttpClient getLocalClient() {
        if (localClient == null) {
            synchronized (Play.class) {
                if (localClient == null) {
                    localClient = new okhttp3.OkHttpClient.Builder()
                            .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                            .readTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                            .build();
                }
            }
        }
        return localClient;
    }

    /**
     * Seeks Zidoo player to a position via REST API. Fire-and-forget.
     * Note: "positon" is a real typo in the Zidoo API.
     */
    private void seekZidoo(long positionMs) {
        new Thread(() -> {
            try {
                okhttp3.Request request = new okhttp3.Request.Builder()
                        .url("http://127.0.0.1:9529/ZidooVideoPlay/seekTo?positon=" + positionMs)
                        .build();
                getLocalClient().newCall(request).execute().close();
            } catch (Exception e) {
                Log.w("Play", "seekZidoo failed: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Sets audio track in Zidoo player via REST API. Fire-and-forget.
     */
    private void setZidooAudio(int index) {
        new Thread(() -> {
            try {
                okhttp3.Request request = new okhttp3.Request.Builder()
                        .url("http://127.0.0.1:9529/ZidooVideoPlay/setAudio?index=" + index)
                        .build();
                getLocalClient().newCall(request).execute().close();
            } catch (Exception e) {
                Log.w("Play", "setZidooAudio failed: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Sets subtitle track in Zidoo player via REST API. Fire-and-forget.
     * Index is 1-based; 0 = off.
     */
    private void setZidooSubtitle(int index) {
        new Thread(() -> {
            try {
                okhttp3.Request request = new okhttp3.Request.Builder()
                        .url("http://127.0.0.1:9529/ZidooVideoPlay/setSubtitle?index=" + index)
                        .build();
                getLocalClient().newCall(request).execute().close();
            } catch (Exception e) {
                Log.w("Play", "setZidooSubtitle failed: " + e.getMessage());
            }
        }).start();
    }

    private Intent originalIntent;
    private Intent newIntent;
    private int viewOffset = 0;
    private String directPath = "";
    private String videoTitle = "";
    private boolean audioSelected = false;
    private int selectedAudioIndex = -1;
    private boolean subtitleSelected = false;
    private int selectedSubtitleIndex = -1;
    private String password = "";
    private String message = "";
    private boolean foundSubstitution = false;
    private String videoPath = "";
    private volatile String jellyfinItemId = "";
    // The item the launching client handed us. The client attributes the position we return to
    // this item only, never to an episode we moved on to through Up Next or a Zidoo file advance,
    // so jellyfinItemId may move on while this one stays put.
    private String originalItemId = "";
    // The position handed back on the result intent. 0 wipes the launching client's own resume
    // point but never marks the item played, so it is the safe value whenever we do not want the
    // client to write a resume point at all.
    private volatile long resultPositionMs = 0;
    private String jellyfinApiPath = "";
    private boolean zdmc = false;
    private String callerPackage = "";
    private String serverUrl = "";
    private String accessToken = "";
    private String userId = "";
    private volatile String playSessionId = "";
    private volatile long durationTicks = 0;
    private volatile long lastKnownPositionMs = 0;
    private volatile long lastKnownDurationMs = 0;
    private volatile String currentPlayingPath = null;
    private String seriesId = "";
    private volatile boolean upNextTriggered = false;
    private volatile boolean waitingForUpNext = false;
    private volatile boolean handlingPlaybackResult = false;
    private volatile boolean awaitingPlaybackResult = false;
    private volatile java.util.concurrent.ScheduledExecutorService progressPoller = null;

    // Intro/credit skip state
    private volatile boolean introSkipArmed = true;
    private volatile boolean creditSkipArmed = true;
    private volatile long introStartMs = -1, introEndMs = -1;
    private volatile long creditStartMs = -1, creditEndMs = -1;
    private volatile long lastPollPositionMs = -1;
    private volatile boolean tracksSet = false;  // Set audio/subtitle only once per episode
    private volatile boolean introSegmentsFetched = false;

    // Audio/subtitle from intent URL
    private volatile int jellyfinAudioStreamIndex = -1;
    private int jellyfinSubtitleStreamIndex = -1;
    private volatile com.google.gson.JsonArray mediaStreams = null;  // Parsed from getItem response

    // Intro/credit skip state
    private boolean introSkipArmed = true;
    private boolean creditSkipArmed = true;
    private long introStartMs = -1, introEndMs = -1;
    private long creditStartMs = -1, creditEndMs = -1;
    private long lastPollPositionMs = -1;
    private boolean tracksSet = false;  // Set audio/subtitle only once per episode
    private boolean introSegmentsFetched = false;

    // Audio/subtitle from intent URL
    private int jellyfinAudioStreamIndex = -1;
    private int jellyfinSubtitleStreamIndex = -1;
    private com.google.gson.JsonArray mediaStreams = null;  // Parsed from getItem response

    private TextView textView1;
    private TextView textView2;
    private Button playButton;

    private boolean useNewZdiooPlayer = false;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        useNewZdiooPlayer = useNewZdiooPlayer();

        // Drop any plaintext password an older build left in the default preferences
        SecureStorage.removeLegacyPlaintextPassword(getApplicationContext());

        // Stable per device id for the Jellyfin client identity
        String deviceId = SecureStorage.getDeviceId(getApplicationContext());
        JellyfinApi.setClientIdentity(Build.MODEL, deviceId, BuildConfig.VERSION_NAME);

        setContentView(R.layout.activity_play);
    }

    private boolean useNewZdiooPlayer()
    {
        try
        {
            PackageInfo packageInfo;
            try
            {
                packageInfo = getPackageManager().getPackageInfo("com.zidoo.player", 0);
            }
            catch (Exception e)
            {
                packageInfo = null;
            }

            if (packageInfo != null)
            {
                return true;
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return false;
    }

    @Override
    protected void onRestart()
    {
        super.onRestart();
        // Don't finish if we're awaiting/handling playback results or Up Next flow.
        // onRestart fires BEFORE onActivityResult in the Android lifecycle, so
        // awaitingPlaybackResult prevents premature finish before result processing.
        if (awaitingPlaybackResult || upNextTriggered || waitingForUpNext || handlingPlaybackResult) {
            return;
        }
        this.finishWithResult();
    }

    /** Matches the credential part of an smb URI so it can be hidden from logs and the debug page. */
    private static final Pattern SMB_CREDENTIALS_PATTERN = Pattern.compile("smb://([^:/@]+):[^@/]*@");

    /** Matches an api_key or ApiKey query value so tokens stay off the debug page. */
    private static final Pattern API_KEY_PATTERN = Pattern.compile("(?i)([?&](?:api_key|ApiKey)=)[^&\\s]*");

    /**
     * Replaces the password in an smb URI with a fixed mask, so a substituted path can be
     * logged or shown without leaking the share credentials.
     */
    static String maskCredentials(String value)
    {
        if (value == null || value.isEmpty())
        {
            return value;
        }
        return SMB_CREDENTIALS_PATTERN.matcher(value).replaceAll("smb://$1:****@");
    }

    /**
     * Replaces api_key and ApiKey query values with a placeholder so the Jellyfin access
     * token never reaches the debug page.
     */
    static String maskTokens(String value)
    {
        if (value == null || value.isEmpty())
        {
            return value;
        }
        return API_KEY_PATTERN.matcher(value).replaceAll("$1<token>");
    }

    private void updateDebugPage()
    {
        String originalIntentToPrint = intentToString(originalIntent);
        String newIntentToPrint = intentToString(newIntent);
        String pathToPrint = maskCredentials(directPath);

        // If the path has a password in it then hide it from the debug output.
        // Pattern.quote keeps a password with regex characters from breaking the match.
        if(pathToPrint != null && !password.isEmpty())
        {
            pathToPrint = pathToPrint.replaceFirst(":" + Pattern.quote(password) + "@", ":********@");
        }

        if(!foundSubstitution && message.isEmpty())
        {
            message = "ERROR: No substitution found";
        }

        if(!message.isEmpty())
        {
            textView1.setVisibility(View.VISIBLE);
            textView1.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
            if(message.contains("WARNING"))
            {
                textView1.setBackgroundColor(0xFFFFDB58);
            }
            textView1.setText(String.format(Locale.ENGLISH, message + "\n"));
        }

        String debugText = String.format(Locale.ENGLISH,
            "Intent: %s\n\n" +
            "Jellyfin Item ID: %s\n\n" +
            "API Path: %s\n\n" +
            "Path Substitution: %s\n\n" +
            "Video Path: %s\n\n" +
            "View Offset: %d ms\n\n" +
            "Selected Audio Index: %d\n\n" +
            "Selected Subtitle Index: %d\n\n" +
            "New Zidoo Player: %b\n\n" +
            "New Intent: %s",
            originalIntentToPrint, jellyfinItemId, jellyfinApiPath,
            pathToPrint, videoPath, viewOffset,
            selectedAudioIndex, selectedSubtitleIndex,
            useNewZdiooPlayer, newIntentToPrint);
        textView2.setText(debugText);
    }

    private void showDebugPageOrSendIntent()
    {
        if(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("useZidooPlayer", true))
        {
            buildZidooIntent(directPath, viewOffset);
        }
        else
        {
            buildDefaultIntent(directPath);
        }

        // If the debug flag is on then update the text field
        if(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("debug", false))
        {
            updateDebugPage();

            playButton.setEnabled(true);
            playButton.setVisibility(View.VISIBLE);
        }
        // Else just play the movie
        else
        {
            playButton.callOnClick();
        }
    }

    private void doSubstitution(String path)
    {
        if (path == null)
        {
            return;
        }

        // Check if we can actually do the substitution, if not then pass along the original file and see if it plays
        String[] pref_index = {"", "_02", "_03", "_04", "_05", "_06", "_07", "_08", "_09", "_10"};
        for (String s: pref_index)
        {
            String[] path_to_replace_array = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString("path_to_replace" + s, "").split("\\s*,\\s*");
            String[] replaced_with_array = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString("replaced_with" + s, "").split("\\s*,\\s*");
            String smb_username = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString("smbUsername" + s, "");
            String smb_password = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString("smbPassword" + s, "");
            // A cleared preference can hold a null value, so normalise both before use
            if (smb_username == null) smb_username = "";
            if (smb_password == null) smb_password = "";

            if (path_to_replace_array.length > 0 && replaced_with_array.length > 0 && path_to_replace_array.length == replaced_with_array.length)
            {
                for (int i = 0; i < path_to_replace_array.length; i++)
                {
                    // Prefix match, so that forward and reverse substitution are inverses of each other
                    if (!path_to_replace_array[i].isEmpty() && path.startsWith(path_to_replace_array[i]))
                    {
                        // quoteReplacement so a replacement containing $ or \ (for example an
                        // administrative share name) is used literally instead of as a back reference
                        path = path.replaceFirst(Pattern.quote(path_to_replace_array[i]),
                                Matcher.quoteReplacement(replaced_with_array[i])).replace("\\", "/");

                        if (path.contains("nfs://") || directPath.contains("/mnt/nfs/"))
                        {
                            // 8k model suppors NFS mounting. For example:  "path_to_replace": "/Volumes/share1" ----  "replaced_with": "nfs://192.168.11.113/share1", The player will automatically mount NFS:/mnt/nfs/192.168.11.113#share1
                            if (!useNewZdiooPlayer)
                            {
                                path = path.replaceAll("nfs://(.*?)/", "/mnt/nfs/$1#");
                            }
                        }
                        else
                        {
                            path = Uri.encode(path, "/ :");
                        }

                        // If this is an SMB request add user name and password to the path.
                        // Both are URL encoded so that a credential containing @ : / # or %
                        // still produces a valid smb URI.
                        if (!smb_username.isEmpty())
                        {
                            // Keep the encoded form, it is what appears in the path we mask for debug output
                            password = Uri.encode(smb_password);
                            path = path.replace("smb://", "smb://" + Uri.encode(smb_username) + ":" + password + "@");
                        }

                        foundSubstitution = true;
                        directPath = path;

                        return;
                    }
                }
            }
        }
    }

    @Override
    protected void onStart()
    {
        super.onStart();

        // Don't re-initialize when returning from Zidoo player or UpNextActivity.
        // onStart fires BEFORE onActivityResult in the Android lifecycle, so
        // awaitingPlaybackResult prevents re-init before result processing.
        if (awaitingPlaybackResult || handlingPlaybackResult || waitingForUpNext || upNextTriggered) {
            Log.d("Play", "onStart: skipping re-init (awaitingPlaybackResult=" + awaitingPlaybackResult + " handlingPlaybackResult=" + handlingPlaybackResult + " waitingForUpNext=" + waitingForUpNext + " upNextTriggered=" + upNextTriggered + ")");
            return;
        }

        // Capture calling package for relaunch after playback
        String caller = getCallingPackage();
        if (caller == null) {
            android.net.Uri referrer = getReferrer();
            if (referrer != null && "android-app".equals(referrer.getScheme())) {
                caller = referrer.getHost();
            }
        }
        if (caller != null && !caller.isEmpty()) {
            callerPackage = caller;
        }

        originalIntent = getIntent();

        String inputString = originalIntent.getDataString();
        if (inputString == null)
        {
            // An explicit intent from another app can arrive without any data
            Log.i("Play", "Incoming intent has no data string");
            inputString = "";
        }
        directPath = inputString;
        textView1 = findViewById(R.id.textView1);
        textView2 = findViewById(R.id.textView2);
        playButton = findViewById(R.id.play_button);
        playButton.setOnClickListener(v ->
        {
            if(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("useZidooPlayer", true))
            {
                awaitingPlaybackResult = true;
                startActivityForResult(newIntent, 98);
                // Report playback start to Jellyfin
                if (!jellyfinItemId.isEmpty() && !serverUrl.isEmpty() && !accessToken.isEmpty()) {
                    JellyfinApi.reportPlaybackStart(serverUrl, accessToken, jellyfinItemId,
                        playSessionId, new JellyfinApi.SimpleCallback() {
                            @Override
                            public void onSuccess(String msg) {
                                Log.d("Play", "Playback start reported");
                                startProgressPoller();
                            }
                            @Override
                            public void onError(String error) {
                                Log.w("Play", "Failed to report playback start: " + error);
                                startProgressPoller(); // Start poller anyway
                            }
                        });
                }
            }
            else
            {
                startActivity(newIntent);
            }
        });

        try
        {
            // The PlexToZidoo- parameter prefix is an external contract with the ZDMC/Kodi integration, so it must keep this name.
            Pattern p = Pattern.compile("[?&]PlexToZidoo-([^=]+)=([^&]+)");
            Matcher m = p.matcher(inputString);
            boolean pathMapped = false;
            while(m.find())
            {
                zdmc = true;
                if(m.group(1).equals("ViewOffset"))
                {
                    viewOffset = Integer.parseInt(m.group(2));
                }
                else if(m.group(1).equals("AudioIndex"))
                {
                    audioSelected = true;
                    selectedAudioIndex = Integer.parseInt(m.group(2));
                }
                else if(m.group(1).equals("SubtitleIndex"))
                {
                    subtitleSelected = true;
                    selectedSubtitleIndex = Integer.parseInt(m.group(2));
                }
                else if(m.group(1).equals("Title"))
                {
                    videoTitle = URLDecoder.decode(m.group(2), StandardCharsets.UTF_8.toString());
                }
                else if(m.group(1).equals("Path"))
                {
                    videoPath = URLDecoder.decode(m.group(2), StandardCharsets.UTF_8.toString());
                }
                else if(m.group(1).equals("PathMapped"))
                {
                    pathMapped = true;
                }
            }

            if(zdmc)
            {
                directPath = m.replaceAll("");
                if(pathMapped)
                {
                    // Already did the substitution in kodi
                    if (directPath.contains("nfs://") || directPath.contains("/mnt/nfs/"))
                    {
                        directPath = Uri.decode(directPath);
                        directPath = directPath.replaceAll("nfs://(.*?)/", "/mnt/nfs/$1#");
                    }

                    foundSubstitution = true;
                    showDebugPageOrSendIntent();
                    return;
                }
                else if(!videoPath.isEmpty())
                {
                    doSubstitution(videoPath);
                    showDebugPageOrSendIntent();
                    return;
                }
            }
        }
        catch (Exception e)
        {
            message = "ERROR 1.0: " + e;
            showDebugPageOrSendIntent();
            return;
        }

        // Jellyfin intent handling (replaces Plex server communication)
        if(!zdmc)
        {
            String inputUrl = inputString;
            String extractedItemId = JellyfinApi.extractItemId(inputUrl);

            // Only treat an http(s) URL as a Jellyfin item when it comes from the configured
            // server. Anything else falls through to the generic path below.
            if(extractedItemId != null && !isConfiguredServerHost(inputUrl))
            {
                Log.i("Play", "Incoming URL is not from the configured Jellyfin server, using the generic path");
                extractedItemId = null;
            }

            final String itemId = extractedItemId;

            if(itemId != null)
            {
                // This is a Jellyfin streaming URL -- resolve via API
                jellyfinItemId = itemId;
                originalItemId = itemId;

                // Parse audio/subtitle stream indices from intent URL
                jellyfinAudioStreamIndex = JellyfinApi.parseUrlParam(inputUrl, "AudioStreamIndex");
                jellyfinSubtitleStreamIndex = JellyfinApi.parseUrlParam(inputUrl, "SubtitleStreamIndex");

                // Check for position from intent extras (Jellyfin client sends ms). The client
                // always sets this extra and sets it to 0 for "Play from beginning", so we have
                // to know whether it was present, not just whether it was non zero.
                boolean hasPosition = false;
                int intentPosition = 0;
                try
                {
                    hasPosition = originalIntent.hasExtra("position");
                    if(hasPosition)
                    {
                        intentPosition = originalIntent.getIntExtra("position", 0);
                    }
                }
                catch(Exception e)
                {
                    // Ignore -- some intents may not have this extra
                    hasPosition = false;
                    intentPosition = 0;
                }
                // Earliest seed for the result position: if the item lookup itself fails, the
                // client at least gets back the position it sent us.
                resultPositionMs = intentPosition;
                final boolean hasIntentPos = hasPosition;
                final int intentPos = intentPosition;

                // Read server config and store in instance fields for playback reporting
                serverUrl = PreferenceManager.getDefaultSharedPreferences(
                    getApplicationContext()).getString("jellyfin_server_url", "");
                accessToken = SecureStorage.getInstance(getApplicationContext())
                    .getString("jellyfin_access_token", "");

                if(serverUrl.isEmpty() || accessToken.isEmpty())
                {
                    message = "ERROR: Jellyfin server not configured. Go to Settings and Login.";
                    showDebugPageOrSendIntent();
                    return;
                }

                // Store userId and generate playSessionId for playback reporting
                userId = SecureStorage.getInstance(getApplicationContext())
                    .getString("jellyfin_user_id", "");
                playSessionId = java.util.UUID.randomUUID().toString().replace("-", "");

                // Call Jellyfin API asynchronously (detailed variant for MediaStreams extraction)
                JellyfinApi.getItemDetailed(serverUrl, accessToken, itemId, new JellyfinApi.DetailedCallback()
                {
                    @Override
                    public void onSuccess(String serverPath, long positionTicks, String title, long durationTicks, String itemSeriesId, String rawBody)
                    {
                        jellyfinApiPath = serverPath;
                        videoPath = serverPath;
                        videoTitle = title;
                        Play.this.durationTicks = durationTicks;
                        if (itemSeriesId != null) Play.this.seriesId = itemSeriesId;

                        // Extract MediaStreams from raw JSON for audio/subtitle track mapping
                        try {
                            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(rawBody).getAsJsonObject();
                            if (root.has("MediaSources")) {
                                com.google.gson.JsonArray sources = root.getAsJsonArray("MediaSources");
                                if (sources.size() > 0) {
                                    mediaStreams = sources.get(0).getAsJsonObject().getAsJsonArray("MediaStreams");
                                }
                            }
                        } catch (Exception e) {
                            Log.w("Play", "Failed to extract MediaStreams: " + e.getMessage());
                        }

                        // Apply default stream fallbacks if intent URL didn't specify indices
                        if (mediaStreams != null) {
                            if (jellyfinAudioStreamIndex == -1) {
                                jellyfinAudioStreamIndex = JellyfinApi.findDefaultStreamIndex(mediaStreams, "Audio");
                            }
                            if (jellyfinSubtitleStreamIndex == -1) {
                                jellyfinSubtitleStreamIndex = JellyfinApi.findDefaultStreamIndex(mediaStreams, "Subtitle");
                            }
                        }

                        // Fetch IntroSkipper segments for intro/credit skip
                        if (!introSegmentsFetched) {
                            introSegmentsFetched = true;
                            JellyfinApi.getIntroSkipperSegments(serverUrl, accessToken, jellyfinItemId, new JellyfinApi.SimpleCallback() {
                                @Override
                                public void onSuccess(String message) {
                                    // A proxy or an error page can return a body this parser cannot read,
                                    // and this runs on the main thread, so never let it escape
                                    try {
                                        JellyfinApi.IntroSkipperResult result = JellyfinApi.parseIntroSkipperResponse(message);
                                        introStartMs = result.introStartMs();
                                        introEndMs = result.introEndMs();
                                        creditStartMs = result.creditStartMs();
                                        creditEndMs = result.creditEndMs();
                                    } catch (Exception e) {
                                        Log.w("Play", "Could not read intro skipper segments: " + e.getMessage());
                                    }
                                }
                                @Override
                                public void onError(String error) { /* silent no-op */ }
                            });
                        }

                        // The client's position wins whenever it sent one, an explicit 0 from
                        // "Play from beginning" included; the server's saved position is used
                        // only when the extra is absent.
                        viewOffset = resolveStartPositionMs(hasIntentPos, intentPos, positionTicks);
                        // Seed the result position here too, so a substitution failure that never
                        // reaches buildZidooIntent still hands the client its own resume point back.
                        resultPositionMs = viewOffset;

                        doSubstitution(serverPath);
                        showDebugPageOrSendIntent();
                    }

                    @Override
                    public void onError(String error)
                    {
                        message = "ERROR: " + error;
                        showDebugPageOrSendIntent();
                    }
                });
            }
            else
            {
                // Not a Jellyfin URL -- try direct substitution (existing fallback behavior)
                doSubstitution(directPath);
                showDebugPageOrSendIntent();
            }
        }
    }

    /**
     * True when the incoming URL may be treated as a Jellyfin item URL. Only http and https
     * URLs are checked, and only when a server URL is configured with a host we can compare
     * against, so nothing that used to work is blocked.
     */
    private boolean isConfiguredServerHost(String url)
    {
        try
        {
            Uri incoming = Uri.parse(url);
            String scheme = incoming.getScheme();
            if (scheme == null)
            {
                return true;
            }
            scheme = scheme.toLowerCase(Locale.ENGLISH);
            if (!scheme.equals("http") && !scheme.equals("https"))
            {
                return true;
            }

            String configured = PreferenceManager.getDefaultSharedPreferences(
                    getApplicationContext()).getString("jellyfin_server_url", "");
            if (configured == null || configured.trim().isEmpty())
            {
                return true;
            }

            String configuredHost = Uri.parse(configured.trim()).getHost();
            if (configuredHost == null || configuredHost.isEmpty())
            {
                return true;
            }

            String incomingHost = incoming.getHost();
            return incomingHost != null && incomingHost.equalsIgnoreCase(configuredHost);
        }
        catch (Exception e)
        {
            Log.w("Play", "Could not compare the intent host with the configured server: " + e.getMessage());
            return true;
        }
    }

    protected void buildDefaultIntent(String path)
    {
        newIntent = new Intent(Intent.ACTION_VIEW);
        newIntent.setDataAndTypeAndNormalize(Uri.parse(path != null ? path : ""), "video/*" );
    }

    /**
     * Decides where playback starts, in milliseconds.
     *
     * The Jellyfin Android client always puts a "position" extra on the external player intent
     * and sets it to 0 when the user picks "Play from beginning", so a present extra always wins,
     * 0 included. Only when the extra is absent does the item's saved server position apply.
     *
     * @param hasIntentPosition   true when the calling intent carried a "position" extra
     * @param intentPositionMs    the value of that extra, in milliseconds
     * @param serverPositionTicks the item's saved PlaybackPositionTicks from the server
     * @return the start offset in milliseconds, never negative
     */
    static int resolveStartPositionMs(boolean hasIntentPosition, int intentPositionMs, long serverPositionTicks)
    {
        if(hasIntentPosition)
        {
            return Math.max(intentPositionMs, 0);
        }
        if(serverPositionTicks > 0)
        {
            return (int) JellyfinApi.ticksToMs(serverPositionTicks);
        }
        return 0;
    }

    /**
     * Decides the position handed back to the launching client on the result intent, in
     * milliseconds.
     *
     * The official Jellyfin Android TV client sends its own playback stop when this activity
     * returns, using this value. A null or absent value means played to completion on the
     * server, which is why a value is always returned. Four rules, in order:
     *
     * <ol>
     *   <li>The current item is not the one the client launched (Up Next or a Zidoo file advance
     *       moved us on): return 0, because the client would otherwise write another item's
     *       position onto the original, and 0 also keeps the client from starting its own next
     *       episode on top of ours.</li>
     *   <li>The item is watched: return 0. This app has already marked it played with position 0,
     *       so 0 is what the client should store too.</li>
     *   <li>A real final position is known: return it, so the client stores what this app stored.</li>
     *   <li>Nothing is known: return the fallback, the position playback started from.</li>
     * </ol>
     *
     * @param originalItemId    the item the launching client handed us
     * @param currentItemId     the item playing when playback stopped, may be null
     * @param finalPositionMs   the final playback position in milliseconds, 0 when unknown
     * @param finalPositionTicks the same position in ticks, for the watched check
     * @param durationTicks     the duration used for the watched threshold, 0 when unknown
     * @param fallbackMs        the position playback started from
     * @return the position to put on the result intent, never negative
     */
    static long resolveResultPositionMs(String originalItemId, String currentItemId,
            long finalPositionMs, long finalPositionTicks, long durationTicks, long fallbackMs)
    {
        if(currentItemId == null || !currentItemId.equals(originalItemId))
        {
            return 0;
        }
        if(JellyfinApi.isWatched(finalPositionTicks, durationTicks))
        {
            return 0;
        }
        if(finalPositionMs > 0)
        {
            return finalPositionMs;
        }
        return Math.max(0, fallbackMs);
    }

    /**
     * True when consecutive polls report the same position, which means the Zidoo player is
     * paused (or otherwise not advancing). A negative baseline means no previous poll, so the
     * state is unknown and reported as not paused.
     *
     * @param lastPollPositionMs position reported by the previous poll, or -1 when there is none
     * @param currentPositionMs  position reported by this poll
     */
    static boolean isPlayerPaused(long lastPollPositionMs, long currentPositionMs)
    {
        return lastPollPositionMs >= 0 && currentPositionMs == lastPollPositionMs;
    }

    /**
     * True when the one-time audio and subtitle selection may be sent to the Zidoo player.
     *
     * A track switch sent to a paused Realtek player re-primes the pipeline and resumes
     * playback, so the selection waits until two consecutive polls show the position moving
     * forward, which confirms the player is actually playing. A single poll is not enough,
     * because the first poll after a resume reports a position without proving it advances.
     *
     * @param tracksSet          true once the selection has already been applied for this episode
     * @param lastPollPositionMs position reported by the previous poll, or -1 when there is none
     * @param currentPositionMs  position reported by this poll
     */
    static boolean shouldApplyTrackSelection(boolean tracksSet, long lastPollPositionMs, long currentPositionMs)
    {
        return !tracksSet
                && currentPositionMs > 0
                && lastPollPositionMs >= 0
                && currentPositionMs > lastPollPositionMs;
    }

    protected void buildZidooIntent(String path, int viewOffset)
    {
        // see https://github.com/Andy2244/jellyfin-androidtv-zidoo/blob/Zidoo-Edition/app/src/main/java/org/jellyfin/androidtv/ui/playback/ExternalPlayerActivity.java
        // NOTE: This code requires the new ZIDOO API to work.
        //       For Z9X and Z9X Pro lines that means firmware version 6.4.42+
        //       For Z9X 8K Line that means firmware version 1.1.42+
        newIntent = new Intent(Intent.ACTION_VIEW);

        // A caller can hand us an intent with no data, so treat a missing path as empty
        String safePath = (path != null) ? path : "";

        // If it is a file, it will be played directly
        if(safePath.startsWith("/") && new File(safePath).exists())
        {
            newIntent.setDataAndTypeAndNormalize(Uri.fromFile(new File(safePath)), "video/*");
        }
        else
        {
            newIntent.setDataAndTypeAndNormalize(Uri.parse(safePath), "video/*");
        }

        newIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        newIntent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

        if (useNewZdiooPlayer)
        {
            newIntent.setPackage("com.zidoo.player");
            newIntent.setClassName("com.zidoo.player", "com.zidoo.player.activity.PlayerActivity");
        }
        else
        {
            newIntent.setPackage("com.android.gallery3d");
            newIntent.setClassName("com.android.gallery3d", "com.android.gallery3d.app.MovieActivity");
        }

        if (PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("showTitle", true))
        {
            newIntent.putExtra("title", videoTitle);
        }
        else
        {
            newIntent.putExtra("title", "");
        }

        // Seed the position we hand back to the launching client with the position playback is
        // starting from. It is the fallback for every exit where playback never reports a final
        // position, so the client writes back the resume point the user already had.
        if(viewOffset > 0)
        {
            resultPositionMs = viewOffset;
            newIntent.putExtra("from_start", false);
            newIntent.putExtra("position", viewOffset);
        }
        else
        {
            resultPositionMs = 0;
            newIntent.putExtra("from_start", true);
        }

        if(audioSelected)
        {
            newIntent.putExtra("audio_idx", selectedAudioIndex);
        }

        if(subtitleSelected)
        {
            newIntent.putExtra("subtitle_idx", selectedSubtitleIndex);
        }

        newIntent.putExtra("return_result", true);
    }

    private void startProgressPoller() {
        if (progressPoller != null) return; // Already running

        java.util.concurrent.ScheduledExecutorService poller =
                java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        progressPoller = poller;
        // Self-rescheduling: 10s normally, 3s when < 60s remaining
        scheduleNextPoll(poller, 3000); // Initial 3s delay for Zidoo player startup
    }

    /**
     * Schedules one poll on the executor it was started with. The executor instance is carried
     * through every reschedule and compared against the live field, so a task still in flight
     * when the poller is replaced stops instead of chaining onto the new one.
     */
    private void scheduleNextPoll(final java.util.concurrent.ScheduledExecutorService poller, long delayMs) {
        if (poller == null || poller.isShutdown()) return;
        if (progressPoller != poller) return; // A newer poller has taken over

        poller.schedule(() -> {
            if (progressPoller != poller) {
                Log.d("Play", "Stale poll task, a newer poller is running");
                return;
            }
            long nextDelay = 10000; // Default: 10 seconds
            try {
                okhttp3.Request request = new okhttp3.Request.Builder()
                        .url("http://127.0.0.1:9529/ZidooVideoPlay/getPlayStatus")
                        .build();

                // Synchronous call -- runs on executor thread, not main thread
                try (okhttp3.Response response = getLocalClient().newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(
                                response.body().string()).getAsJsonObject();
                        if (root.has("video")) {
                            com.google.gson.JsonObject video = root.getAsJsonObject("video");

                            // Track the file path Zidoo is currently playing
                            String nowPlayingPath = video.has("path") ? video.get("path").getAsString() : null;

                            // An empty path means the player is between files: still opening one, or shutting down.
                            // Position and duration read 0 at those moments, so nothing in this poll is trusted.
                            if (nowPlayingPath != null && nowPlayingPath.trim().isEmpty()) {
                                Log.d("Play", "Poll: player between files (empty path), skipping");
                            } else {
                                // Detect Zidoo auto-advancing to next file — track it
                                // The Zidoo reports the same file first as the launch URI and later as its mount path, and that is not an advance.
                                if (JellyfinApi.isZidooFileChange(nowPlayingPath, currentPlayingPath)) {
                                    Log.d("Play", "Zidoo advanced to next file: " + maskCredentials(nowPlayingPath));
                                    currentPlayingPath = nowPlayingPath;

                                    // Save the previous file's position before tracking moves on. The
                                    // position has not been overwritten by this poll yet.
                                    final String previousItemId = jellyfinItemId;
                                    final String previousSessionId = playSessionId;
                                    final long previousPositionMs = lastKnownPositionMs;
                                    if (!previousItemId.isEmpty() && !serverUrl.isEmpty() && !accessToken.isEmpty()) {
                                        JellyfinApi.reportPlaybackStopped(serverUrl, accessToken, previousItemId,
                                                previousSessionId, JellyfinApi.msToTicks(previousPositionMs),
                                                new JellyfinApi.SimpleCallback() {
                                                    @Override public void onSuccess(String msg) { }
                                                    @Override public void onError(String error) {
                                                        Log.w("Play", "Failed to report stop for the previous file: " + error);
                                                    }
                                                });
                                    }
                                    // Cleared right away so this poll cannot report the new file's
                                    // position against the previous item; onFound sets the new id.
                                    jellyfinItemId = "";
                                    playSessionId = "";

                                    // Reset per-episode state for the new file
                                    introSkipArmed = true;
                                    creditSkipArmed = true;
                                    introStartMs = -1; introEndMs = -1;
                                    creditStartMs = -1; creditEndMs = -1;
                                    lastPollPositionMs = -1;
                                    tracksSet = false;
                                    introSegmentsFetched = false;
                                    jellyfinAudioStreamIndex = -1;   // Reset stale indices from previous episode
                                    jellyfinSubtitleStreamIndex = -1; // Will be re-resolved from new episode's MediaStreams
                                    mediaStreams = null;               // Will be re-fetched with getItem for new episode

                                    if (upNextTriggered) {
                                        Log.w("Play", "Auto-advance despite stop command — stop may have failed");
                                    }

                                    // Resolve new episode's Jellyfin item ID via path search (binge path)
                                    String[][] subRules = getSubstitutionRules();
                                    String reversedPath = JellyfinApi.reverseSubstitution(nowPlayingPath, subRules);
                                    if (reversedPath != null && !serverUrl.isEmpty() && !accessToken.isEmpty()) {
                                        JellyfinApi.searchItemByPath(serverUrl, accessToken, reversedPath,
                                                new JellyfinApi.SearchByPathCallback() {
                                                    @Override
                                                    public void onFound(String itemId) {
                                                        Log.d("Play", "Binge episode resolved: " + itemId);
                                                        jellyfinItemId = itemId;
                                                        playSessionId = java.util.UUID.randomUUID().toString().replace("-", "");
                                                        durationTicks = 0;
                                                        upNextTriggered = false;

                                                        // Re-fetch item details for MediaStreams
                                                        JellyfinApi.getItemDetailed(serverUrl, accessToken, itemId, new JellyfinApi.DetailedCallback() {
                                                            @Override
                                                            public void onSuccess(String serverPath, long posTicks, String title, long durTicks, String sid, String rawBody) {
                                                                durationTicks = durTicks;
                                                                videoTitle = title;
                                                                if (sid != null) seriesId = sid;

                                                                // Extract MediaStreams for track mapping
                                                                try {
                                                                    com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(rawBody).getAsJsonObject();
                                                                    if (root.has("MediaSources")) {
                                                                        com.google.gson.JsonArray sources = root.getAsJsonArray("MediaSources");
                                                                        if (sources.size() > 0) {
                                                                            mediaStreams = sources.get(0).getAsJsonObject().getAsJsonArray("MediaStreams");
                                                                        }
                                                                    }
                                                                } catch (Exception e) {
                                                                    Log.w("Play", "Failed to extract MediaStreams for binge ep: " + e.getMessage());
                                                                }

                                                                // Apply default stream fallbacks
                                                                if (mediaStreams != null) {
                                                                    jellyfinAudioStreamIndex = JellyfinApi.findDefaultStreamIndex(mediaStreams, "Audio");
                                                                    jellyfinSubtitleStreamIndex = JellyfinApi.findDefaultStreamIndex(mediaStreams, "Subtitle");
                                                                }

                                                                // Report playback start for new episode
                                                                JellyfinApi.reportPlaybackStart(serverUrl, accessToken, jellyfinItemId,
                                                                        playSessionId, new JellyfinApi.SimpleCallback() {
                                                                    @Override public void onSuccess(String msg) { }
                                                                    @Override public void onError(String error) {
                                                                        Log.w("Play", "Failed to report start for binge ep: " + error);
                                                                    }
                                                                });
                                                            }
                                                            @Override
                                                            public void onError(String error) {
                                                                Log.w("Play", "Failed to get binge episode details: " + error);
                                                            }
                                                        });

                                                        // Re-fetch IntroSkipper segments for the new binge episode
                                                        JellyfinApi.getIntroSkipperSegments(serverUrl, accessToken, itemId, new JellyfinApi.SimpleCallback() {
                                                            @Override
                                                            public void onSuccess(String message) {
                                                                try {
                                                                    JellyfinApi.IntroSkipperResult result = JellyfinApi.parseIntroSkipperResponse(message);
                                                                    introStartMs = result.introStartMs();
                                                                    introEndMs = result.introEndMs();
                                                                    creditStartMs = result.creditStartMs();
                                                                    creditEndMs = result.creditEndMs();
                                                                } catch (Exception e) {
                                                                    Log.w("Play", "Could not read intro skipper segments for the binge episode: " + e.getMessage());
                                                                }
                                                                introSegmentsFetched = true;
                                                            }
                                                            @Override
                                                            public void onError(String error) { introSegmentsFetched = true; /* silent no-op */ }
                                                        });
                                                    }

                                                    @Override
                                                    public void onNotFound(String error) {
                                                        Log.w("Play", "Binge episode not found by path: " + error);
                                                        // Stop reporting the new file's progress against the previous
                                                        // episode, which would overwrite its resume position
                                                        jellyfinItemId = "";
                                                        playSessionId = "";
                                                    }
                                                });
                                    } else {
                                        // No reversed path means no item can be matched for this file
                                        Log.w("Play", "Could not reverse the new file path, progress reporting paused");
                                        jellyfinItemId = "";
                                        playSessionId = "";
                                    }
                                }

                                if (nowPlayingPath != null && !nowPlayingPath.isEmpty() && currentPlayingPath == null) {
                                    currentPlayingPath = nowPlayingPath;
                                }

                                if (video.has("duration")) {
                                    lastKnownDurationMs = video.get("duration").getAsLong();
                                }

                                if (video.has("currentPosition")) {
                                    long currentPositionMs = video.get("currentPosition").getAsLong();
                                    lastKnownPositionMs = currentPositionMs;

                                    // A track switch sent to a paused Realtek player re-primes the
                                    // pipeline and resumes playback on its own, so the one-time
                                    // audio/subtitle selection waits until the position is confirmed
                                    // to be advancing between polls.
                                    boolean playerPaused = isPlayerPaused(lastPollPositionMs, currentPositionMs);
                                    boolean applyTracks = shouldApplyTrackSelection(tracksSet, lastPollPositionMs, currentPositionMs);
                                    if (!tracksSet && !applyTracks) {
                                        Log.d("Play", "Track selection deferred: player not advancing (pos=" + currentPositionMs
                                                + " lastPos=" + lastPollPositionMs + " paused=" + playerPaused + ")");
                                    }
                                    if (applyTracks) {
                                        tracksSet = true;
                                        Log.d("Play", "Track selection applied at pos=" + currentPositionMs
                                                + " (advanced from " + lastPollPositionMs + ")");
                                        new Thread(() -> {
                                            try { Thread.sleep(500); } catch (InterruptedException e) { return; }
                                            if (jellyfinAudioStreamIndex >= 0 && mediaStreams != null) {
                                                int zidooAudioIdx = JellyfinApi.jellyfinToZidooAudioIndex(mediaStreams, jellyfinAudioStreamIndex);
                                                if (zidooAudioIdx >= 0) setZidooAudio(zidooAudioIdx);
                                            }
                                            if (jellyfinSubtitleStreamIndex >= 0 && mediaStreams != null) {
                                                int zidooSubIdx = JellyfinApi.jellyfinToZidooSubtitleIndex(mediaStreams, jellyfinSubtitleStreamIndex);
                                                if (zidooSubIdx >= 0) setZidooSubtitle(zidooSubIdx);
                                            }
                                        }).start();
                                    }

                                    long positionTicks = JellyfinApi.msToTicks(currentPositionMs);
                                    // Report progress to Jellyfin
                                    if (!jellyfinItemId.isEmpty() && !serverUrl.isEmpty() && !accessToken.isEmpty()) {
                                        JellyfinApi.reportPlaybackProgress(serverUrl, accessToken,
                                                jellyfinItemId, playSessionId, positionTicks, playerPaused,
                                                new JellyfinApi.SimpleCallback() {
                                                    @Override public void onSuccess(String msg) { }
                                                    @Override public void onError(String error) {
                                                        Log.w("Play", "Progress report failed: " + error);
                                                    }
                                                });
                                    }

                                    // Adaptive polling: speed up when nearing end of episode
                                    long remainingMs = lastKnownDurationMs - currentPositionMs;
                                    String seriesIdForLog = seriesId.isEmpty() ? "EMPTY"
                                            : (seriesId.length() > 8 ? seriesId.substring(0, 8) : seriesId);
                                    Log.d("Play", "Poll: pos=" + currentPositionMs + " dur=" + lastKnownDurationMs + " remaining=" + remainingMs + "ms seriesId=" + seriesIdForLog);
                                    if (lastKnownDurationMs > 0 && remainingMs < 60000) {
                                        nextDelay = 3000; // 3s polls in final minute
                                    }

                                    // Read settings toggles
                                    android.content.SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(Play.this);
                                    boolean skipIntrosEnabled = prefs.getBoolean("skip_intros", true);
                                    boolean skipCreditsEnabled = prefs.getBoolean("skip_credits", true);

                                    // Detect manual seek (position jump detection)
                                    if (lastPollPositionMs >= 0) {
                                        long positionDelta = currentPositionMs - lastPollPositionMs;
                                        boolean likelyManualSeek = positionDelta < -3000 || positionDelta > 30000;

                                        if (likelyManualSeek) {
                                            if (introSkipArmed && currentPositionMs < introEndMs) {
                                                introSkipArmed = false;
                                                Log.d("Play", "Intro skip disarmed (manual seek to " + currentPositionMs + "ms)");
                                            }
                                            if (creditSkipArmed && currentPositionMs < creditStartMs) {
                                                creditSkipArmed = false;
                                                Log.d("Play", "Credit skip disarmed (manual seek to " + currentPositionMs + "ms)");
                                            }
                                        }
                                    }

                                    // Intro skip check (only after we have baseline position -- NOT on first poll)
                                    if (skipIntrosEnabled && introSkipArmed && introStartMs >= 0
                                            && lastPollPositionMs >= 0  // Must have baseline (resume protection)
                                            && currentPositionMs >= introStartMs && currentPositionMs < introEndMs) {
                                        Log.d("Play", "Skipping intro: seeking from " + currentPositionMs + "ms to " + introEndMs + "ms");
                                        seekZidoo(introEndMs);
                                        introSkipArmed = false;  // Prevent re-triggering
                                    }

                                    // Credit skip check (only for TV shows with seriesId, and only after baseline)
                                    if (skipCreditsEnabled && creditSkipArmed && creditStartMs >= 0
                                            && lastPollPositionMs >= 0
                                            && !seriesId.isEmpty()  // TV shows only
                                            && currentPositionMs >= creditStartMs
                                            && !upNextTriggered) {
                                        Log.d("Play", "Credits reached at " + currentPositionMs + "ms, triggering Up Next");
                                        upNextTriggered = true;
                                        creditSkipArmed = false;
                                        // Same pattern as generic stop — finishActivity triggers onActivityResult → handleEpisodeCompleted
                                        runOnUiThread(() -> {
                                            try {
                                                finishActivity(98);
                                                Log.d("Play", "Credit skip: finishActivity(98) sent");
                                            } catch (Exception e) {
                                                Log.w("Play", "Credit skip: finishActivity failed: " + e.getMessage());
                                            }
                                        });
                                    }

                                    lastPollPositionMs = currentPositionMs;

                                    // There is deliberately no unconditional stop near the end of an episode.
                                    // The player is only stopped early when Intro Skipper gave us a credits
                                    // segment and playback reached it (the credit skip check above). Without a
                                    // credits segment the episode plays to its natural end and the Zidoo end of
                                    // playback result drives the Up Next flow in onActivityResult.

                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.w("Play", "Zidoo poll failed: " + e.getMessage());
            }

            // Schedule next poll on the same executor this task was started on
            scheduleNextPoll(poller, nextDelay);
        }, delayMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * Called when the player is stopped near end of episode (~10s before end).
     * Reports the episode as stopped/watched, then launches Up Next.
     */
    private void handleEpisodeCompleted() {
        // The item is watched or Up Next has moved on, and both want 0 so the launching client
        // neither wipes a real resume point nor auto plays its own next episode.
        resultPositionMs = 0;
        handlingPlaybackResult = true;
        runOnUiThread(() -> {
            stopProgressPoller();

            if (jellyfinItemId.isEmpty() || serverUrl.isEmpty() || accessToken.isEmpty()) {
                handlingPlaybackResult = false;
                finishWithResult();
                return;
            }

            long finalTicks = durationTicks > 0 ? durationTicks
                    : (lastKnownDurationMs > 0 ? JellyfinApi.msToTicks(lastKnownDurationMs)
                    : JellyfinApi.msToTicks(lastKnownPositionMs));

            JellyfinApi.reportPlaybackStopped(serverUrl, accessToken, jellyfinItemId,
                    playSessionId, finalTicks, new JellyfinApi.SimpleCallback() {
                @Override
                public void onSuccess(String msg) {
                    JellyfinApi.markAsWatched(serverUrl, accessToken, userId,
                            jellyfinItemId, new JellyfinApi.SimpleCallback() {
                        @Override
                        public void onSuccess(String msg) {
                            Log.d("Play", "Episode completed, marked as watched");
                            if (!seriesId.isEmpty()) {
                                launchUpNext();
                            } else {
                                runOnUiThread(() -> finishWithResult());
                            }
                        }
                        @Override
                        public void onError(String error) {
                            Log.w("Play", "Failed to mark watched: " + error);
                            if (!seriesId.isEmpty()) {
                                launchUpNext();
                            } else {
                                runOnUiThread(() -> finishWithResult());
                            }
                        }
                    });
                }
                @Override
                public void onError(String error) {
                    Log.w("Play", "Failed to report stop on episode complete: " + error);
                    if (!seriesId.isEmpty()) {
                        launchUpNext();
                    } else {
                        runOnUiThread(() -> finishWithResult());
                    }
                }
            });
        });
    }

    /**
     * Launches the Up Next countdown screen showing the next episode in the series.
     * Queries Jellyfin for next up details, then starts UpNextActivity.
     */
    private void launchUpNext() {
        Log.d("Play", "launchUpNext called, seriesId=" + seriesId + " isFinishing=" + isFinishing());
        if (seriesId.isEmpty() || serverUrl.isEmpty() || accessToken.isEmpty()) {
            Log.w("Play", "launchUpNext: missing credentials, finishing");
            runOnUiThread(this::finish);
            return;
        }

        JellyfinApi.getNextUpWithDetails(serverUrl, accessToken, userId, seriesId,
            new JellyfinApi.NextUpDetailCallback() {
                @Override
                public void onResult(String nextItemId, String seriesName, String episodeName,
                                     int seasonNumber, int episodeNumber, String sid, String serverPath) {
                    Log.d("Play", "NextUp found: " + seriesName + " S" + seasonNumber + "E" + episodeNumber);
                    runOnUiThread(() -> {
                        waitingForUpNext = true;
                        Intent upNextIntent = new Intent(Play.this, UpNextActivity.class);
                        upNextIntent.putExtra("nextItemId", nextItemId);
                        upNextIntent.putExtra("seriesName", seriesName);
                        upNextIntent.putExtra("episodeName", episodeName);
                        upNextIntent.putExtra("seasonNumber", seasonNumber);
                        upNextIntent.putExtra("episodeNumber", episodeNumber);
                        upNextIntent.putExtra("seriesId", sid);
                        upNextIntent.putExtra("serverPath", serverPath);
                        upNextIntent.putExtra("backdropUrl", serverUrl + "/Items/" + sid + "/Images/Backdrop");
                        upNextIntent.putExtra("serverUrl", serverUrl);
                        // The access token is deliberately not passed, UpNextActivity does not need it
                        startActivityForResult(upNextIntent, UP_NEXT_REQUEST_CODE);
                    });
                }

                @Override
                public void onNoNextEpisode() {
                    Log.d("Play", "No next episode (series finale), finishing");
                    runOnUiThread(() -> finishWithResult());
                }
            });
    }

    /**
     * Extracts all configured substitution rules from SharedPreferences.
     * Returns a 2D array where each entry is {pathToReplace, replacedWith}.
     * Each slot is split on commas exactly like doSubstitution does, so a slot holding
     * several pairs produces several rules and the reverse lookup can match them.
     */
    private String[][] getSubstitutionRules() {
        String[] prefIndex = {"", "_02", "_03", "_04", "_05", "_06", "_07", "_08", "_09", "_10"};
        List<String[]> rules = new ArrayList<>();
        android.content.SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        for (String s : prefIndex) {
            String pathToReplaceSlot = prefs.getString("path_to_replace" + s, "");
            String replacedWithSlot = prefs.getString("replaced_with" + s, "");
            if (pathToReplaceSlot == null) pathToReplaceSlot = "";
            if (replacedWithSlot == null) replacedWithSlot = "";

            String[] pathToReplaceArray = pathToReplaceSlot.split("\\s*,\\s*");
            String[] replacedWithArray = replacedWithSlot.split("\\s*,\\s*");
            if (pathToReplaceArray.length != replacedWithArray.length) {
                continue;
            }

            for (int i = 0; i < pathToReplaceArray.length; i++) {
                String pathToReplace = pathToReplaceArray[i].trim();
                String replacedWith = replacedWithArray[i].trim();
                if (!pathToReplace.isEmpty() && !replacedWith.isEmpty()) {
                    rules.add(new String[]{pathToReplace, replacedWith});
                }
            }
        }
        return rules.toArray(new String[0][]);
    }

    private void stopProgressPoller() {
        if (progressPoller != null) {
            progressPoller.shutdownNow();
            progressPoller = null;
        }
    }

    /**
     * Sets result data with current episode info before finishing, so the calling
     * Jellyfin client can navigate to the last-played episode instead of the original.
     */
    private void finishWithResult() {
        if (!jellyfinItemId.isEmpty() && !serverUrl.isEmpty()) {
            Intent resultData = new Intent();
            resultData.setData(Uri.parse(serverUrl + "/Videos/" + jellyfinItemId + "/stream"));
            resultData.putExtra("itemId", jellyfinItemId);
            // "position" is the MX Player result API key the official Jellyfin Android TV client
            // reads off our result intent for the playback stop it sends on its own.
            resultData.putExtra("position", (int) resultPositionMs);
            setResult(RESULT_OK, resultData);
        }
        finish();
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        stopProgressPoller();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);

        // Handle Up Next result
        if (requestCode == UP_NEXT_REQUEST_CODE) {
            waitingForUpNext = false;
            if (resultCode == Activity.RESULT_OK && data != null) {
                // User chose to play next episode — set up fresh tracking and launch Zidoo
                String nextItemId = data.getStringExtra("nextItemId");
                String nextServerPath = data.getStringExtra("serverPath");
                String nextSeriesId = data.getStringExtra("seriesId");

                // Reset tracking state for new episode
                jellyfinItemId = nextItemId;
                seriesId = nextSeriesId != null ? nextSeriesId : seriesId;
                playSessionId = java.util.UUID.randomUUID().toString().replace("-", "");
                durationTicks = 0;
                lastKnownPositionMs = 0;
                lastKnownDurationMs = 0;
                currentPlayingPath = null;
                upNextTriggered = false;

                // Reset intro/credit skip and track state for new episode
                introSkipArmed = true;
                creditSkipArmed = true;
                introStartMs = -1; introEndMs = -1;
                creditStartMs = -1; creditEndMs = -1;
                lastPollPositionMs = -1;
                tracksSet = false;
                introSegmentsFetched = false;
                jellyfinAudioStreamIndex = -1;
                jellyfinSubtitleStreamIndex = -1;
                mediaStreams = null;

                // Resolve path and launch Zidoo player
                Log.d("Play", "Play Now: nextServerPath=" + nextServerPath);
                foundSubstitution = false;
                doSubstitution(nextServerPath);
                Log.d("Play", "Play Now: foundSubstitution=" + foundSubstitution + " directPath=" + maskCredentials(directPath));
                // Capture resolved SMB path — directPath is an instance var that onStart() can overwrite
                final String resolvedSmbPath = directPath;
                if (foundSubstitution) {
                    // Get item details for duration tracking (detailed for MediaStreams)
                    JellyfinApi.getItemDetailed(serverUrl, accessToken, nextItemId, new JellyfinApi.DetailedCallback() {
                        @Override
                        public void onSuccess(String path, long posTicks, String title, long durTicks, String sid, String rawBody) {
                            durationTicks = durTicks;
                            videoTitle = title;

                            // Extract MediaStreams for track mapping
                            try {
                                com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(rawBody).getAsJsonObject();
                                if (root.has("MediaSources")) {
                                    com.google.gson.JsonArray sources = root.getAsJsonArray("MediaSources");
                                    if (sources.size() > 0) {
                                        mediaStreams = sources.get(0).getAsJsonObject().getAsJsonArray("MediaStreams");
                                    }
                                }
                            } catch (Exception e) {
                                Log.w("Play", "Failed to extract MediaStreams for next ep: " + e.getMessage());
                            }

                            // Apply default stream fallbacks
                            if (mediaStreams != null) {
                                jellyfinAudioStreamIndex = JellyfinApi.findDefaultStreamIndex(mediaStreams, "Audio");
                                jellyfinSubtitleStreamIndex = JellyfinApi.findDefaultStreamIndex(mediaStreams, "Subtitle");
                            }

                            // Fetch IntroSkipper segments for next episode
                            if (!introSegmentsFetched) {
                                introSegmentsFetched = true;
                                JellyfinApi.getIntroSkipperSegments(serverUrl, accessToken, jellyfinItemId, new JellyfinApi.SimpleCallback() {
                                    @Override
                                    public void onSuccess(String message) {
                                        try {
                                            JellyfinApi.IntroSkipperResult result = JellyfinApi.parseIntroSkipperResponse(message);
                                            introStartMs = result.introStartMs();
                                            introEndMs = result.introEndMs();
                                            creditStartMs = result.creditStartMs();
                                            creditEndMs = result.creditEndMs();
                                        } catch (Exception e) {
                                            Log.w("Play", "Could not read intro skipper segments for the next episode: " + e.getMessage());
                                        }
                                    }
                                    @Override
                                    public void onError(String error) { /* silent no-op */ }
                                });
                            }

                            // Report playback start
                            JellyfinApi.reportPlaybackStart(serverUrl, accessToken, jellyfinItemId,
                                    playSessionId, new JellyfinApi.SimpleCallback() {
                                @Override public void onSuccess(String msg) { }
                                @Override public void onError(String error) {
                                    Log.w("Play", "Failed to report start for next ep: " + error);
                                }
                            });
                            // Launch Zidoo player — must be on UI thread
                            runOnUiThread(() -> {
                                handlingPlaybackResult = false; // Reset so NEW Zidoo results are processed
                                awaitingPlaybackResult = true;
                                buildZidooIntent(resolvedSmbPath, 0);
                                startActivityForResult(newIntent, 98);
                                startProgressPoller();
                            });
                        }
                        @Override
                        public void onError(String error) {
                            Log.w("Play", "Failed to get next episode details: " + error);
                            // Launch anyway with what we have — must be on UI thread
                            runOnUiThread(() -> {
                                handlingPlaybackResult = false; // Reset so NEW Zidoo results are processed
                                awaitingPlaybackResult = true;
                                buildZidooIntent(resolvedSmbPath, 0);
                                startActivityForResult(newIntent, 98);
                                startProgressPoller();
                            });
                        }
                    });
                } else {
                    Log.e("Play", "No substitution rule matched for next episode path: " + nextServerPath);
                    Toast.makeText(getApplicationContext(), "Cannot resolve next episode path", Toast.LENGTH_LONG).show();
                    finishWithResult();
                }
            } else {
                // User canceled Up Next — go back
                handlingPlaybackResult = false;
                finishWithResult();
            }
            return; // Don't fall through to Zidoo result handling
        }

        // Clear awaiting flag — we've received the Zidoo player result
        awaitingPlaybackResult = false;

        // Ignore stale Zidoo results — if handleEpisodeCompleted is already
        // running (or completed), this is a duplicate result from finishActivity(98)
        if (handlingPlaybackResult) {
            Log.d("Play", "Ignoring stale Zidoo result (episode completion already in progress)");
            return;
        }

        // Stop the progress poller immediately
        stopProgressPoller();

        // Guard against onRestart()->finish() during async callbacks
        handlingPlaybackResult = true;

        // If we triggered Up Next (stop before end), route to episode completion handler
        if (upNextTriggered) {
            upNextTriggered = false;
            handleEpisodeCompleted();
            return;
        }

        // Skip reporting for non-Jellyfin playback or ZDMC
        if (jellyfinItemId.isEmpty() || serverUrl.isEmpty() || accessToken.isEmpty() || zdmc) {
            String skipReason = zdmc ? "zdmc"
                    : jellyfinItemId.isEmpty() ? "item id empty"
                    : serverUrl.isEmpty() ? "no server"
                    : "no token";
            Log.w("Play", "Stop report skipped: " + skipReason);
            handlingPlaybackResult = false;
            finishWithResult();
            return;
        }

        // Get final position from Zidoo player result
        long finalPositionMs = 0;
        if (resultCode == Activity.RESULT_OK && requestCode == 98 && data != null) {
            finalPositionMs = data.getIntExtra("position", 0);
        }
        // Fallback to last polled position if Zidoo didn't return one
        if (finalPositionMs <= 0) {
            finalPositionMs = lastKnownPositionMs;
        }

        final long finalPositionTicks = JellyfinApi.msToTicks(finalPositionMs);

        // Duration used for the watched threshold on a natural end of playback. Jellyfin is the
        // first choice; when the item lookup gave us nothing, fall back to the duration the Zidoo
        // player reported while polling, so an episode that runs to its end still crosses the
        // threshold and still opens Up Next.
        final long effectiveDurationTicks = durationTicks > 0 ? durationTicks
                : (lastKnownDurationMs > 0 ? JellyfinApi.msToTicks(lastKnownDurationMs) : 0);

        // Work out what the launching client should store. The current value of resultPositionMs
        // is the start position seeded when the Zidoo intent was built, used as the fallback.
        resultPositionMs = resolveResultPositionMs(originalItemId, jellyfinItemId, finalPositionMs,
                finalPositionTicks, effectiveDurationTicks, resultPositionMs);

        // Report playback stopped to Jellyfin
        JellyfinApi.reportPlaybackStopped(serverUrl, accessToken, jellyfinItemId,
                playSessionId, finalPositionTicks, new JellyfinApi.SimpleCallback() {
            @Override
            public void onSuccess(String msg) {
                // Check 90% watched threshold
                if (JellyfinApi.isWatched(finalPositionTicks, effectiveDurationTicks)) {
                    JellyfinApi.markAsWatched(serverUrl, accessToken, userId,
                            jellyfinItemId, new JellyfinApi.SimpleCallback() {
                        @Override
                        public void onSuccess(String msg) {
                            Log.d("Play", "Marked as watched");
                            if (!seriesId.isEmpty()) {
                                launchUpNext();
                            } else {
                                handlingPlaybackResult = false;
                                runOnUiThread(() -> finishWithResult());
                            }
                        }
                        @Override
                        public void onError(String error) {
                            Log.w("Play", "Failed to mark watched: " + error);
                            if (!seriesId.isEmpty()) {
                                launchUpNext();
                            } else {
                                handlingPlaybackResult = false;
                                runOnUiThread(() -> finishWithResult());
                            }
                        }
                    });
                } else {
                    handlingPlaybackResult = false;
                    runOnUiThread(() -> finishWithResult());
                }
            }
            @Override
            public void onError(String error) {
                handlingPlaybackResult = false;
                runOnUiThread(() -> {
                    Toast.makeText(getApplicationContext(),
                            "Couldn't update progress or watched status",
                            Toast.LENGTH_LONG).show();
                    finishWithResult();
                });
            }
        });
    }

    public static String intentToString(Intent intent)
    {
        if (intent == null)
        {
            return "";
        }

        StringBuilder stringBuilder = new StringBuilder("action: ")
                .append(intent.getAction())
                .append(" type: ")
                .append(intent.getType())
                .append(" data: ")
                .append(intent.getDataString())
                ;
        if(intent.getExtras() != null)
        {
            stringBuilder.append(" extras: ");
            for (String key : intent.getExtras().keySet())
            {
                stringBuilder.append(key).append("=").append(intent.getExtras().get(key)).append(" ");
            }
        }

        // Never show the Jellyfin token or an smb password on the debug page
        return maskCredentials(maskTokens(stringBuilder.toString()));
    }
}
