package com.jellyfintozidoo;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
// PLEX_REMOVED_START - Plex XML parsing imports
// import android.util.Xml;
// PLEX_REMOVED_END
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

// PLEX_REMOVED_START - Plex API: Volley HTTP client imports for Plex server communication
// import com.android.volley.Request;
// import com.android.volley.RequestQueue;
// import com.android.volley.toolbox.StringRequest;
// import com.android.volley.toolbox.Volley;
// PLEX_REMOVED_END

// PLEX_REMOVED_START - Plex XML parsing imports
// import org.xmlpull.v1.XmlPullParser;
// PLEX_REMOVED_END

// PLEX_REMOVED_START - Plex API: stream parsing imports
// import java.io.ByteArrayInputStream;
// import java.io.InputStream;
// PLEX_REMOVED_END
import java.io.File;
// PLEX_REMOVED_START - Plex API: library list import
// import java.util.List;
// PLEX_REMOVED_END
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

    // PLEX_REMOVED_START - Plex API: token and client identifier URL parameters
    // static final String tokenParameter = "X-Plex-Token=";
    // static final String clientParameter = "X-Plex-Client-Identifier=";
    // PLEX_REMOVED_END
    private Intent originalIntent;
    private Intent newIntent;
    // PLEX_REMOVED_START - Plex API: server address and library info
    // private String address = "";
    // private PlexLibraryInfo libraryInfo;
    // private String ratingKey = "";
    // private String partKey = "";
    // private String partId = "";
    // private String token = "";
    // private int duration = 0;
    // PLEX_REMOVED_END
    private int viewOffset = 0;
    private String directPath = "";
    private String videoTitle = "";
    private boolean audioSelected = false;
    private int selectedAudioIndex = -1;
    private boolean subtitleSelected = false;
    private int selectedSubtitleIndex = -1;
    private String password = "";
    // PLEX_REMOVED_START - Plex API: video index and parent key for episode navigation
    // private int videoIndex = 0;
    // private String parentRatingKey = "";
    // private String server = "";
    // PLEX_REMOVED_END
    private String message = "";
    private boolean foundSubstitution = false;
    private String videoPath = "";
    private String jellyfinItemId = "";
    private String jellyfinApiPath = "";
    // PLEX_REMOVED_START - Plex API: remote stream and media index tracking
    // private boolean remoteStream = false;
    // PLEX_REMOVED_END
    private boolean zdmc = false;
    private String callerPackage = "";
    private String serverUrl = "";
    private String accessToken = "";
    private String userId = "";
    private String playSessionId = "";
    private long durationTicks = 0;
    private long lastKnownPositionMs = 0;
    private long lastKnownDurationMs = 0;
    private String currentPlayingPath = null;
    private String seriesId = "";
    private volatile boolean upNextTriggered = false;
    private volatile boolean waitingForUpNext = false;
    private volatile boolean handlingPlaybackResult = false;
    private java.util.concurrent.ScheduledExecutorService progressPoller = null;
    // PLEX_REMOVED_START - Plex API: media version index
    // private int mediaIndex = -1;
    // PLEX_REMOVED_END

    private TextView textView1;
    private TextView textView2;
    private Button playButton;

    private boolean useNewZdiooPlayer = false;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        useNewZdiooPlayer = useNewZdiooPlayer();
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
        // Don't finish if we're handling Up Next flow or processing playback results
        if (upNextTriggered || waitingForUpNext || handlingPlaybackResult) {
            return;
        }
        this.finishWithResult();
    }

    // PLEX_REMOVED_START - Plex API: sanitizes Plex server addresses and tokens from debug strings
    // private String getPrintableString(String inputString)
    // {
    //     String outputString = inputString;
    //     outputString = outputString.replaceFirst("https://[^/ ]+", "https://<address>");
    //     outputString = outputString.replaceFirst(tokenParameter + "[^& ]+", tokenParameter + "<token>");
    //     outputString = outputString.replaceFirst(clientParameter + "[^& ]+", clientParameter + "<client>");
    //     return outputString;
    // }
    // PLEX_REMOVED_END

    private void updateDebugPage()
    {
        String originalIntentToPrint = intentToString(originalIntent);
        String newIntentToPrint = intentToString(newIntent);
        String pathToPrint = directPath;

        // If the path has a password in it then hide it from the debug output
        if(!password.isEmpty())
        {
            pathToPrint = pathToPrint.replaceFirst(":" + password + "@", ":********@");
        }

        // PLEX_REMOVED_START - Plex API: library section and media type debug info
        // String librarySection = "";
        // String mediaType = "";
        // if(libraryInfo != null)
        // {
        //     librarySection = libraryInfo.getKey();
        //     mediaType = libraryInfo.getType().name;
        // }
        // PLEX_REMOVED_END

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

    // PLEX_REMOVED_START - Plex API: searches Plex server for next episode file to auto-play
    // private void searchFiles()
    // {
    //     RequestQueue queue = Volley.newRequestQueue(this);
    //     videoIndex++;
    //     String url = address + "/library/sections/" + libraryInfo.getKey() + "/search?type=" + libraryInfo.getType().searchId + "&index=" + videoIndex + "&parent=" + parentRatingKey + "&" + tokenParameter + token;
    //     StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
    //             response ->
    //             {
    //                 PlexLibraryXmlParser parser = new PlexLibraryXmlParser(null, -1);
    //                 InputStream targetStream = new ByteArrayInputStream(response.getBytes());
    //                 try
    //                 {
    //                     String path = parser.parse(targetStream);
    //                     if(!path.isEmpty())
    //                     {
    //                         String inputString = originalIntent.getDataString();
    //                         inputString = inputString.replace(partKey, path);
    //                         originalIntent.setData(Uri.parse(inputString));
    //                         originalIntent.putExtra("viewOffset", 0);
    //                         startActivity(originalIntent);
    //                     }
    //                 }
    //                 catch (Exception e)
    //                 {
    //                     message = "ERROR 6: " + e;
    //                     showDebugPageOrSendIntent();
    //                     return;
    //                 }
    //             },
    //             error ->
    //             {
    //                 message = "WARNING: Couldn't find next file - " + error.toString();
    //                 showDebugPageOrSendIntent();
    //             });
    //     queue.add(stringRequest);
    // }
    // PLEX_REMOVED_END

    // PLEX_REMOVED_START - Plex API: fetches metadata from Plex server for audio/subtitle stream selection
    // private void searchMetadata()
    // {
    //     RequestQueue queue = Volley.newRequestQueue(this);
    //     String url = address + "/library/metadata/" + ratingKey + "?" + tokenParameter + token;
    //     StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
    //             response ->
    //             {
    //                 PlexLibraryXmlParser parser = new PlexLibraryXmlParser(partKey, mediaIndex);
    //                 InputStream targetStream = new ByteArrayInputStream(response.getBytes());
    //                 try
    //                 {
    //                     String path = parser.parse(targetStream);
    //                     if(!path.isEmpty())
    //                     {
    //                         if(!audioSelected)
    //                         {
    //                             audioSelected = parser.isAudioSelected();
    //                             if (audioSelected) { selectedAudioIndex = parser.getSelectedAudioIndex(); }
    //                         }
    //                         if(!subtitleSelected)
    //                         {
    //                             subtitleSelected = parser.isSubtitleSelected();
    //                             if (subtitleSelected) { selectedSubtitleIndex = parser.getSelectedSubtitleIndex(); }
    //                         }
    //                     }
    //                 }
    //                 catch (Exception e)
    //                 {
    //                     message = "ERROR 5: " + e;
    //                 }
    //                 showDebugPageOrSendIntent();
    //             },
    //             error ->
    //             {
    //                 message = "ERROR: Couldn't find metadata - " + error.toString();
    //                 showDebugPageOrSendIntent();
    //             });
    //     queue.add(stringRequest);
    // }
    // PLEX_REMOVED_END

    private void doSubstitution(String path)
    {
        // Check if we can actually do the substitution, if not then pass along the original file and see if it plays
        String[] pref_index = {"", "_02", "_03", "_04", "_05", "_06", "_07", "_08", "_09", "_10"};
        for (String s: pref_index)
        {
            String[] path_to_replace_array = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString("path_to_replace" + s, "").split("\\s*,\\s*");
            String[] replaced_with_array = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString("replaced_with" + s, "").split("\\s*,\\s*");
            String smb_username = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString("smbUsername" + s, "");
            String smb_password = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString("smbPassword" + s, "");

            if (path_to_replace_array.length > 0 && replaced_with_array.length > 0 && path_to_replace_array.length == replaced_with_array.length)
            {
                for (int i = 0; i < path_to_replace_array.length; i++)
                {
                    if (!path_to_replace_array[i].isEmpty() && path.contains(path_to_replace_array[i]))
                    {
                        path = path.replaceFirst(Pattern.quote(path_to_replace_array[i]), replaced_with_array[i]).replace("\\", "/");

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

                        // If this is an SMB request add user name and password to the path
                        if (!smb_username.isEmpty())
                        {
                            password = smb_password;
                            path = path.replace("smb://", "smb://" + smb_username + ":" + password + "@");
                        }

                        foundSubstitution = true;
                        directPath = path;

                        return;
                    }
                }
            }
        }
    }

    // PLEX_REMOVED_START - Plex API: searches Plex library sections for the video file path by partId
    // private void searchPath(List<PlexLibraryInfo> infos, int index)
    // {
    //     PlexLibraryInfo info = infos.get(index);
    //     libraryInfo = info;
    //     RequestQueue queue = Volley.newRequestQueue(this);
    //     String url = address + "/library/sections/" + info.getKey() + "/search?type=" + info.getType().searchId + "&part=" + partId + "&" + tokenParameter + token;
    //     StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
    //             response ->
    //             {
    //                 PlexLibraryXmlParser parser = new PlexLibraryXmlParser(partKey, mediaIndex);
    //                 InputStream targetStream = new ByteArrayInputStream(response.getBytes());
    //                 try
    //                 {
    //                     String path = parser.parse(targetStream);
    //                     if(!path.isEmpty())
    //                     {
    //                         videoPath = path;
    //                         ratingKey = parser.getRatingKey();
    //                         videoTitle = parser.getVideoTitle();
    //                         duration = parser.getDuration();
    //                         videoIndex = parser.getVideoIndex();
    //                         parentRatingKey = parser.getParentRatingKey();
    //                         password = "";
    //                         doSubstitution(path);
    //                         if(!foundSubstitution && originalIntent.getDataString().contains("&location=wan&"))
    //                         {
    //                             remoteStream = true;
    //                             message = "WARNING: Remote Stream - May Not Work";
    //                         }
    //                         searchMetadata();
    //                     }
    //                     else if(index + 1 < infos.size())
    //                     {
    //                         searchPath(infos, index + 1);
    //                     }
    //                     else
    //                     {
    //                         message = "ERROR: Video not found on Plex";
    //                         showDebugPageOrSendIntent();
    //                     }
    //                 }
    //                 catch (Exception e)
    //                 {
    //                     message = "ERROR 4: " + e;
    //                     showDebugPageOrSendIntent();
    //                     return;
    //                 }
    //             },
    //             error ->
    //             {
    //                 if(index + 1 < infos.size())
    //                 {
    //                     searchPath(infos, index + 1);
    //                 }
    //                 else
    //                 {
    //                     message = "ERROR: Couldn't find path - " + error.toString();
    //                     showDebugPageOrSendIntent();
    //                 }
    //             });
    //     queue.add(stringRequest);
    // }
    // PLEX_REMOVED_END

    // PLEX_REMOVED_START - Plex API: fetches library sections list from Plex server
    // private void searchLibrary()
    // {
    //     RequestQueue queue = Volley.newRequestQueue(this);
    //     String url = address + "/library/sections/?" + tokenParameter + token;
    //     StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
    //             response ->
    //             {
    //                 String include_libraries = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString("libraries", "").trim();
    //                 String exclude_libraries = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString("exclude_libraries", "").trim();
    //                 PlexXmlParser parser = new PlexXmlParser(include_libraries, exclude_libraries);
    //                 InputStream targetStream = new ByteArrayInputStream(response.getBytes());
    //                 try
    //                 {
    //                     List<PlexLibraryInfo> libraries = parser.parse(targetStream);
    //                     searchPath(libraries, 0);
    //                     return;
    //                 }
    //                 catch (Exception e)
    //                 {
    //                     message = "ERROR 3: " + e;
    //                     showDebugPageOrSendIntent();
    //                     return;
    //                 }
    //             },
    //             error ->
    //             {
    //                 message = "ERROR: Couldn't find library - " + error.toString();
    //                 showDebugPageOrSendIntent();
    //             });
    //     queue.add(stringRequest);
    // }
    // PLEX_REMOVED_END

    // PLEX_REMOVED_START - Plex API: queries Plex server identity to get machineIdentifier
    // private void findServer()
    // {
    //     RequestQueue queue = Volley.newRequestQueue(this);
    //     String url = address + "/identity?" + tokenParameter + token;
    //     StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
    //             response ->
    //             {
    //                 InputStream targetStream = new ByteArrayInputStream(response.getBytes());
    //                 try
    //                 {
    //                     XmlPullParser parser = Xml.newPullParser();
    //                     parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
    //                     parser.setInput(targetStream, null);
    //                     parser.nextTag();
    //                     parser.require(XmlPullParser.START_TAG, null, "MediaContainer");
    //                     server = parser.getAttributeValue(null, "machineIdentifier");
    //                 }
    //                 catch (Exception e)
    //                 {
    //                     message = "WARNING 2: " + e;
    //                 }
    //                 searchLibrary();
    //             },
    //             error ->
    //             {
    //                 message = "WARNING: Couldn't find server - " + error.toString();
    //                 searchLibrary();
    //             });
    //     queue.add(stringRequest);
    // }
    // PLEX_REMOVED_END

    @Override
    protected void onStart()
    {
        super.onStart();

        // Don't re-initialize when returning from Zidoo player during Up Next flow
        // or when returning from UpNextActivity — onActivityResult handles these cases
        if (handlingPlaybackResult || waitingForUpNext) {
            Log.d("Play", "onStart: skipping re-init (handlingPlaybackResult=" + handlingPlaybackResult + " waitingForUpNext=" + waitingForUpNext + ")");
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
        directPath = inputString;
        textView1 = findViewById(R.id.textView1);
        textView2 = findViewById(R.id.textView2);
        playButton = findViewById(R.id.play_button);
        playButton.setOnClickListener(v ->
        {
            if(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("useZidooPlayer", true))
            {
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
            // PLEX_REMOVED_START - Plex API: extract viewOffset and mediaIndex from Plex intent extras
            // else
            // {
            //     try
            //     {
            //         viewOffset = originalIntent.getIntExtra("viewOffset", 0);
            //     }
            //     catch (Exception e)
            //     {
            //         // There is some strange error that is seen on newer levels of Plex such that if
            //         // I just try again it will work.  I've only seen this on the first extra we try
            //         // and read so we'll just ignore the first error and hope the second one succeeds
            //         viewOffset = originalIntent.getIntExtra("viewOffset", 0);
            //     }
            //     mediaIndex = originalIntent.getIntExtra("mediaIndex", -1);
            // }
            // PLEX_REMOVED_END
        }
        catch (Exception e)
        {
            message = "ERROR 1.0: " + e;
            showDebugPageOrSendIntent();
            return;
        }

        // PLEX_REMOVED_START - Plex API: extract token and server address from Plex URL, then query Plex server
        // try
        // {
        //     Pattern tokenPattern = Pattern.compile(tokenParameter + "([^&]+)");
        //     Matcher tokenMatcher = tokenPattern.matcher(inputString);
        //     if(tokenMatcher.find() && tokenMatcher.groupCount() >= 1)
        //     {
        //         token = tokenMatcher.group(1);
        //     }
        //
        //     Pattern addressPattern = Pattern.compile("^https://[^/]+");
        //     Matcher addressMatcher = addressPattern.matcher(inputString);
        //     if(addressMatcher.find())
        //     {
        //         address = addressMatcher.group();
        //
        //         if(address.contains("provider.plex.tv"))
        //         {
        //             message = "WARNING: Plex Free Stream - May Not Work";
        //             showDebugPageOrSendIntent();
        //             return;
        //         }
        //     }
        //     else
        //     {
        //         message = "ERROR: No address found";
        //         showDebugPageOrSendIntent();
        //         return;
        //     }
        // }
        // catch (Exception e)
        // {
        //     message = "ERROR 1.1: " + e;
        //     showDebugPageOrSendIntent();
        //     return;
        // }
        //
        // try
        // {
        //     Pattern partKeyPattern = Pattern.compile("/(library|services)/[^?]+");
        //     Matcher partKeyMatcher = partKeyPattern.matcher(inputString);
        //     if(partKeyMatcher.find())
        //     {
        //         partKey = partKeyMatcher.group();
        //
        //         if(partKey.contains("services"))
        //         {
        //             showDebugPageOrSendIntent();
        //             return;
        //         }
        //
        //         String[] partDirs = partKey.split("/");
        //         if(partDirs.length > 3)
        //         {
        //             partId = partDirs[3];
        //         }
        //     }
        //     else
        //     {
        //         message = "ERROR: No partKey found";
        //         showDebugPageOrSendIntent();
        //         return;
        //     }
        // }
        // catch (Exception e)
        // {
        //     message = "ERROR 1.2: " + e;
        //     showDebugPageOrSendIntent();
        //     return;
        // }
        //
        // findServer();
        // PLEX_REMOVED_END

        // Jellyfin intent handling (replaces Plex server communication)
        if(!zdmc)
        {
            String inputUrl = originalIntent.getDataString();
            String itemId = JellyfinApi.extractItemId(inputUrl);

            if(itemId != null)
            {
                // This is a Jellyfin streaming URL -- resolve via API
                jellyfinItemId = itemId;

                // Check for position from intent extras (Jellyfin client sends ms)
                int intentPosition = 0;
                try
                {
                    intentPosition = originalIntent.getIntExtra("position", 0);
                }
                catch(Exception e)
                {
                    // Ignore -- some intents may not have this extra
                }
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

                // Call Jellyfin API asynchronously
                JellyfinApi.getItem(serverUrl, accessToken, itemId, new JellyfinApi.Callback()
                {
                    @Override
                    public void onSuccess(String serverPath, long positionTicks, String title, long durationTicks, String itemSeriesId)
                    {
                        jellyfinApiPath = serverPath;
                        videoPath = serverPath;
                        videoTitle = title;
                        Play.this.durationTicks = durationTicks;
                        if (itemSeriesId != null) Play.this.seriesId = itemSeriesId;

                        // Use intent position if provided, otherwise convert API ticks to ms
                        if(intentPos > 0)
                        {
                            viewOffset = intentPos;
                        }
                        else if(positionTicks > 0)
                        {
                            viewOffset = (int) JellyfinApi.ticksToMs(positionTicks);
                        }

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

    protected void buildDefaultIntent(String path)
    {
        newIntent = new Intent(Intent.ACTION_VIEW);
        newIntent.setDataAndTypeAndNormalize(Uri.parse(path), "video/*" );
    }

    protected void buildZidooIntent(String path, int viewOffset)
    {
        // see https://github.com/Andy2244/jellyfin-androidtv-zidoo/blob/Zidoo-Edition/app/src/main/java/org/jellyfin/androidtv/ui/playback/ExternalPlayerActivity.java
        // NOTE: This code requires the new ZIDOO API to work.
        //       For Z9X and Z9X Pro lines that means firmware version 6.4.42+
        //       For Z9X 8K Line that means firmware version 1.1.42+
        newIntent = new Intent(Intent.ACTION_VIEW);

        // If it is a file, it will be played directly
        if(path.startsWith("/") && new File(path).exists())
        {
            newIntent.setDataAndTypeAndNormalize(Uri.fromFile(new File(path)), "video/*");
        }
        else
        {
            newIntent.setDataAndTypeAndNormalize(Uri.parse(path), "video/*");
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

        if(viewOffset > 0)
        {
            newIntent.putExtra("from_start", false);
            newIntent.putExtra("position", viewOffset);
        }
        else
        {
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

        progressPoller = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        // Self-rescheduling: 10s normally, 3s when < 60s remaining
        scheduleNextPoll(3000); // Initial 3s delay for Zidoo player startup
    }

    private void scheduleNextPoll(long delayMs) {
        if (progressPoller == null || progressPoller.isShutdown()) return;

        progressPoller.schedule(() -> {
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

                            // Detect Zidoo auto-advancing to next file — track it
                            if (nowPlayingPath != null && currentPlayingPath != null
                                    && !nowPlayingPath.equals(currentPlayingPath)) {
                                Log.d("Play", "Zidoo advanced to next file: " + nowPlayingPath);
                                currentPlayingPath = nowPlayingPath;
                                if (upNextTriggered) {
                                    Log.w("Play", "Auto-advance despite stop command — stop may have failed");
                                }
                            }

                            if (nowPlayingPath != null && currentPlayingPath == null) {
                                currentPlayingPath = nowPlayingPath;
                            }

                            if (video.has("duration")) {
                                lastKnownDurationMs = video.get("duration").getAsLong();
                            }

                            if (video.has("currentPosition")) {
                                long currentPositionMs = video.get("currentPosition").getAsLong();
                                lastKnownPositionMs = currentPositionMs;

                                long positionTicks = JellyfinApi.msToTicks(currentPositionMs);
                                // Report progress to Jellyfin
                                if (!jellyfinItemId.isEmpty() && !serverUrl.isEmpty() && !accessToken.isEmpty()) {
                                    JellyfinApi.reportPlaybackProgress(serverUrl, accessToken,
                                            jellyfinItemId, playSessionId, positionTicks, false,
                                            new JellyfinApi.SimpleCallback() {
                                                @Override public void onSuccess(String msg) { }
                                                @Override public void onError(String error) {
                                                    Log.w("Play", "Progress report failed: " + error);
                                                }
                                            });
                                }

                                // Adaptive polling: speed up when nearing end of episode
                                long remainingMs = lastKnownDurationMs - currentPositionMs;
                                Log.d("Play", "Poll: pos=" + currentPositionMs + " dur=" + lastKnownDurationMs + " remaining=" + remainingMs + "ms seriesId=" + (seriesId.isEmpty() ? "EMPTY" : seriesId.substring(0, 8)));
                                if (lastKnownDurationMs > 0 && remainingMs < 60000) {
                                    nextDelay = 3000; // 3s polls in final minute
                                }

                                // Stop player before end to prevent Zidoo auto-advancing.
                                // Multiple trigger points as safety net (30s, 15s, 5s).
                                if (!upNextTriggered && !seriesId.isEmpty()
                                        && lastKnownDurationMs > 60000 && currentPositionMs > 0
                                        && (remainingMs <= 30000 || remainingMs <= 15000 || remainingMs <= 5000)) {
                                    upNextTriggered = true;
                                    Log.d("Play", "Near end of episode (" + remainingMs + "ms remaining), stopping player for Up Next");
                                    // finishActivity(98) tells Android to finish the activity
                                    // we launched with startActivityForResult(_, 98)
                                    runOnUiThread(() -> {
                                        try {
                                            finishActivity(98);
                                            Log.d("Play", "finishActivity(98) sent");
                                        } catch (Exception e) {
                                            Log.w("Play", "finishActivity failed: " + e.getMessage());
                                        }
                                    });
                                }

                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.w("Play", "Zidoo poll failed: " + e.getMessage());
            }

            // Schedule next poll
            scheduleNextPoll(nextDelay);
        }, delayMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * Called when the player is stopped near end of episode (~10s before end).
     * Reports the episode as stopped/watched, then launches Up Next.
     */
    private void handleEpisodeCompleted() {
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

        JellyfinApi.getNextUpWithDetails(serverUrl, accessToken, seriesId,
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
                        upNextIntent.putExtra("accessToken", accessToken);
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
     */
    private String[][] getSubstitutionRules() {
        String[] prefIndex = {"", "_02", "_03", "_04", "_05", "_06", "_07", "_08", "_09", "_10"};
        List<String[]> rules = new ArrayList<>();
        android.content.SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        for (String s : prefIndex) {
            String pathToReplace = prefs.getString("path_to_replace" + s, "").trim();
            String replacedWith = prefs.getString("replaced_with" + s, "").trim();
            if (!pathToReplace.isEmpty() && !replacedWith.isEmpty()) {
                rules.add(new String[]{pathToReplace, replacedWith});
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

                // Resolve path and launch Zidoo player
                Log.d("Play", "Play Now: nextServerPath=" + nextServerPath);
                foundSubstitution = false;
                doSubstitution(nextServerPath);
                Log.d("Play", "Play Now: foundSubstitution=" + foundSubstitution + " directPath=" + directPath);
                // Capture resolved SMB path — directPath is an instance var that onStart() can overwrite
                final String resolvedSmbPath = directPath;
                if (foundSubstitution) {
                    // Get item details for duration tracking
                    JellyfinApi.getItem(serverUrl, accessToken, nextItemId, new JellyfinApi.Callback() {
                        @Override
                        public void onSuccess(String path, long posTicks, String title, long durTicks, String sid) {
                            durationTicks = durTicks;
                            videoTitle = title;
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

        // Report playback stopped to Jellyfin
        JellyfinApi.reportPlaybackStopped(serverUrl, accessToken, jellyfinItemId,
                playSessionId, finalPositionTicks, new JellyfinApi.SimpleCallback() {
            @Override
            public void onSuccess(String msg) {
                // Check 90% watched threshold
                if (JellyfinApi.isWatched(finalPositionTicks, durationTicks)) {
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

        return stringBuilder.toString();
    }
}
