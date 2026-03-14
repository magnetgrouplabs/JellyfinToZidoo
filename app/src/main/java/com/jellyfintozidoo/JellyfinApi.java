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
     * Builds the Jellyfin authorization header value.
     * Package-private for testability.
     *
     * @param apiKey The API key or access token
     * @return The formatted authorization header value
     */
    static String buildAuthHeader(String apiKey) {
        return "MediaBrowser Token=\"" + apiKey + "\"";
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
        if (zidooPath == null || zidooPath.isEmpty()) {
            return null;
        }

        // URL-decode the path (handles %20 etc.)
        String decoded;
        try {
            decoded = URLDecoder.decode(zidooPath, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            decoded = zidooPath;
        }

        // Strip SMB credentials: smb://user:pass@host -> smb://host
        String stripped = decoded.replaceFirst("smb://[^@]+@", "smb://");

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
        String baseUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
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
        String baseUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        String url = baseUrl + "/Users/AuthenticateByName";

        JsonObject body = new JsonObject();
        body.addProperty("Username", username);
        body.addProperty("Pw", password);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "MediaBrowser Client=\"JellyfinToZidoo\", Device=\"Zidoo\", DeviceId=\"jellyfintozidoo\", Version=\"1.0.0\"")
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
     * Used for POST requests that require full client identification.
     *
     * @param apiKey The access token
     * @return The formatted full authorization header value
     */
    static String buildFullAuthHeader(String apiKey) {
        return "MediaBrowser Client=\"JellyfinToZidoo\", Device=\"Zidoo\", "
                + "DeviceId=\"jellyfintozidoo\", Version=\"1.0.0\", Token=\"" + apiKey + "\"";
    }

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json");

    /**
     * Reports playback start to Jellyfin server.
     * POST /Sessions/Playing
     */
    public static void reportPlaybackStart(String serverUrl, String apiKey, String itemId,
                                            String playSessionId, SimpleCallback callback) {
        String baseUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
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
        String baseUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
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
        String baseUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
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
     * Marks an item as watched (played) on the Jellyfin server.
     * POST /Users/{userId}/PlayedItems/{itemId}
     */
    public static void markAsWatched(String serverUrl, String apiKey, String userId,
                                      String itemId, SimpleCallback callback) {
        String baseUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        String url = baseUrl + "/Users/" + userId + "/PlayedItems/" + itemId;

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
        String baseUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
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

    /**
     * Enqueues an OkHttp request with simple success/error callback on the main thread.
     * Shared by all reporting methods.
     */
    private static void enqueueSimpleRequest(Request request, String operationName,
                                              SimpleCallback callback) {
        getClient().newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, operationName + " failed", e);
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
        String baseUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
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
}
