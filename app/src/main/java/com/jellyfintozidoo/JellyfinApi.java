package com.jellyfintozidoo;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Jellyfin API client for fetching item metadata and testing server connections.
 * Provides static utility methods for URL parsing and tick conversion.
 */
public class JellyfinApi {

    private static final String TAG = "JellyfinApi";
    static final long TICKS_PER_MS = 10000;

    private static final Pattern ITEM_ID_PATTERN = Pattern.compile(
            "/Videos/([a-f0-9]{32}|[a-f0-9-]{36})/stream",
            Pattern.CASE_INSENSITIVE
    );

    private static volatile OkHttpClient client;
    private static volatile Handler mainHandler;

    private static OkHttpClient getClient() {
        if (client == null) {
            synchronized (JellyfinApi.class) {
                if (client == null) {
                    client = new OkHttpClient.Builder()
                            .connectTimeout(10, TimeUnit.SECONDS)
                            .readTimeout(10, TimeUnit.SECONDS)
                            .build();
                }
            }
        }
        return client;
    }

    private static Handler getMainHandler() {
        if (mainHandler == null) {
            synchronized (JellyfinApi.class) {
                if (mainHandler == null) {
                    mainHandler = new Handler(Looper.getMainLooper());
                }
            }
        }
        return mainHandler;
    }

    /**
     * Callback for getItem() responses.
     */
    public interface Callback {
        void onSuccess(String serverPath, long positionTicks, String title, long durationTicks, String seriesId);
        void onError(String error);
    }

    /**
     * Callback for getItemDetailed() — includes raw JSON body for MediaStreams extraction.
     */
    public interface DetailedCallback {
        void onSuccess(String serverPath, long positionTicks, String title, long durationTicks, String seriesId, String rawBody);
        void onError(String error);
    }

    /**
     * Callback for simple responses like testConnection().
     */
    public interface SimpleCallback {
        void onSuccess(String message);
        void onError(String error);
    }

    /**
     * Result object for parsed item responses. Package-private for testability.
     */
    static class ItemResult {
        final String path;
        final long positionTicks;
        final String title;
        final long durationTicks;
        final String seriesId;

        ItemResult(String path, long positionTicks, String title, long durationTicks, String seriesId) {
            this.path = path;
            this.positionTicks = positionTicks;
            this.title = title;
            this.durationTicks = durationTicks;
            this.seriesId = seriesId;
        }
    }

    /**
     * Result object for parsed NextUp detail responses. Package-private for testability.
     */
    static class NextUpDetailResult {
        final String itemId;
        final String seriesName;
        final String episodeName;
        final int seasonNumber;
        final int episodeNumber;
        final String seriesId;
        final String serverPath;

        NextUpDetailResult(String itemId, String seriesName, String episodeName,
                           int seasonNumber, int episodeNumber, String seriesId, String serverPath) {
            this.itemId = itemId;
            this.seriesName = seriesName;
            this.episodeName = episodeName;
            this.seasonNumber = seasonNumber;
            this.episodeNumber = episodeNumber;
            this.seriesId = seriesId;
            this.serverPath = serverPath;
        }
    }

    /**
     * Result object for parsed media segment responses. Package-private for testability.
     * Fields store timestamps in milliseconds. A value of -1 means the segment is not
     * present or not valid. The accessor names are part of the app's internal contract
     * (Play.java reads them) and are kept unchanged.
     */
    static class IntroSkipperResult {
        final long introStartMillis;
        final long introEndMillis;
        final long creditStartMillis;
        final long creditEndMillis;

        IntroSkipperResult(long introStartMillis, long introEndMillis,
                           long creditStartMillis, long creditEndMillis) {
            this.introStartMillis = introStartMillis;
            this.introEndMillis = introEndMillis;
            this.creditStartMillis = creditStartMillis;
            this.creditEndMillis = creditEndMillis;
        }

        long introStartMs() { return introStartMillis; }
        long introEndMs() { return introEndMillis; }
        long creditStartMs() { return creditStartMillis; }
        long creditEndMs() { return creditEndMillis; }
    }

    /** Shared empty result: every segment absent. */
    private static final IntroSkipperResult EMPTY_SEGMENTS =
            new IntroSkipperResult(-1, -1, -1, -1);

    /** MediaSegmentType value that Intro Skipper maps its Introduction analysis mode onto. */
    static final String SEGMENT_TYPE_INTRO = "Intro";

    /** MediaSegmentType value that Intro Skipper maps its Credits analysis mode onto. */
    static final String SEGMENT_TYPE_OUTRO = "Outro";

    /**
     * Parses a Jellyfin GET /MediaSegments/{itemId} response.
     * The body is a MediaSegmentDtoQueryResult:
     * {"Items":[{"Id","ItemId","Type","StartTicks","EndTicks"}],"TotalRecordCount":n}.
     * Type "Intro" is what this app calls the introduction, Type "Outro" is what it calls
     * the credits. StartTicks and EndTicks are .NET ticks, converted here to milliseconds.
     * When several segments share a type, the one with the lowest StartTicks wins.
     *
     * Never throws: a non JSON body, a JSON array, a null body or missing fields all yield
     * the empty result with every value at -1. Deliberately does no logging so that it stays
     * usable from plain JVM unit tests.
     * Package-private for testability.
     *
     * @param jsonBody The raw JSON response body
     * @return Parsed IntroSkipperResult, never null
     */
    static IntroSkipperResult parseMediaSegmentsResponse(String jsonBody) {
        try {
            if (jsonBody == null || jsonBody.trim().isEmpty()) {
                return EMPTY_SEGMENTS;
            }

            JsonElement rootElement = JsonParser.parseString(jsonBody);
            if (rootElement == null || !rootElement.isJsonObject()) {
                return EMPTY_SEGMENTS;
            }

            JsonObject root = rootElement.getAsJsonObject();
            if (!root.has("Items") || !root.get("Items").isJsonArray()) {
                return EMPTY_SEGMENTS;
            }

            JsonArray items = root.getAsJsonArray("Items");

            long introStartTicks = Long.MAX_VALUE, creditStartTicks = Long.MAX_VALUE;
            long introStartMs = -1, introEndMs = -1, creditStartMs = -1, creditEndMs = -1;

            for (int i = 0; i < items.size(); i++) {
                try {
                    JsonElement element = items.get(i);
                    if (element == null || !element.isJsonObject()) continue;
                    JsonObject segment = element.getAsJsonObject();

                    String type = optString(segment, "Type");
                    long startTicks = optLong(segment, "StartTicks", -1);
                    long endTicks = optLong(segment, "EndTicks", -1);

                    // An end of zero or less means the segment carries no usable range.
                    if (startTicks < 0 || endTicks <= 0) continue;

                    if (SEGMENT_TYPE_INTRO.equalsIgnoreCase(type)) {
                        if (startTicks < introStartTicks) {
                            introStartTicks = startTicks;
                            introStartMs = ticksToMs(startTicks);
                            introEndMs = ticksToMs(endTicks);
                        }
                    } else if (SEGMENT_TYPE_OUTRO.equalsIgnoreCase(type)) {
                        if (startTicks < creditStartTicks) {
                            creditStartTicks = startTicks;
                            creditStartMs = ticksToMs(startTicks);
                            creditEndMs = ticksToMs(endTicks);
                        }
                    }
                } catch (Exception perSegment) {
                    // One malformed element never costs the rest of the list.
                }
            }

            return new IntroSkipperResult(introStartMs, introEndMs, creditStartMs, creditEndMs);
        } catch (Exception e) {
            return EMPTY_SEGMENTS;
        }
    }

    /**
     * Historic entry point name, kept because Play.java calls it.
     * Delegates to parseMediaSegmentsResponse.
     * Package-private for testability.
     *
     * @param jsonBody The raw JSON response body
     * @return Parsed IntroSkipperResult, never null
     */
    static IntroSkipperResult parseIntroSkipperResponse(String jsonBody) {
        return parseMediaSegmentsResponse(jsonBody);
    }

    /**
     * Reads a string member, returning "" when it is absent, null or not a primitive.
     */
    private static String optString(JsonObject object, String member) {
        if (object == null || !object.has(member) || object.get(member).isJsonNull()) {
            return "";
        }
        JsonElement element = object.get(member);
        if (!element.isJsonPrimitive()) {
            return "";
        }
        return element.getAsString();
    }

    /**
     * Reads a long member, returning the fallback when it is absent, null or not a number.
     */
    private static long optLong(JsonObject object, String member, long fallback) {
        if (object == null || !object.has(member) || object.get(member).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(member).getAsLong();
        } catch (Exception e) {
            return fallback;
        }
    }

    /**
     * Maps a Jellyfin global stream index to a Zidoo 0-based audio track index.
     * Iterates MediaStreams, counting only Audio-type streams.
     * Package-private for testability.
     *
     * @param mediaStreams JsonArray of MediaStream objects from Jellyfin item response
     * @param jellyfinIndex The global stream index from Jellyfin
     * @return 0-based audio index for Zidoo, or -1 if not found or not an audio stream
     */
    static int jellyfinToZidooAudioIndex(JsonArray mediaStreams, int jellyfinIndex) {
        if (mediaStreams == null) return -1;
        int audioCount = 0;
        for (int i = 0; i < mediaStreams.size(); i++) {
            JsonElement element = mediaStreams.get(i);
            if (element == null || !element.isJsonObject()) continue;
            JsonObject stream = element.getAsJsonObject();
            String type = optString(stream, "Type");
            if ("Audio".equals(type)) {
                if (optLong(stream, "Index", Long.MIN_VALUE) == jellyfinIndex) {
                    return audioCount;
                }
                audioCount++;
            }
        }
        return -1;
    }

    /**
     * Maps a Jellyfin global stream index to a Zidoo 1-based subtitle track index.
     * Zidoo uses 1-based indexing for subtitles (0 = subtitles off).
     * Package-private for testability.
     *
     * @param mediaStreams JsonArray of MediaStream objects from Jellyfin item response
     * @param jellyfinIndex The global stream index from Jellyfin
     * @return 1-based subtitle index for Zidoo, or -1 if not found or not a subtitle stream
     */
    static int jellyfinToZidooSubtitleIndex(JsonArray mediaStreams, int jellyfinIndex) {
        if (mediaStreams == null) return -1;
        int subtitleCount = 0;
        for (int i = 0; i < mediaStreams.size(); i++) {
            JsonElement element = mediaStreams.get(i);
            if (element == null || !element.isJsonObject()) continue;
            JsonObject stream = element.getAsJsonObject();
            String type = optString(stream, "Type");
            if ("Subtitle".equals(type)) {
                if (optLong(stream, "Index", Long.MIN_VALUE) == jellyfinIndex) {
                    return subtitleCount + 1;
                }
                subtitleCount++;
            }
        }
        return -1;
    }

    /**
     * Extracts an integer query parameter from a URL string.
     * Uses simple string parsing to avoid Android dependency in unit tests.
     * Package-private for testability.
     *
     * @param url The full URL string
     * @param paramName The query parameter name to extract
     * @return The integer value, or -1 if not found or not parseable
     */
    static int parseUrlParam(String url, String paramName) {
        try {
            int queryStart = url.indexOf('?');
            if (queryStart < 0) return -1;
            String query = url.substring(queryStart + 1);
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    String key = pair.substring(0, eq);
                    if (paramName.equals(key)) {
                        return Integer.parseInt(pair.substring(eq + 1));
                    }
                }
            }
        } catch (Exception e) {
            // Malformed URL or parse error
        }
        return -1;
    }

    /**
     * Finds the first stream of a given type with IsDefault=true (or IsForced=true for subtitles).
     * Returns the Jellyfin global Index of that stream.
     * Package-private for testability.
     *
     * @param mediaStreams JsonArray of MediaStream objects
     * @param type The stream type to search for ("Audio" or "Subtitle")
     * @return The Jellyfin Index of the default/forced stream, or -1 if not found
     */
    static int findDefaultStreamIndex(JsonArray mediaStreams, String type) {
        if (mediaStreams == null) return -1;
        for (int i = 0; i < mediaStreams.size(); i++) {
            JsonElement element = mediaStreams.get(i);
            if (element == null || !element.isJsonObject()) continue;
            JsonObject stream = element.getAsJsonObject();
            String streamType = optString(stream, "Type");
            if (!streamType.equals(type)) continue;

            boolean isDefault = stream.has("IsDefault") && !stream.get("IsDefault").isJsonNull()
                    && stream.get("IsDefault").getAsBoolean();
            boolean isForced = stream.has("IsForced") && !stream.get("IsForced").isJsonNull()
                    && stream.get("IsForced").getAsBoolean();

            if (isDefault || isForced) {
                return (int) optLong(stream, "Index", -1);
            }
        }
        return -1;
    }

    private JellyfinApi() {
        // Prevent instantiation
    }

    /**
     * Extracts a Jellyfin item UUID from a streaming URL.
     * Handles UUIDs with and without hyphens, case-insensitive.
     *
     * @param url The streaming URL (e.g., "http://server:8096/Videos/{uuid}/stream")
     * @return The UUID string, or null if not a Jellyfin streaming URL
     */
    public static String extractItemId(String url) {
        if (url == null) return null;
        Matcher matcher = ITEM_ID_PATTERN.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Converts Jellyfin playback position ticks to milliseconds.
     * Jellyfin uses .NET ticks (1 tick = 100 nanoseconds = 0.0001 ms).
     *
     * @param ticks Playback position in ticks
     * @return Position in milliseconds
     */
    public static long ticksToMs(long ticks) {
        return ticks / TICKS_PER_MS;
    }

    /**
     * Converts milliseconds to Jellyfin playback position ticks.
     *
     * @param ms Position in milliseconds
     * @return Position in ticks
     */
    public static long msToTicks(long ms) {
        return ms * TICKS_PER_MS;
    }

    /**
     * Builds the JSON body for a playback start report.
     * Package-private for testability.
     */
    static JsonObject buildPlaybackStartBody(String itemId, String playSessionId) {
        JsonObject body = new JsonObject();
        body.addProperty("ItemId", itemId);
        body.addProperty("PlaySessionId", playSessionId);
        body.addProperty("CanSeek", true);
        body.addProperty("PlayMethod", "DirectPlay");
        body.addProperty("PositionTicks", 0L);
        return body;
    }

    /**
     * Builds the JSON body for a playback progress report.
     * Package-private for testability.
     */
    static JsonObject buildPlaybackProgressBody(String itemId, String playSessionId,
                                                 long positionTicks, boolean isPaused) {
        JsonObject body = new JsonObject();
        body.addProperty("ItemId", itemId);
        body.addProperty("PlaySessionId", playSessionId);
        body.addProperty("PositionTicks", positionTicks);
        body.addProperty("IsPaused", isPaused);
        body.addProperty("CanSeek", true);
        return body;
    }

    /**
     * Builds the JSON body for a playback stopped report.
     * Package-private for testability.
     */
    static JsonObject buildPlaybackStoppedBody(String itemId, String playSessionId,
                                                long positionTicks) {
        JsonObject body = new JsonObject();
        body.addProperty("ItemId", itemId);
        body.addProperty("PlaySessionId", playSessionId);
        body.addProperty("PositionTicks", positionTicks);
        return body;
    }

    /**
     * Determines if playback has reached the watched threshold (90% of duration).
     *
     * @param positionTicks Current position in ticks
     * @param durationTicks Total duration in ticks
     * @return true if position exceeds 90% of duration
     */
    public static boolean isWatched(long positionTicks, long durationTicks) {
        return durationTicks > 0 && positionTicks > (long) (durationTicks * 0.9);
    }

    /**
     * Normalizes a configured server URL into a base for path concatenation:
     * strips one trailing slash and surrounding whitespace, and turns null into "".
     * The single place this class handles the trailing slash.
     *
     * @param serverUrl The server URL as configured by the user
     * @return The URL with no trailing slash, never null
     */
    private static String baseUrl(String serverUrl) {
        if (serverUrl == null) {
            return "";
        }
        String trimmed = serverUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    /** Default client name reported to the server. */
    private static final String DEFAULT_CLIENT_NAME = "JellyfinToZidoo";
    /** Default device name reported to the server. */
    private static final String DEFAULT_DEVICE_NAME = "Zidoo";
    /** Default device id reported to the server. */
    private static final String DEFAULT_DEVICE_ID = "jellyfintozidoo";
    /** Default app version reported to the server. */
    private static final String DEFAULT_APP_VERSION = "1.0.0";

    private static volatile String clientName = DEFAULT_CLIENT_NAME;
    private static volatile String deviceName = DEFAULT_DEVICE_NAME;
    private static volatile String deviceId = DEFAULT_DEVICE_ID;
    private static volatile String appVersion = DEFAULT_APP_VERSION;

    /**
     * Sets the client identity sent in the MediaBrowser Authorization header.
     * Call once at app startup with the real build version and a per device id, so the
     * server records a stable device row instead of every install sharing one identity.
     * Any argument that is null or blank leaves the current value in place.
     *
     * @param deviceName  Human readable device name, for example the Zidoo model
     * @param deviceId    Stable per device identifier
     * @param appVersion  App version string, for example BuildConfig.VERSION_NAME
     */
    public static void setClientIdentity(String deviceName, String deviceId, String appVersion) {
        if (deviceName != null && !deviceName.trim().isEmpty()) {
            JellyfinApi.deviceName = deviceName.trim();
        }
        if (deviceId != null && !deviceId.trim().isEmpty()) {
            JellyfinApi.deviceId = deviceId.trim();
        }
        if (appVersion != null && !appVersion.trim().isEmpty()) {
            JellyfinApi.appVersion = appVersion.trim();
        }
    }

    /**
     * Restores the compiled in client identity defaults. Package-private, for tests.
     */
    static void resetClientIdentity() {
        clientName = DEFAULT_CLIENT_NAME;
        deviceName = DEFAULT_DEVICE_NAME;
        deviceId = DEFAULT_DEVICE_ID;
        appVersion = DEFAULT_APP_VERSION;
    }

    /**
     * Builds the client identity portion of the MediaBrowser Authorization header,
     * with no Token. Used on its own by authenticate(), where no token exists yet.
     * Package-private for testability.
     *
     * @return The header value without a Token field
     */
    static String buildClientIdentityHeader() {
        return "MediaBrowser Client=\"" + clientName + "\", Device=\"" + deviceName + "\", "
                + "DeviceId=\"" + deviceId + "\", Version=\"" + appVersion + "\"";
    }

    /**
     * Builds the Jellyfin authorization header value.
     * Since Jellyfin 12.0 every call sends the full MediaBrowser header with client identity,
     * rather than relying on the server backfilling the missing fields from the device row.
     * Kept as a separate name because callers and tests already use it.
     *
     * @param apiKey The API key or access token
     * @return The formatted authorization header value
     */
    public static String buildAuthHeader(String apiKey) {
        return buildFullAuthHeader(apiKey);
    }

    /**
     * Parses a Jellyfin item JSON response into an ItemResult.
     * Package-private for testability.
     *
     * @param jsonBody The raw JSON response body
     * @return Parsed ItemResult
     * @throws Exception if JSON parsing fails
     */
    static ItemResult parseItemResponse(String jsonBody) throws Exception {
        JsonObject root = JsonParser.parseString(jsonBody).getAsJsonObject();

        // Extract title
        String title = "";
        if (root.has("Name") && !root.get("Name").isJsonNull()) {
            title = root.get("Name").getAsString();
        }

        // Extract playback position ticks
        long positionTicks = 0;
        if (root.has("UserData") && !root.get("UserData").isJsonNull()) {
            JsonObject userData = root.getAsJsonObject("UserData");
            if (userData.has("PlaybackPositionTicks") && !userData.get("PlaybackPositionTicks").isJsonNull()) {
                positionTicks = userData.get("PlaybackPositionTicks").getAsLong();
            }
        }

        // Extract path: try root Path first, fall back to MediaSources[0].Path
        String path = null;
        if (root.has("Path") && !root.get("Path").isJsonNull()) {
            path = root.get("Path").getAsString();
        }
        if (path == null || path.isEmpty()) {
            if (root.has("MediaSources") && root.get("MediaSources").isJsonArray()) {
                JsonArray mediaSources = root.getAsJsonArray("MediaSources");
                if (mediaSources.size() > 0) {
                    JsonObject firstSource = mediaSources.get(0).getAsJsonObject();
                    if (firstSource.has("Path") && !firstSource.get("Path").isJsonNull()) {
                        path = firstSource.get("Path").getAsString();
                    }
                }
            }
        }

        if (path == null) {
            path = "";
        }

        // Extract duration (RunTimeTicks) from root
        long durationTicks = 0;
        if (root.has("RunTimeTicks") && !root.get("RunTimeTicks").isJsonNull()) {
            durationTicks = root.get("RunTimeTicks").getAsLong();
        }

        // Extract SeriesId (present for episodes)
        String seriesId = null;
        if (root.has("SeriesId") && !root.get("SeriesId").isJsonNull()) {
            seriesId = root.get("SeriesId").getAsString();
        }

        return new ItemResult(path, positionTicks, title, durationTicks, seriesId);
    }

    /**
     * The Zidoo player reports the file it is playing either as the launch URI (smb://...)
     * or as its local SMB mount path once the share is mounted; both must compare and map as the same file.
     */
    static String normalizeZidooPath(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }

        // URL-decode the path (handles %20 etc.)
        String decoded;
        try {
            decoded = URLDecoder.decode(path, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            decoded = path;
        }

        // Mounted form: /data/system/smb/<host>#<share path> -> smb://<host>/<share path>
        String mountPrefix = "/data/system/smb/";
        if (decoded.startsWith(mountPrefix)) {
            String remainder = decoded.substring(mountPrefix.length());
            int hashIndex = remainder.indexOf('#');
            if (hashIndex >= 0) {
                String host = remainder.substring(0, hashIndex);
                String rest = remainder.substring(hashIndex + 1);
                return "smb://" + host + "/" + rest;
            }
            return "smb://" + remainder;
        }

        // Strip SMB credentials: smb://user:pass@host -> smb://host
        return decoded.replaceFirst("smb://[^@]+@", "smb://");
    }

    /**
     * Reverses path substitution: converts a Zidoo SMB path back to the server-side path.
     * Package-private for testability.
     *
     * @param zidooPath The path as seen by Zidoo (e.g., "smb://user:pass@host/share/media/file.mkv")
     * @param rules     Array of rule pairs: each entry is {pathToReplace, replacedWith} from settings
     *                  Forward substitution does: pathToReplace -> replacedWith
     *                  Reverse does: replacedWith -> pathToReplace
     * @return The server-side path, or null if no rule matches or input is null/empty
     */
    static String reverseSubstitution(String zidooPath, String[][] rules) {
        String stripped = normalizeZidooPath(zidooPath);
        if (stripped == null) {
            return null;
        }

        // Try each rule in order — first match wins
        for (String[] rule : rules) {
            if (rule.length < 2) continue;
            String pathToReplace = rule[0]; // server-side prefix
            String replacedWith = rule[1];  // Zidoo-side prefix

            if (!replacedWith.isEmpty() && stripped.startsWith(replacedWith)) {
                return pathToReplace + stripped.substring(replacedWith.length());
            }
        }

        return null;
    }

    /**
     * True when both paths normalize to the same non-null Zidoo path, regardless of
     * whether either is spelled as the launch URI or as the local SMB mount path.
     */
    static boolean isSameZidooFile(String a, String b) {
        String normalizedA = normalizeZidooPath(a);
        String normalizedB = normalizeZidooPath(b);
        return normalizedA != null && normalizedA.equals(normalizedB);
    }

    /**
     * True only when both paths normalize to non-null Zidoo paths that differ. An empty or
     * missing path on either side (the player opening or closing a file) is never a file change.
     */
    static boolean isZidooFileChange(String nowPath, String currentPath) {
        String normalizedNow = normalizeZidooPath(nowPath);
        String normalizedCurrent = normalizeZidooPath(currentPath);
        return normalizedNow != null && normalizedCurrent != null
                && !normalizedNow.equals(normalizedCurrent);
    }

    /**
     * Extracts the filename stem (without extension) from a server-side path.
     * Used as the search term for Jellyfin item lookup by path.
     * Package-private for testability.
     *
     * @param serverPath The server-side file path
     * @return The filename without extension, or null if input is null/empty
     */
    static String extractSearchName(String serverPath) {
        if (serverPath == null || serverPath.isEmpty()) {
            return null;
        }

        // Get the last path segment
        int lastSlash = serverPath.lastIndexOf('/');
        String filename = (lastSlash >= 0) ? serverPath.substring(lastSlash + 1) : serverPath;

        // Remove extension
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0) {
            filename = filename.substring(0, lastDot);
        }

        return filename;
    }

    /**
     * Parses a Jellyfin NextUp response (from /Shows/NextUp) into detailed episode metadata.
     * Package-private for testability.
     *
     * @param jsonBody The raw JSON response body
     * @return Parsed NextUpDetailResult, or null if Items array is empty
     * @throws Exception if JSON parsing fails
     */
    static NextUpDetailResult parseNextUpDetailResponse(String jsonBody) throws Exception {
        JsonObject root = JsonParser.parseString(jsonBody).getAsJsonObject();

        if (!root.has("Items") || !root.get("Items").isJsonArray()) {
            return null;
        }

        JsonArray items = root.getAsJsonArray("Items");
        if (items.size() == 0) {
            return null;
        }

        JsonObject item = items.get(0).getAsJsonObject();

        String itemId = item.has("Id") && !item.get("Id").isJsonNull()
                ? item.get("Id").getAsString() : "";

        String seriesName = item.has("SeriesName") && !item.get("SeriesName").isJsonNull()
                ? item.get("SeriesName").getAsString() : "";

        String episodeName = item.has("Name") && !item.get("Name").isJsonNull()
                ? item.get("Name").getAsString() : "";

        int seasonNumber = item.has("ParentIndexNumber") && !item.get("ParentIndexNumber").isJsonNull()
                ? item.get("ParentIndexNumber").getAsInt() : 0;

        int episodeNumber = item.has("IndexNumber") && !item.get("IndexNumber").isJsonNull()
                ? item.get("IndexNumber").getAsInt() : 0;

        String seriesId = item.has("SeriesId") && !item.get("SeriesId").isJsonNull()
                ? item.get("SeriesId").getAsString() : null;

        // Extract path: try root Path first, fall back to MediaSources[0].Path
        String serverPath = null;
        if (item.has("Path") && !item.get("Path").isJsonNull()) {
            serverPath = item.get("Path").getAsString();
        }
        if (serverPath == null || serverPath.isEmpty()) {
            if (item.has("MediaSources") && item.get("MediaSources").isJsonArray()) {
                JsonArray mediaSources = item.getAsJsonArray("MediaSources");
                if (mediaSources.size() > 0) {
                    JsonObject firstSource = mediaSources.get(0).getAsJsonObject();
                    if (firstSource.has("Path") && !firstSource.get("Path").isJsonNull()) {
                        serverPath = firstSource.get("Path").getAsString();
                    }
                }
            }
        }
        if (serverPath == null) {
            serverPath = "";
        }

        return new NextUpDetailResult(itemId, seriesName, episodeName,
                seasonNumber, episodeNumber, seriesId, serverPath);
    }

    /**
     * Parses a Jellyfin search response to find an item matching the expected path.
     * Checks both root Path and MediaSources[0].Path for each item.
     * Package-private for testability.
     *
     * @param jsonBody     The raw JSON response body
     * @param expectedPath The server-side path to match
     * @return The item ID if an exact path match is found, null otherwise
     * @throws Exception if JSON parsing fails
     */
    static String parseSearchByPathResponse(String jsonBody, String expectedPath) throws Exception {
        JsonObject root = JsonParser.parseString(jsonBody).getAsJsonObject();

        if (!root.has("Items") || !root.get("Items").isJsonArray()) {
            return null;
        }

        JsonArray items = root.getAsJsonArray("Items");
        for (int i = 0; i < items.size(); i++) {
            JsonObject item = items.get(i).getAsJsonObject();

            // Check root Path
            String path = null;
            if (item.has("Path") && !item.get("Path").isJsonNull()) {
                path = item.get("Path").getAsString();
            }

            if (expectedPath.equals(path)) {
                return item.has("Id") ? item.get("Id").getAsString() : null;
            }

            // Check MediaSources[0].Path as fallback
            if (item.has("MediaSources") && item.get("MediaSources").isJsonArray()) {
                JsonArray mediaSources = item.getAsJsonArray("MediaSources");
                if (mediaSources.size() > 0) {
                    JsonObject firstSource = mediaSources.get(0).getAsJsonObject();
                    if (firstSource.has("Path") && !firstSource.get("Path").isJsonNull()) {
                        String msPath = firstSource.get("Path").getAsString();
                        if (expectedPath.equals(msPath)) {
                            return item.has("Id") ? item.get("Id").getAsString() : null;
                        }
                    }
                }
            }
        }

        return null;
    }

    /**
     * Fetches item metadata from a Jellyfin server asynchronously.
     * Callback runs on the main (UI) thread.
     *
     * @param serverUrl Base server URL (e.g., "http://192.168.1.10:8096")
     * @param apiKey    API key or access token
     * @param itemId    Item UUID
     * @param callback  Callback for success/error
     */
    public static void getItem(String serverUrl, String apiKey, String itemId, Callback callback) {
        // Strip trailing slash
        String baseUrl = baseUrl(serverUrl);
        String url = baseUrl + "/Items/" + itemId + "?Fields=Path,MediaSources";

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", buildAuthHeader(apiKey))
                .build();

        getClient().newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "getItem failed", e);
                getMainHandler().post(() -> callback.onError("Network error: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        final String msg = "HTTP error " + response.code();
                        getMainHandler().post(() -> callback.onError(msg));
                        return;
                    }

                    String body = response.body() != null ? response.body().string() : "";
                    ItemResult result = parseItemResponse(body);
                    getMainHandler().post(() -> callback.onSuccess(result.path, result.positionTicks, result.title, result.durationTicks, result.seriesId));
                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse item response", e);
                    final String msg = "Parse error: " + e.getMessage();
                    getMainHandler().post(() -> callback.onError(msg));
                } finally {
                    response.close();
                }
            }
        });
    }

    /**
     * Fetches a Jellyfin item with full details including raw JSON body.
     * Same as getItem() but passes raw response body for MediaStreams extraction.
     * Callback runs on the main (UI) thread.
     */
    public static void getItemDetailed(String serverUrl, String apiKey, String itemId, DetailedCallback callback) {
        String baseUrl = baseUrl(serverUrl);
        String url = baseUrl + "/Items/" + itemId + "?Fields=Path,MediaSources";

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", buildAuthHeader(apiKey))
                .build();

        getClient().newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "getItemDetailed failed", e);
                getMainHandler().post(() -> callback.onError("Network error: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        final String msg = "HTTP error " + response.code();
                        getMainHandler().post(() -> callback.onError(msg));
                        return;
                    }

                    String body = response.body() != null ? response.body().string() : "";
                    ItemResult result = parseItemResponse(body);
                    getMainHandler().post(() -> callback.onSuccess(result.path, result.positionTicks,
                            result.title, result.durationTicks, result.seriesId, body));
                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse item response", e);
                    final String msg = "Parse error: " + e.getMessage();
                    getMainHandler().post(() -> callback.onError(msg));
                } finally {
                    response.close();
                }
            }
        });
    }

    /**
     * Callback for authenticate() responses.
     */
    public interface AuthCallback {
        void onSuccess(String accessToken, String userId, String serverName);
        void onError(String error);
    }

    /**
     * Authenticates with a Jellyfin server using username/password.
     * Returns an access token and user ID via callback on the main thread.
     *
     * @param serverUrl Base server URL
     * @param username  Jellyfin username
     * @param password  Jellyfin password
     * @param callback  Callback for success/error
     */
    public static void authenticate(String serverUrl, String username, String password, AuthCallback callback) {
        String baseUrl = baseUrl(serverUrl);
        String url = baseUrl + "/Users/AuthenticateByName";

        JsonObject body = new JsonObject();
        body.addProperty("Username", username);
        body.addProperty("Pw", password);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", buildClientIdentityHeader())
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body.toString(), MediaType.get("application/json")))
                .build();

        getClient().newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "authenticate failed", e);
                getMainHandler().post(() -> callback.onError("Network error: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        final String msg = response.code() == 401 ? "Invalid username or password" : "HTTP error " + response.code();
                        getMainHandler().post(() -> callback.onError(msg));
                        return;
                    }

                    String responseBody = response.body() != null ? response.body().string() : "";
                    JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
                    String accessToken = root.get("AccessToken").getAsString();
                    JsonObject user = root.getAsJsonObject("User");
                    String userId = user.get("Id").getAsString();
                    String serverName = "";
                    if (root.has("ServerId") && !root.get("ServerId").isJsonNull()) {
                        serverName = root.get("ServerId").getAsString();
                    }
                    final String sn = serverName;
                    getMainHandler().post(() -> callback.onSuccess(accessToken, userId, sn));
                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse auth response", e);
                    final String msg = "Parse error: " + e.getMessage();
                    getMainHandler().post(() -> callback.onError(msg));
                } finally {
                    response.close();
                }
            }
        });
    }

    /**
     * Builds the full MediaBrowser authorization header with client info and token.
     * The single builder behind every authenticated request in this class.
     *
     * @param apiKey The access token
     * @return The formatted full authorization header value
     */
    static String buildFullAuthHeader(String apiKey) {
        return buildClientIdentityHeader() + ", Token=\"" + apiKey + "\"";
    }

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json");

    /**
     * Reports playback start to Jellyfin server.
     * POST /Sessions/Playing
     */
    public static void reportPlaybackStart(String serverUrl, String apiKey, String itemId,
                                            String playSessionId, SimpleCallback callback) {
        String baseUrl = baseUrl(serverUrl);
        String url = baseUrl + "/Sessions/Playing";

        JsonObject body = buildPlaybackStartBody(itemId, playSessionId);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", buildFullAuthHeader(apiKey))
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body.toString(), JSON_MEDIA_TYPE))
                .build();

        enqueueSimpleRequest(request, "reportPlaybackStart", callback);
    }

    /**
     * Reports playback progress to Jellyfin server.
     * POST /Sessions/Playing/Progress
     */
    public static void reportPlaybackProgress(String serverUrl, String apiKey, String itemId,
                                               String playSessionId, long positionTicks,
                                               boolean isPaused, SimpleCallback callback) {
        String baseUrl = baseUrl(serverUrl);
        String url = baseUrl + "/Sessions/Playing/Progress";

        JsonObject body = buildPlaybackProgressBody(itemId, playSessionId, positionTicks, isPaused);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", buildFullAuthHeader(apiKey))
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body.toString(), JSON_MEDIA_TYPE))
                .build();

        enqueueSimpleRequest(request, "reportPlaybackProgress", callback);
    }

    /**
     * Reports playback stopped to Jellyfin server.
     * POST /Sessions/Playing/Stopped
     */
    public static void reportPlaybackStopped(String serverUrl, String apiKey, String itemId,
                                              String playSessionId, long positionTicks,
                                              SimpleCallback callback) {
        String baseUrl = baseUrl(serverUrl);
        String url = baseUrl + "/Sessions/Playing/Stopped";

        JsonObject body = buildPlaybackStoppedBody(itemId, playSessionId, positionTicks);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", buildFullAuthHeader(apiKey))
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body.toString(), JSON_MEDIA_TYPE))
                .build();

        enqueueSimpleRequest(request, "reportPlaybackStopped", callback);
    }

    /**
     * Builds the mark as watched URL for Jellyfin 12.0.
     * Package-private so the URL shape is unit testable without a network call.
     *
     * @param serverUrl Base server URL
     * @param itemId    Item UUID
     * @return POST target for marking the item played
     */
    static String buildMarkAsWatchedUrl(String serverUrl, String itemId) {
        return baseUrl(serverUrl) + "/UserPlayedItems/" + itemId;
    }

    /**
     * Marks an item as watched (played) on the Jellyfin server.
     * POST /UserPlayedItems/{itemId}; the server resolves the user from the token.
     * The 10.x route POST /Users/{userId}/PlayedItems/{itemId} still answers on 12.0 but is
     * marked obsolete and hidden from the OpenAPI document, so it is no longer used.
     *
     * @param userId Unused since the move to /UserPlayedItems; kept so callers do not change.
     */
    public static void markAsWatched(String serverUrl, String apiKey, String userId,
                                      String itemId, SimpleCallback callback) {
        String url = buildMarkAsWatchedUrl(serverUrl, itemId);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", buildFullAuthHeader(apiKey))
                .post(RequestBody.create("", JSON_MEDIA_TYPE))
                .build();

        enqueueSimpleRequest(request, "markAsWatched", callback);
    }

    /**
     * Callback for getNextUp() — returns the next episode's item ID, or null if none.
     */
    public interface NextUpCallback {
        void onResult(String nextItemId);
    }

    /**
     * Queries Jellyfin for the next unwatched episode in a series.
     * Jellyfin equivalent of PlexToZidoo's searchFiles().
     */
    public static void getNextUp(String serverUrl, String apiKey, String seriesId,
                                  NextUpCallback callback) {
        String baseUrl = baseUrl(serverUrl);
        String url = baseUrl + "/Shows/NextUp?seriesId=" + seriesId + "&limit=1";

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", buildFullAuthHeader(apiKey))
                .build();

        getClient().newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.w(TAG, "getNextUp failed: " + e.getMessage());
                getMainHandler().post(() -> callback.onResult(null));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful() || response.body() == null) {
                        getMainHandler().post(() -> callback.onResult(null));
                        return;
                    }
                    String body = response.body().string();
                    JsonObject root = JsonParser.parseString(body).getAsJsonObject();
                    if (root.has("Items") && root.get("Items").isJsonArray()) {
                        JsonArray items = root.getAsJsonArray("Items");
                        if (items.size() > 0) {
                            JsonObject nextItem = items.get(0).getAsJsonObject();
                            String nextId = nextItem.has("Id") ? nextItem.get("Id").getAsString() : null;
                            getMainHandler().post(() -> callback.onResult(nextId));
                            return;
                        }
                    }
                    getMainHandler().post(() -> callback.onResult(null));
                } catch (Exception e) {
                    Log.w(TAG, "getNextUp parse error: " + e.getMessage());
                    getMainHandler().post(() -> callback.onResult(null));
                } finally {
                    response.close();
                }
            }
        });
    }

    /** How much of an error body reaches the log. Request headers are never logged. */
    static final int ERROR_BODY_LOG_LIMIT = 200;

    /** Delay before the single retry of a report that failed with a network error. */
    static final long RETRY_DELAY_MS = 1000;

    /**
     * Says whether an operation gets one retry after an IOException.
     * Only the two writes whose loss the user would notice qualify: the stop report, which
     * carries the resume position, and the mark as watched call. HTTP errors are never
     * retried, because the server did answer.
     * Package-private for testability.
     *
     * @param operationName The operation label passed to enqueueSimpleRequest
     * @return true when the operation should be retried once on a network failure
     */
    static boolean shouldRetryOnNetworkFailure(String operationName) {
        return "reportPlaybackStopped".equals(operationName) || "markAsWatched".equals(operationName);
    }

    /**
     * Trims a response body down to what may go into a log line.
     * Package-private for testability.
     *
     * @param body The response body, possibly null
     * @return At most ERROR_BODY_LOG_LIMIT characters, never null
     */
    static String truncateForLog(String body) {
        if (body == null) return "";
        String single = body.replace('\n', ' ').replace('\r', ' ');
        return single.length() <= ERROR_BODY_LOG_LIMIT
                ? single
                : single.substring(0, ERROR_BODY_LOG_LIMIT);
    }

    /**
     * Enqueues an OkHttp request with simple success/error callback on the main thread.
     * Shared by all reporting methods.
     */
    private static void enqueueSimpleRequest(Request request, String operationName,
                                              SimpleCallback callback) {
        enqueueSimpleRequest(request, operationName, callback,
                shouldRetryOnNetworkFailure(operationName) ? 1 : 0);
    }

    /**
     * Enqueues an OkHttp request, retrying at most retriesLeft times on a network failure.
     * A non 2xx answer is reported straight back with the status and the head of the body,
     * because the server has spoken and repeating the call will not change its mind.
     */
    private static void enqueueSimpleRequest(Request request, String operationName,
                                              SimpleCallback callback, int retriesLeft) {
        getClient().newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (retriesLeft > 0) {
                    Log.w(TAG, operationName + " network failure, retrying once in "
                            + RETRY_DELAY_MS + "ms: " + e.getMessage());
                    getMainHandler().postDelayed(
                            () -> enqueueSimpleRequest(request, operationName, callback, retriesLeft - 1),
                            RETRY_DELAY_MS);
                    return;
                }
                Log.e(TAG, operationName + " failed", e);
                getMainHandler().post(() -> callback.onError("Network error: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        String errorBody = "";
                        try {
                            if (response.body() != null) {
                                errorBody = response.body().string();
                            }
                        } catch (Exception readFailure) {
                            errorBody = "";
                        }
                        Log.w(TAG, operationName + " HTTP " + response.code()
                                + " body: " + truncateForLog(errorBody));
                        final String msg = "HTTP error " + response.code();
                        getMainHandler().post(() -> callback.onError(msg));
                        return;
                    }
                    getMainHandler().post(() -> callback.onSuccess(operationName + " successful"));
                } finally {
                    response.close();
                }
            }
        });
    }

    /**
     * Tests connectivity to a Jellyfin server by querying /System/Info.
     * Callback runs on the main (UI) thread.
     *
     * @param serverUrl Base server URL
     * @param apiKey    API key or access token
     * @param callback  Callback for success/error
     */
    public static void testConnection(String serverUrl, String apiKey, SimpleCallback callback) {
        String baseUrl = baseUrl(serverUrl);
        String url = baseUrl + "/System/Info";

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", buildAuthHeader(apiKey))
                .build();

        getClient().newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "testConnection failed", e);
                getMainHandler().post(() -> callback.onError("Network error: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        final String msg = "HTTP error " + response.code();
                        getMainHandler().post(() -> callback.onError(msg));
                        return;
                    }

                    String body = response.body() != null ? response.body().string() : "";
                    JsonObject root = JsonParser.parseString(body).getAsJsonObject();
                    String serverName = "Jellyfin Server";
                    if (root.has("ServerName") && !root.get("ServerName").isJsonNull()) {
                        serverName = root.get("ServerName").getAsString();
                    }
                    final String name = serverName;
                    getMainHandler().post(() -> callback.onSuccess("Connected to " + name));
                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse system info", e);
                    final String msg = "Parse error: " + e.getMessage();
                    getMainHandler().post(() -> callback.onError(msg));
                } finally {
                    response.close();
                }
            }
        });
    }

    /**
     * Callback for getNextUpWithDetails() — returns full episode metadata for Up Next screen.
     */
    public interface NextUpDetailCallback {
        void onResult(String nextItemId, String seriesName, String episodeName,
                      int seasonNumber, int episodeNumber, String seriesId, String serverPath);
        void onNoNextEpisode();
    }

    /**
     * Queries Jellyfin for the next unwatched episode with full detail metadata.
     * Uses /Shows/NextUp with Fields=Path,MediaSources for server path resolution.
     * Callback runs on the main (UI) thread.
     *
     * @param serverUrl Base server URL
     * @param apiKey    Access token
     * @param seriesId  Series UUID
     * @param callback  Callback for result/no-next
     */
    public static void getNextUpWithDetails(String serverUrl, String apiKey, String userId,
                                             String seriesId, NextUpDetailCallback callback) {
        String baseUrl = baseUrl(serverUrl);
        String url = baseUrl + "/Shows/NextUp?seriesId=" + seriesId + "&userId=" + userId + "&limit=1&Fields=Path,MediaSources";

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", buildFullAuthHeader(apiKey))
                .build();

        getClient().newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.w(TAG, "getNextUpWithDetails failed: " + e.getMessage());
                getMainHandler().post(() -> callback.onNoNextEpisode());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful() || response.body() == null) {
                        Log.w(TAG, "getNextUpWithDetails: response code=" + response.code());
                        getMainHandler().post(() -> callback.onNoNextEpisode());
                        return;
                    }
                    String body = response.body().string();
                    NextUpDetailResult result = parseNextUpDetailResponse(body);
                    if (result == null) {
                        Log.d(TAG, "getNextUpWithDetails: no next episode in response");
                        getMainHandler().post(() -> callback.onNoNextEpisode());
                        return;
                    }
                    getMainHandler().post(() -> callback.onResult(
                            result.itemId, result.seriesName, result.episodeName,
                            result.seasonNumber, result.episodeNumber,
                            result.seriesId, result.serverPath));
                } catch (Exception e) {
                    Log.w(TAG, "getNextUpWithDetails parse error: " + e.getMessage());
                    getMainHandler().post(() -> callback.onNoNextEpisode());
                } finally {
                    response.close();
                }
            }
        });
    }

    /**
     * Callback for searchItemByPath() — returns item ID if found.
     */
    public interface SearchByPathCallback {
        void onFound(String itemId);
        void onNotFound(String error);
    }

    /**
     * How many search hits to pull before matching on the exact path.
     * Jellyfin 12.0 routes any searchTerm query through the new search providers, which ask
     * for more candidates than the limit and then rescore them, so a common episode title can
     * push the right file well down the list. Matching stays exact on Path; a wider window
     * only makes it more likely the right row is in the window at all.
     */
    static final int SEARCH_BY_PATH_LIMIT = 50;

    /**
     * Searches Jellyfin for an episode by its server-side file path.
     * Extracts filename as search term, queries /Items, and matches exact path.
     * Callback runs on the main (UI) thread.
     *
     * @param serverUrl  Base server URL
     * @param apiKey     Access token
     * @param serverPath The server-side file path to search for
     * @param callback   Callback for found/not-found
     */
    public static void searchItemByPath(String serverUrl, String apiKey, String serverPath,
                                         SearchByPathCallback callback) {
        String searchName = extractSearchName(serverPath);
        if (searchName == null || searchName.isEmpty()) {
            getMainHandler().post(() -> callback.onNotFound("Could not extract search name from path"));
            return;
        }

        String baseUrl = baseUrl(serverUrl);
        String encodedName;
        try {
            encodedName = URLEncoder.encode(searchName, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            encodedName = searchName;
        }
        String url = baseUrl + "/Items?searchTerm=" + encodedName
                + "&IncludeItemTypes=Episode&Fields=Path,MediaSources&Recursive=true"
                + "&Limit=" + SEARCH_BY_PATH_LIMIT;

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", buildFullAuthHeader(apiKey))
                .build();

        getClient().newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.w(TAG, "searchItemByPath failed: " + e.getMessage());
                getMainHandler().post(() -> callback.onNotFound("Network error: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful() || response.body() == null) {
                        final String msg = "HTTP error " + response.code();
                        getMainHandler().post(() -> callback.onNotFound(msg));
                        return;
                    }
                    String body = response.body().string();
                    String itemId = parseSearchByPathResponse(body, serverPath);
                    if (itemId != null) {
                        getMainHandler().post(() -> callback.onFound(itemId));
                    } else {
                        getMainHandler().post(() -> callback.onNotFound("No item found matching path"));
                    }
                } catch (Exception e) {
                    Log.w(TAG, "searchItemByPath parse error: " + e.getMessage());
                    final String msg = "Parse error: " + e.getMessage();
                    getMainHandler().post(() -> callback.onNotFound(msg));
                } finally {
                    response.close();
                }
            }
        });
    }

    /**
     * Builds the media segment URL for an episode, filtered to the two types this app uses.
     * Package-private so the URL shape is unit testable without a network call.
     *
     * @param serverUrl Base server URL
     * @param itemId    Episode item ID
     * @return GET target for the episode's intro and outro segments
     */
    static String buildMediaSegmentsUrl(String serverUrl, String itemId) {
        return baseUrl(serverUrl) + "/MediaSegments/" + itemId
                + "?includeSegmentTypes=" + SEGMENT_TYPE_INTRO
                + "&includeSegmentTypes=" + SEGMENT_TYPE_OUTRO;
    }

    /**
     * Fetches intro and credit segments for an episode from the Jellyfin server.
     * Uses GET /MediaSegments/{itemId}, Jellyfin's own route, which Intro Skipper 12.0
     * mirrors its analysis results into; the plugin's old /Episode/{id}/IntroSkipperSegments
     * route was removed in its 12.0 line.
     * Returns the raw JSON response body via callback for parsing with parseIntroSkipperResponse().
     * On any non 2xx answer, returns empty JSON "{}" which parses to all -1 sentinels, so a
     * server without the plugin simply plays with no skipping.
     * Callback runs on the main (UI) thread.
     *
     * @param serverUrl   Base server URL
     * @param accessToken Access token
     * @param itemId      Episode item ID
     * @param callback    Callback for success (raw JSON body) / error
     */
    public static void getIntroSkipperSegments(String serverUrl, String accessToken,
                                                String itemId, SimpleCallback callback) {
        String url = buildMediaSegmentsUrl(serverUrl, itemId);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", buildAuthHeader(accessToken))
                .build();

        getClient().newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.w(TAG, "getIntroSkipperSegments failed: " + e.getMessage());
                // Silent no-op: empty JSON parses to all -1 sentinels
                getMainHandler().post(() -> callback.onSuccess("{}"));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful() || response.body() == null) {
                        int code = response.code();
                        if (code == 401 || code == 403) {
                            Log.w(TAG, "getIntroSkipperSegments: HTTP " + code
                                    + ", the server rejected the access token, so no intro or"
                                    + " credit segments will be read for item " + itemId);
                        } else {
                            // 404 or other error: silent no-op with empty JSON
                            Log.d(TAG, "getIntroSkipperSegments: HTTP " + code + " for item " + itemId);
                        }
                        getMainHandler().post(() -> callback.onSuccess("{}"));
                        return;
                    }
                    String body = response.body().string();
                    getMainHandler().post(() -> callback.onSuccess(body));
                } finally {
                    response.close();
                }
            }
        });
    }
}
