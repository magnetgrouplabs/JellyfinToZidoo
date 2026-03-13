# Phase 2: Core Bridge - Research

**Researched:** 2026-03-13
**Domain:** Android intent interception, Jellyfin REST API, EncryptedSharedPreferences, Zidoo player launch
**Confidence:** HIGH

## Summary

Phase 2 requires intercepting `ACTION_VIEW` intents sent by Jellyfin Android TV clients (stock and Moonfin), extracting the Jellyfin item ID from the HTTP streaming URL, calling the Jellyfin API to resolve the server-side file path, applying path substitution to produce an SMB URI, and launching the Zidoo native player at the correct resume position. Authentication uses API key only (stored in EncryptedSharedPreferences), and the settings/debug screens extend the existing PlexToZidoo patterns.

The critical discovery is the exact intent format: Jellyfin Android TV sends `Intent.ACTION_VIEW` with a data URI like `http://server:8096/Videos/{itemId}/stream?static=true&mediaSourceId={id}&api_key={token}` and MIME type `video/*`. The item ID is a UUID embedded in the URL path. Moonfin is a fork of jellyfin-androidtv and uses the same external player mechanism. The position extra is `"position"` in milliseconds (already converted from ticks by the Jellyfin client), but we also need to fetch `UserData.PlaybackPositionTicks` from the API for cases where the client passes 0 or doesn't pass position at all.

**Primary recommendation:** Extract item ID from the incoming URL via regex on the `/Videos/{uuid}/stream` pattern, call `GET /Items/{id}?Fields=Path,MediaSources` with the API key in the Authorization header, then apply the existing `doSubstitution()` logic to the server-side path.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- Primary targets: Moonfin and stock Jellyfin Android TV client
- Jellyfin clients send HTTP streaming URLs; we extract the item ID and call the Jellyfin API to get the server-side file path
- API key only -- no username/password auth. Simple, one field.
- Credentials stored using EncryptedSharedPreferences (AndroidX Security library)
- Auth token persists across app restarts
- New "Jellyfin Server" section at TOP of settings screen with: Server URL, API Key
- Below that: General (debug toggle), Player, Substitution, Import/Export -- same order as current
- Follow PlexToZidoo's existing error patterns exactly -- don't reinvent
- Follow PlexToZidoo's existing debug screen pattern, replace Plex fields with Jellyfin equivalents
- Jellyfin uses ticks (1 tick = 100ns), Zidoo player uses milliseconds
- "Whatever PlexToZidoo does" for error handling, debug display, and UX

### Claude's Discretion
- Test connection button in settings (if trivially simple to add)
- Debug screen information density (pipeline trace vs minimal)
- Settings sub-screen layout (flat vs sub-pages)
- Resume position display format on debug screen
- HTTP client choice for Jellyfin API calls (OkHttp preferred per PROJECT.md)
- Error handling specifics -- match PlexToZidoo patterns

### Deferred Ideas (OUT OF SCOPE)
- Username/password authentication
- Identifying calling app (Moonfin vs stock) on debug screen
- Findroid-specific support
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| AUTH-01 | Authenticate with Jellyfin server using API key | Authorization header: `MediaBrowser Token="<key>"`. Single field in settings. |
| AUTH-02 | Authenticate with username/password | DEFERRED per user decision -- API key only for Phase 2 |
| AUTH-03 | Credentials stored securely using Android Keystore | EncryptedSharedPreferences uses Android Keystore internally via MasterKey |
| AUTH-04 | Auth token persists across app restarts | EncryptedSharedPreferences persists like regular SharedPreferences |
| BRDG-01 | Intercept ACTION_VIEW intents for video MIME types | Existing manifest intent filters already handle this correctly |
| BRDG-02 | Extract item ID from Jellyfin HTTP streaming URL | Regex on `/Videos/([a-f0-9-]{32,36})/stream` from the intent data URI |
| BRDG-03 | Resolve server-side file path via Jellyfin API | `GET /Items/{id}?Fields=Path,MediaSources` returns `Path` and `MediaSources[].Path` |
| BRDG-04 | Apply path substitution to convert server path to SMB URI | Existing `doSubstitution()` handles this -- pass API-resolved path to it |
| BRDG-05 | Launch native Zidoo player with SMB path | Existing `buildZidooIntent()` handles this already |
| BRDG-06 | Pass resume position converting Jellyfin ticks to ms | `UserData.PlaybackPositionTicks / 10000 = milliseconds`; also check intent extra `position` |
| SETT-01 | Configure Jellyfin server URL | New EditTextPreference in Jellyfin Server category at top of root_preferences.xml |
| SETT-02 | Configure API key credentials | New EditTextPreference with password input type in Jellyfin Server category |
| SETT-03 | Configure path substitution rule | Already exists in current settings -- no changes needed |
| SETT-05 | Configure SMB username/password | Already exists in current settings -- no changes needed |
| SETT-06 | Toggle debug screen on/off | Already exists in current settings -- no changes needed |
| SETT-07 | Settings UI is D-pad navigable | PreferenceScreen is inherently D-pad navigable on Android TV |
| DEBG-01 | Debug screen shows parsed intent data | Extend existing `updateDebugPage()` to show Jellyfin item ID, raw URL |
| DEBG-02 | Debug screen shows resolved file path from Jellyfin API | Show the `Path` value returned by the API call |
| DEBG-03 | Debug screen shows substituted SMB path | Already shown as "Path Substitution" in current debug output |
| DEBG-04 | Debug screen has manual Play button | Already exists -- `play_button` in activity_play.xml |
</phase_requirements>

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| OkHttp | 4.12.0 | HTTP client for Jellyfin API calls | PROJECT.md specifies OkHttp; use 4.x not 5.x for Java 8 compatibility |
| androidx.security:security-crypto | 1.1.0-alpha06 | EncryptedSharedPreferences | User decision; wraps Android Keystore, drop-in SharedPreferences replacement |
| androidx.preference | 1.1.1 (existing) | Settings screens | Already in use, PreferenceScreen pattern established |
| com.google.code.gson | 2.8.6 (existing) | JSON parsing of Jellyfin API responses | Already a dependency; sufficient for parsing Items response |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Volley | 1.2.0 (existing) | Legacy HTTP client | Already in build.gradle but commented out in code; do NOT use for new code |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| OkHttp 4.12.0 | OkHttp 5.3.0 | 5.x requires Kotlin stdlib and targets newer JVM; 4.x is pure Java-friendly and matches Java 8 source compat |
| Gson | org.json (built-in) | Gson already in deps and handles typed deserialization; org.json requires manual parsing |

**Installation:**
```groovy
// Add to app/build.gradle dependencies
implementation 'com.squareup.okhttp3:okhttp:4.12.0'
implementation 'androidx.security:security-crypto:1.1.0-alpha06'
```

**Note on minSdkVersion:** Current `minSdkVersion` is 28 (Android 9). EncryptedSharedPreferences requires API 23+, so no change needed. OkHttp 4.x supports API 21+.

## Architecture Patterns

### Recommended Project Structure
```
app/src/main/java/com/jellyfintozidoo/
    Play.java                  # Modified: add Jellyfin URL parsing + API call flow
    SettingsActivity.java      # Modified: add Jellyfin server section handling
    JellyfinApi.java           # NEW: Jellyfin API client (item lookup, connection test)
    SecureStorage.java         # NEW: EncryptedSharedPreferences wrapper
app/src/main/res/xml/
    root_preferences.xml       # Modified: add Jellyfin Server category at top
```

### Pattern 1: Intent Interception Flow
**What:** When a Jellyfin client fires an external player intent, Play.java receives it, extracts the item ID, calls the Jellyfin API, then launches the Zidoo player.
**When to use:** Every time a video intent arrives that is not from ZDMC.

**Flow:**
```
1. Play.onStart() receives intent
2. Extract data URI: http://server:8096/Videos/{itemId}/stream?static=true&...
3. Parse item ID (UUID) from URL path using regex
4. Call JellyfinApi.getItemPath(itemId) -> returns server-side file path
5. Call doSubstitution(serverPath) -> produces SMB URI
6. Fetch resume position: UserData.PlaybackPositionTicks / 10000 -> ms
7. Call buildZidooIntent(smbPath, resumeMs)
8. showDebugPageOrSendIntent()
```

### Pattern 2: Jellyfin API Authentication
**What:** All Jellyfin API calls include the API key in the Authorization header.
**Format:** `Authorization: MediaBrowser Token="<api_key>"`

```java
// Source: https://gist.github.com/nielsvanvelzen/ea047d9028f676185832e51ffaf12a6f
Request request = new Request.Builder()
    .url(serverUrl + "/Items/" + itemId + "?Fields=Path,MediaSources")
    .addHeader("Authorization", "MediaBrowser Token=\"" + apiKey + "\"")
    .build();
```

### Pattern 3: EncryptedSharedPreferences Setup
**What:** Secure credential storage that behaves like regular SharedPreferences.
**When to use:** For storing the API key only. All other settings remain in regular SharedPreferences.

```java
// Source: https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences
MasterKey masterKey = new MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build();

SharedPreferences securePrefs = EncryptedSharedPreferences.create(
    context,
    "jellyfin_secure_prefs",
    masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
);
```

**Important:** EncryptedSharedPreferences is slow to initialize. Create a single instance and reuse it. Do NOT use it for non-sensitive settings.

### Pattern 4: Jellyfin Item ID Extraction from URL
**What:** Parse the UUID item ID from the Jellyfin streaming URL.
**URL formats observed from Jellyfin Android TV source:**
- `http://server:8096/Videos/{itemId}/stream?static=true&mediaSourceId={id}&api_key={token}`
- The `getVideoStreamUrl()` method in jellyfin-androidtv constructs: `/Videos/{itemId}/stream` with query params

```java
// Extract item ID (UUID format) from Jellyfin streaming URL
Pattern jellyfinPattern = Pattern.compile("/Videos/([a-f0-9]{32}|[a-f0-9-]{36})/stream", Pattern.CASE_INSENSITIVE);
Matcher matcher = jellyfinPattern.matcher(inputUrl);
if (matcher.find()) {
    String itemId = matcher.group(1);
    // Proceed with API lookup
}
```

**Note:** Jellyfin UUIDs may appear with or without hyphens (32 hex chars or 36 with hyphens).

### Anti-Patterns to Avoid
- **Don't parse the file path from the URL query string:** The streaming URL does not contain the server-side file path. You must call the API.
- **Don't use the streaming URL directly for playback:** It's an HTTP transcode/remux URL, not the direct file. The whole point is SMB playback via path substitution.
- **Don't make API calls on the main thread:** Use OkHttp's async `enqueue()` method or a background thread. The existing Plex code used Volley's async queue; follow the same async pattern.
- **Don't store the API key in regular SharedPreferences:** User decision mandates EncryptedSharedPreferences.
- **Don't create multiple EncryptedSharedPreferences instances:** It's expensive. Create once, reuse.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Credential encryption | Custom AES encryption | EncryptedSharedPreferences | Handles key management, rotation, and Android Keystore integration |
| HTTP requests | Raw HttpURLConnection | OkHttp 4.12.0 | Connection pooling, automatic retries, proper timeout handling |
| JSON parsing | Manual String splitting | Gson (already in deps) | Type-safe deserialization, handles nested objects |
| Settings UI | Custom layout | PreferenceScreen XML | D-pad navigation, persistence, and standard patterns built-in |

**Key insight:** The entire Zidoo player launch mechanism (`buildZidooIntent`) and path substitution logic (`doSubstitution`) already work. This phase is primarily about adding a Jellyfin API call step between intent reception and the existing substitution/launch pipeline.

## Common Pitfalls

### Pitfall 1: Tick-to-Millisecond Conversion
**What goes wrong:** Using wrong conversion factor, leading to wrong resume position.
**Why it happens:** Jellyfin uses .NET ticks (1 tick = 100 nanoseconds = 0.0001 ms). The conversion is `ticks / 10000 = ms`.
**How to avoid:** Use the constant `TICKS_PER_MS = 10000` (confirmed from Andy2244's code: `RUNTIME_TICKS_TO_MS = 10000`).
**Warning signs:** Resume position is wildly off (hours instead of minutes, or seconds instead of the right spot).

### Pitfall 2: API Key vs User Token for /Items Endpoint
**What goes wrong:** API key returns no `UserData` (including `PlaybackPositionTicks`) because there's no user context.
**Why it happens:** Jellyfin issue #11408 documented that API keys don't establish user context. The fix in PR #11471 requires passing a userId parameter.
**How to avoid:** Use `GET /Users/{userId}/Items/{itemId}?Fields=Path,MediaSources` instead of `/Items/{itemId}`. This requires knowing the userId. Alternatively, use `/Items/{id}?Fields=Path,MediaSources&userId={userId}`.
**Warning signs:** Response has `UserData: null` or missing `PlaybackPositionTicks`.

### Pitfall 3: Jellyfin Server URL Trailing Slash
**What goes wrong:** Double slashes in API URLs causing 404s.
**Why it happens:** User enters `http://192.168.1.10:8096/` with trailing slash, code prepends to `/Items/...`.
**How to avoid:** Strip trailing slash from server URL before storing/using it.
**Warning signs:** API calls fail with connection errors or 404s.

### Pitfall 4: EncryptedSharedPreferences and Import/Export
**What goes wrong:** Exported settings don't include the API key (it's in a separate encrypted file), or importing tries to write to encrypted prefs through the wrong SharedPreferences instance.
**Why it happens:** EncryptedSharedPreferences uses a different backing file than regular SharedPreferences.
**How to avoid:** When exporting, explicitly read from encrypted prefs and include the server URL (but consider security -- don't export the API key in cleartext). When importing, write to the correct SharedPreferences instance. Follow the existing pattern where `smbPassword` is excluded from export.
**Warning signs:** API key disappears after import, or export file contains encrypted gibberish.

### Pitfall 5: Network Call on Main Thread
**What goes wrong:** `android.os.NetworkOnMainThreadException` crash.
**Why it happens:** Calling OkHttp synchronously in `onStart()`.
**How to avoid:** Use OkHttp's `enqueue()` for async calls. The old Plex code used Volley which is inherently async. Follow the same callback pattern: on success -> `doSubstitution()` -> `showDebugPageOrSendIntent()`, on failure -> set `message` error -> `showDebugPageOrSendIntent()`.
**Warning signs:** App crashes immediately when receiving an intent.

### Pitfall 6: Item ID Format Variations
**What goes wrong:** Regex fails to match the item ID.
**Why it happens:** Jellyfin item IDs are UUIDs that may appear with hyphens (`550e8400-e29b-41d4-a716-446655440000`) or without (`550e8400e29b41d4a716446655440000`).
**How to avoid:** Match both formats in the regex: `[a-f0-9]{32}|[a-f0-9-]{36}`.
**Warning signs:** "ERROR: No item ID found" on debug screen.

## Code Examples

### Jellyfin API Response Structure (Items endpoint)
```json
// GET /Items/{id}?Fields=Path,MediaSources
// Authorization: MediaBrowser Token="your_api_key"
{
  "Name": "Movie Title",
  "Id": "550e8400e29b41d4a716446655440000",
  "Path": "/media/movies/Movie Title (2024)/Movie Title.mkv",
  "RunTimeTicks": 72000000000,
  "UserData": {
    "PlaybackPositionTicks": 36000000000,
    "PlayCount": 0,
    "IsFavorite": false,
    "Played": false
  },
  "MediaSources": [
    {
      "Id": "550e8400e29b41d4a716446655440000",
      "Path": "/media/movies/Movie Title (2024)/Movie Title.mkv",
      "Protocol": "File",
      "Type": "Default"
    }
  ]
}
```

### Complete Intent Interception and API Call Flow
```java
// In Play.java onStart(), replacing the Plex server communication block:

// Step 1: Extract Jellyfin item ID from streaming URL
String inputUrl = originalIntent.getDataString();
Pattern jellyfinPattern = Pattern.compile(
    "/Videos/([a-f0-9]{32}|[a-f0-9-]{36})/stream",
    Pattern.CASE_INSENSITIVE
);
Matcher jellyfinMatcher = jellyfinPattern.matcher(inputUrl);

if (jellyfinMatcher.find()) {
    String itemId = jellyfinMatcher.group(1);

    // Step 2: Also check for position from intent extras (Jellyfin sends ms)
    int intentPosition = originalIntent.getIntExtra("position", 0);

    // Step 3: Read server config from secure storage
    String serverUrl = getServerUrl();  // from regular SharedPreferences
    String apiKey = getApiKey();        // from EncryptedSharedPreferences

    if (serverUrl.isEmpty() || apiKey.isEmpty()) {
        message = "ERROR: Jellyfin server not configured. Go to Settings.";
        showDebugPageOrSendIntent();
        return;
    }

    // Step 4: Call Jellyfin API (async)
    JellyfinApi.getItem(serverUrl, apiKey, itemId, new JellyfinApi.Callback() {
        @Override
        public void onSuccess(String serverPath, long positionTicks, String title) {
            videoPath = serverPath;
            videoTitle = title;

            // Use API position if intent didn't provide one
            if (intentPosition > 0) {
                viewOffset = intentPosition;
            } else if (positionTicks > 0) {
                viewOffset = (int)(positionTicks / 10000); // ticks to ms
            }

            doSubstitution(serverPath);
            showDebugPageOrSendIntent();
        }

        @Override
        public void onError(String error) {
            message = "ERROR: " + error;
            showDebugPageOrSendIntent();
        }
    });
} else {
    // Fallback: not a Jellyfin URL, try direct substitution (existing behavior)
    doSubstitution(directPath);
    showDebugPageOrSendIntent();
}
```

### JellyfinApi Class Structure
```java
public class JellyfinApi {
    private static final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build();

    public interface Callback {
        void onSuccess(String serverPath, long positionTicks, String title);
        void onError(String error);
    }

    public static void getItem(String serverUrl, String apiKey, String itemId, Callback callback) {
        String url = serverUrl + "/Items/" + itemId + "?Fields=Path,MediaSources";

        Request request = new Request.Builder()
            .url(url)
            .addHeader("Authorization", "MediaBrowser Token=\"" + apiKey + "\"")
            .build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onResponse(Call call, Response response) {
                // Parse JSON, extract Path and UserData.PlaybackPositionTicks
                // Run callback on main thread
            }

            @Override
            public void onFailure(Call call, IOException e) {
                // Run error callback on main thread
            }
        });
    }

    // Optional: test connection method
    public static void testConnection(String serverUrl, String apiKey, SimpleCallback callback) {
        String url = serverUrl + "/System/Info";
        // Similar pattern, just check for 200 OK
    }
}
```

### EncryptedSharedPreferences Setup
```java
public class SecureStorage {
    private static SharedPreferences instance;

    public static synchronized SharedPreferences getInstance(Context context) {
        if (instance == null) {
            try {
                MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

                instance = EncryptedSharedPreferences.create(
                    context,
                    "jellyfin_secure_prefs",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                );
            } catch (Exception e) {
                // Fallback to regular SharedPreferences if encryption fails
                // (shouldn't happen on API 28+)
                instance = context.getSharedPreferences("jellyfin_secure_prefs_fallback", Context.MODE_PRIVATE);
            }
        }
        return instance;
    }
}
```

### Settings XML Addition
```xml
<!-- Add at TOP of root_preferences.xml, before General category -->
<PreferenceCategory app:title="Jellyfin Server">

    <EditTextPreference
        android:defaultValue=""
        android:key="jellyfin_server_url"
        android:title="Server URL"
        android:summary="e.g. http://192.168.1.10:8096"
        app:useSimpleSummaryProvider="true"
        android:singleLine="true" />

    <EditTextPreference
        android:defaultValue=""
        android:key="jellyfin_api_key"
        android:title="API Key"
        app:useSimpleSummaryProvider="false"
        android:singleLine="true"
        android:inputType="textPassword" />

</PreferenceCategory>
```

**Note on API key PreferenceScreen integration:** The API key is stored in EncryptedSharedPreferences, but PreferenceScreen binds to the default SharedPreferences. The settings activity will need custom handling: when the API key preference changes, intercept the change and write to EncryptedSharedPreferences instead. On load, read from EncryptedSharedPreferences and set the preference summary. This is the only tricky integration point.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Volley for HTTP | OkHttp 4.x | Project decision | Simpler API, better error handling, connection pooling |
| Plain SharedPreferences for secrets | EncryptedSharedPreferences | AndroidX Security 1.0 (2020) | Automatic encryption with Android Keystore |
| MasterKeys (deprecated) | MasterKey.Builder | security-crypto 1.1.0-alpha03 | New API for key management |
| Plex XML parsing | Jellyfin JSON (Gson) | This fork | JSON is simpler than XML; Gson already in deps |

**Deprecated/outdated:**
- `MasterKeys.getOrCreate()`: Deprecated. Use `new MasterKey.Builder(context).setKeyScheme(...).build()`.
- OkHttp 3.x: End of life. Use 4.12.0 for Java projects.
- Volley: Still works but not preferred per PROJECT.md. Already a dependency but commented out.

## Open Questions

1. **UserData availability with API key auth**
   - What we know: Jellyfin issue #11408 showed API keys may not return UserData. Fix was merged in PR #11471.
   - What's unclear: Whether the user's Jellyfin version includes this fix. Older servers may not return PlaybackPositionTicks with API key auth.
   - Recommendation: Always try to read PlaybackPositionTicks. If null/missing, fall back to intent extra `position`. If both are 0, play from start. Log a warning on debug screen if UserData is missing.

2. **Jellyfin server URL determination**
   - What we know: The intent data URI contains the server URL (e.g., `http://192.168.1.10:8096/Videos/...`).
   - What's unclear: Whether we should extract the server URL from the intent or require manual configuration.
   - Recommendation: Require manual configuration in settings (user decision). But also validate that the intent URL matches the configured server, and warn on debug screen if it doesn't.

3. **Test connection button complexity**
   - What we know: `GET /System/Info` with the API key returns server version info if auth succeeds.
   - What's unclear: Edge cases (wrong URL format, server down, firewall).
   - Recommendation: Add it. It's a single OkHttp call to `/System/Info`. Show a Toast with success/failure. Trivially simple.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4 (already in build.gradle) + Android Instrumentation |
| Config file | build.gradle (testImplementation already configured) |
| Quick run command | `./gradlew testDebugUnitTest` |
| Full suite command | `./gradlew test connectedAndroidTest` |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| BRDG-02 | Extract item ID from streaming URL | unit | `./gradlew testDebugUnitTest --tests "*JellyfinUrlParserTest*"` | No - Wave 0 |
| BRDG-06 | Tick to ms conversion | unit | `./gradlew testDebugUnitTest --tests "*TickConversionTest*"` | No - Wave 0 |
| AUTH-01 | API key auth header format | unit | `./gradlew testDebugUnitTest --tests "*JellyfinApiTest*"` | No - Wave 0 |
| BRDG-03 | API response JSON parsing | unit | `./gradlew testDebugUnitTest --tests "*JellyfinApiTest*"` | No - Wave 0 |
| BRDG-01 | Intent interception | manual-only | Manual: fire intent via adb | N/A |
| BRDG-04 | Path substitution (existing) | manual-only | Manual: verify on device | N/A |
| BRDG-05 | Zidoo player launch | manual-only | Manual: verify on device | N/A |
| SETT-01 | Server URL setting | manual-only | Manual: D-pad navigation test | N/A |
| DEBG-01-04 | Debug screen display | manual-only | Manual: visual inspection | N/A |

### Sampling Rate
- **Per task commit:** `./gradlew testDebugUnitTest`
- **Per wave merge:** `./gradlew testDebugUnitTest`
- **Phase gate:** Unit tests green + manual device test before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `app/src/test/java/com/jellyfintozidoo/JellyfinUrlParserTest.java` -- covers BRDG-02 (URL parsing)
- [ ] `app/src/test/java/com/jellyfintozidoo/TickConversionTest.java` -- covers BRDG-06 (tick conversion)
- [ ] `app/src/test/java/com/jellyfintozidoo/JellyfinApiTest.java` -- covers AUTH-01, BRDG-03 (API auth + JSON parsing)

## Sources

### Primary (HIGH confidence)
- [Jellyfin Android TV ExternalPlayerActivity.kt source](https://github.com/jellyfin/jellyfin-androidtv/blob/master/app/src/main/java/org/jellyfin/androidtv/ui/playback/ExternalPlayerActivity.kt) - Exact intent construction code, confirmed ACTION_VIEW with /Videos/{id}/stream URL and position extra in milliseconds
- [Andy2244 jellyfin-androidtv-zidoo ExternalPlayerActivity.java](https://github.com/Andy2244/jellyfin-androidtv-zidoo) - Zidoo-specific intent extras (title, position, from_start, audio_idx, subtitle_idx, return_result), RUNTIME_TICKS_TO_MS = 10000
- [Jellyfin API Authorization gist](https://gist.github.com/nielsvanvelzen/ea047d9028f676185832e51ffaf12a6f) - Authorization header format: `MediaBrowser Token="<key>"`
- [Android EncryptedSharedPreferences reference](https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences) - API setup, MasterKey.Builder

### Secondary (MEDIUM confidence)
- [Jellyfin issue #11408](https://github.com/jellyfin/jellyfin/issues/11408) - UserData missing with API key auth (fixed in PR #11471)
- [Moonfin Android TV repo](https://github.com/Moonfin-Client/AndroidTV-FireTV) - Confirmed fork of jellyfin-androidtv, same external player mechanism
- [OkHttp documentation](https://square.github.io/okhttp/) - Version 4.12.0 for Java 8 compatibility

### Tertiary (LOW confidence)
- Jellyfin Items API response structure - Inferred from multiple GitHub issues and code examples, not from official API spec directly

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - OkHttp and EncryptedSharedPreferences are well-documented, versions verified
- Architecture: HIGH - Intent format confirmed from actual source code of jellyfin-androidtv and Andy2244's Zidoo edition
- Pitfalls: HIGH - Tick conversion confirmed (10000x), API key auth UserData issue documented in Jellyfin issue tracker
- URL parsing: HIGH - Verified from ExternalPlayerActivity.kt source: `getVideoStreamUrl()` produces `/Videos/{itemId}/stream` pattern

**Research date:** 2026-03-13
**Valid until:** 2026-04-13 (stable domain, Jellyfin API and Android libraries are mature)
