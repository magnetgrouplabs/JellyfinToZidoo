# Phase 4: Episode Intelligence - Research

**Researched:** 2026-03-14
**Domain:** Android TV UI (Up Next screen), Jellyfin API (NextUp, Items/search-by-path), Zidoo HTTP API, image loading/blur
**Confidence:** HIGH

## Summary

Phase 4 transforms the current "open next episode in Jellyfin client" flow into an in-app "Up Next" countdown experience with per-episode playback tracking. The existing codebase already has the key building blocks: `handleEpisodeCompleted()` detects path changes, `getNextUp()` queries the NextUp API, `doSubstitution()` converts server paths to SMB URIs, and `buildZidooIntent()` launches the player. The main new work is (1) an Up Next Activity with blurred backdrop UI, (2) reverse path substitution for identifying episodes when Zidoo auto-advances, (3) resetting the playback tracking cycle per-episode, and (4) exposing multi-slot substitution rules in the settings UI.

The critical hardware constraint (Zidoo Z9X Pro with limited RAM/CPU) primarily affects the Up Next screen: Glide must use memory-efficient bitmap handling, blur should use a moderate radius, and the Activity should be lightweight with no unnecessary background processing. The existing 10-second polling interval is already conservative and appropriate.

**Primary recommendation:** Build a single new `UpNextActivity` with Glide for backdrop blur, wire it into the existing `handleEpisodeCompleted()` and `onActivityResult()` flows, implement reverse path substitution as a utility method alongside existing `doSubstitution()`, and expose multi-slot substitution via the already-existing `substitution_preferences.xml` infrastructure.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- Build an "Up Next" countdown screen that shows between episodes
- 10-second countdown timer, auto-plays next episode when it reaches zero
- "Play Now" button (skip countdown) and "Cancel" button (return to Jellyfin client)
- Query Jellyfin NextUp API to get next episode, resolve file path, do substitution, launch Zidoo player
- Up Next screen only triggers for TV episodes (items with seriesId) -- never for movies
- Up Next only appears when episode is completed (>=90% watched) -- mid-episode stop saves resume and goes back to Jellyfin client (current Phase 3 behavior)
- When no next episode exists (series finale), skip Up Next and go back to Jellyfin client
- Blurred series backdrop image (Jellyfin `/Items/{id}/Images/Backdrop`) as full-screen background
- Use Glide for image loading + blur transform
- Semi-transparent gradient overlay for readability
- Centered card on top showing: episode thumbnail, series name, S##E## label, episode title
- D-pad navigable (Play Now + Cancel buttons) for Android TV
- Pull all metadata from Jellyfin API: Name, SeriesName, ParentIndexNumber, IndexNumber, ImageTags
- When Zidoo auto-advances to next file (path change detected in poller): mark previous episode watched, then identify new episode and start fresh tracking
- Fresh tracking = new getItem() call, new playSessionId, new playback start report, reset poller state
- Reverse path substitution to convert Zidoo SMB path back to server-side path, then search Jellyfin items by path to resolve item ID
- Track ANY path change regardless of direction (forward or backward navigation in Zidoo)
- Jellyfin NextUp API handles season boundaries natively -- no special client-side logic needed

### Claude's Discretion
- Countdown timer visual style (circular ring, linear bar, or simple number)
- Up Next screen layout specifics (exact spacing, typography, animation)
- Fallback behavior when reverse path lookup fails (silent log vs toast)
- Glide configuration details (cache strategy, blur radius)
- How to structure the reverse substitution logic (inline vs separate utility)
- Whether to create a new Activity for Up Next or use a Dialog/Fragment overlay

### Deferred Ideas (OUT OF SCOPE)
- Configurable countdown timer duration in settings -- hardcode 10s for now
- "Continue watching" prompt when stopping mid-episode -- just save resume for now
- Series completion celebration screen -- just go back to Jellyfin
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| EPIS-01 | App detects when user navigates to a different episode in Zidoo player (via getPlayStatus path changes) | Already implemented in `startProgressPoller()` -- detects `video.path` changes. Phase 4 extends this to do reverse path lookup and reset tracking state. |
| EPIS-02 | Each episode navigated to in Zidoo player gets its watched status reported to Jellyfin | Requires reverse path substitution to identify new episode, then fresh `getItem()` + `reportPlaybackStart()` + new `playSessionId` cycle. |
| EPIS-03 | App auto-launches next episode after current episode finishes | Replace current `searchNextEpisode()` (opens Jellyfin client) with Up Next Activity that resolves path, does substitution, launches Zidoo player directly. |
| EPIS-04 | App handles season boundaries for next episode resolution | Jellyfin NextUp API (`/Shows/NextUp?seriesId=X&limit=1`) natively returns cross-season episodes. No special handling needed. |
| SETT-04 | User can configure multiple path substitution rules | Backend already supports 10 slots. Need to expose slots 2-10 more prominently in settings UI via existing `substitution_preferences.xml`. |
</phase_requirements>

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Glide | 4.16.0 | Image loading + blur transform | Industry standard for Android image loading; memory-efficient, lifecycle-aware. Use v4 not v5 since project targets Java 8 and API 28 |
| glide-transformations | 4.3.0 | BlurTransformation for backdrop | wasabeef's library provides `BlurTransformation(radius, sampling)` that works with Glide v4 |
| OkHttp | 4.12.0 | HTTP client (already in project) | Already used for all Jellyfin API calls |
| Gson | 2.8.6 | JSON parsing (already in project) | Already used for all API response parsing |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| AndroidX AppCompat | 1.2.0 | Activity base class (already in project) | UpNextActivity extends AppCompatActivity |
| AndroidX ConstraintLayout | 2.0.4 | Layout (already in project) | Up Next screen layout |
| Material Components | 1.2.1 | UI components (already in project) | Buttons, card styling |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Glide | Picasso | Glide has better memory management and built-in transformation support; user explicitly chose Glide |
| Separate Activity | Dialog/Fragment overlay | Activity is cleaner for lifecycle management and D-pad focus; recommended |
| glide-transformations | RenderScript blur | RenderScript deprecated in API 31; wasabeef library is simpler and maintained |

**Installation (add to app/build.gradle dependencies):**
```groovy
implementation 'com.github.bumptech.glide:glide:4.16.0'
annotationProcessor 'com.github.bumptech.glide:compiler:4.16.0'
implementation 'jp.wasabeef:glide-transformations:4.3.0'
```

## Architecture Patterns

### Recommended Project Structure
```
app/src/main/java/com/jellyfintozidoo/
    Play.java                    # Modified: wire Up Next flow, per-episode tracking reset
    JellyfinApi.java             # Modified: extend getNextUp() to return full metadata, add searchItemByPath()
    UpNextActivity.java          # NEW: countdown screen with blurred backdrop
    PathSubstitution.java        # NEW (optional): reverse substitution utility
    SettingsActivity.java        # Modified: expose multi-slot substitution more prominently

app/src/main/res/
    layout/activity_up_next.xml  # NEW: Up Next screen layout
    xml/root_preferences.xml     # Modified: surface multi-slot substitution
```

### Pattern 1: Up Next Activity Flow
**What:** New Activity launched after episode completes, shows countdown, auto-launches next episode
**When to use:** When current episode reaches >=90% watched AND item has seriesId AND NextUp returns a result
**Flow:**
```
handleEpisodeCompleted() / onActivityResult(watched)
    -> getNextUp(seriesId)
    -> if nextItemId != null:
        -> getItem(nextItemId) to get full metadata (path, title, season, episode, images)
        -> Launch UpNextActivity with extras (title, seriesName, seasonNum, episodeNum, imageUrl, serverPath)
    -> else: finish() (series finale)

UpNextActivity:
    -> Display blurred backdrop + episode info card + countdown
    -> On countdown complete OR "Play Now": doSubstitution(serverPath) -> buildZidooIntent() -> startActivityForResult()
    -> On "Cancel": setResult(RESULT_CANCELED) -> finish()
    -> Return result to Play activity for tracking setup
```

### Pattern 2: Per-Episode Tracking Reset (Zidoo Auto-Advance)
**What:** When poller detects path change, close out old episode tracking and start new tracking for the new file
**When to use:** Zidoo's internal "next file" navigation changes the video.path in getPlayStatus
**Flow:**
```
startProgressPoller() detects path change
    -> reportPlaybackStopped(oldItemId, oldPlaySessionId, durationTicks)
    -> markAsWatched(oldItemId)
    -> reverseSubstitution(newZidooPath) -> serverPath
    -> searchItemByPath(serverPath) -> newItemId
    -> getItem(newItemId) -> update all tracking state
    -> generate new playSessionId
    -> reportPlaybackStart(newItemId, newPlaySessionId)
    -> continue polling with new state
```

### Pattern 3: Reverse Path Substitution
**What:** Convert a Zidoo SMB path back to a Jellyfin server-side path for item lookup
**When to use:** When Zidoo auto-advances to a new file and we need to identify which Jellyfin item it is
**Implementation:**
```java
// Reverse of doSubstitution():
// Zidoo path: smb://user:pass@host/share/media/show/episode.mkv
// Strip credentials: smb://host/share/media/show/episode.mkv
// Apply reverse substitution rules:
//   replaced_with: "smb://host/share" -> path_to_replace: "/media"
// Result: /media/show/episode.mkv (server-side path)

// Then search Jellyfin: GET /Items?searchTerm=episode.mkv&IncludeItemTypes=Episode&Fields=Path&Recursive=true
// Filter results client-side by matching the full path
```

### Anti-Patterns to Avoid
- **Creating a new OkHttpClient per poll tick:** The existing `JellyfinApi.getClient()` singleton pattern must be used for all HTTP calls, including the Zidoo localhost API. Currently `startProgressPoller()` creates `new OkHttpClient()` each tick -- this should be fixed.
- **Blocking the UI thread during episode transition:** All API calls (stop report, mark watched, getNextUp, getItem) must chain asynchronously. Use the existing callback pattern.
- **Loading full-resolution backdrop images:** On Zidoo Z9X Pro, always request scaled-down images from Jellyfin API. Use `maxWidth=1280` parameter on image URLs to limit memory usage.
- **Not canceling the countdown timer on Activity destruction:** If user presses Back/Home during countdown, the timer must be canceled to prevent zombie launches.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Image blur | Custom RenderScript or manual bitmap blur | Glide + `BlurTransformation(25, 3)` | Handles memory, threading, caching automatically; RenderScript deprecated |
| Image loading/caching | Manual HTTP download + bitmap decode | Glide with `.override(1280, 720)` | Memory management, lifecycle awareness, disk caching |
| Countdown timer | Manual Thread/Handler timing | `CountDownTimer(10000, 1000)` | Android built-in, handles lifecycle correctly, runs on UI thread |
| Season boundary logic | Custom season-end detection | Jellyfin NextUp API | API already handles cross-season navigation natively |
| UUID generation for playSessionId | Custom random string | `java.util.UUID.randomUUID().toString()` | Standard, guaranteed unique |

**Key insight:** The Jellyfin NextUp API does the heavy lifting for episode sequencing. The app's job is to fetch metadata, display a UI, and launch the player -- not to understand TV show structure.

## Common Pitfalls

### Pitfall 1: Reverse Path Lookup Ambiguity
**What goes wrong:** Multiple episodes may have similar filenames. Searching by filename alone returns wrong results.
**Why it happens:** Jellyfin `/Items` search is text-based, not path-based. No direct "find by server path" filter exists.
**How to avoid:** Search by filename extracted from path, filter by `IncludeItemTypes=Episode`, then compare full `Path` field in results. Request `Fields=Path` in the query. If multiple matches, use the one whose Path exactly matches the reverse-substituted path.
**Warning signs:** Wrong episode getting tracked, watched status applied to wrong item.

### Pitfall 2: Zidoo Player Returning Before File Change Is Detectable
**What goes wrong:** When Zidoo auto-advances, the old Play activity may get `onActivityResult` before the poller detects the path change, causing the episode to be treated as "stopped mid-watch" instead of "completed."
**Why it happens:** Race condition between Zidoo's internal file navigation and the app's polling interval.
**How to avoid:** The existing code already handles this: `handleEpisodeCompleted()` is called from the poller when path changes are detected. The `onActivityResult` path is for when the user manually stops. These are two separate flows and should remain separate.
**Warning signs:** Episode marked as "resumed" instead of "watched" after Zidoo auto-advance.

### Pitfall 3: Memory Pressure from Backdrop Images on Zidoo Z9X Pro
**What goes wrong:** Loading a full 4K backdrop image into memory causes OOM or severe lag on the memory-constrained Zidoo device.
**Why it happens:** Jellyfin backdrop images can be 3840x2160 or larger.
**How to avoid:** Always request scaled images via URL parameter: `/Items/{id}/Images/Backdrop?maxWidth=1280`. Use Glide's `.override(1280, 720)` as a safety net. Use `BlurTransformation(25, 3)` where the second parameter (sampling=3) downscales before blur, reducing memory further.
**Warning signs:** App crash on Up Next screen, visible lag when loading backdrop.

### Pitfall 4: Countdown Timer Not Canceled on Activity Destroy
**What goes wrong:** If user navigates away during countdown, the timer fires and launches Zidoo player from a destroyed Activity.
**Why it happens:** `CountDownTimer` is not automatically canceled when Activity is destroyed.
**How to avoid:** Cancel the `CountDownTimer` in `onDestroy()`. Set a flag to prevent action after cancel.
**Warning signs:** Zidoo player launches unexpectedly after backing out of Up Next screen.

### Pitfall 5: SMB Credentials in Reverse Path
**What goes wrong:** Zidoo's `getPlayStatus` returns the playing path WITH embedded SMB credentials (user:pass@host). Reverse substitution fails because the credentials aren't part of the original substitution rule.
**Why it happens:** `doSubstitution()` adds credentials after the path substitution. The reverse must strip them first.
**How to avoid:** Before reversing substitution, strip the `user:pass@` portion from the SMB URI. Pattern: `smb://[^@]+@` -> `smb://`.
**Warning signs:** Reverse substitution returns null/empty, episode tracking silently fails.

### Pitfall 6: URI Encoding Mismatch
**What goes wrong:** `doSubstitution()` URI-encodes the path (`Uri.encode(path, "/ :")`). The Zidoo player may return the path decoded or with different encoding.
**Why it happens:** Different components handle URI encoding differently.
**How to avoid:** Decode both paths before comparison. Use `URLDecoder.decode()` on the Zidoo path before attempting reverse substitution.
**Warning signs:** Path comparison fails despite paths being "the same" to a human reader.

## Code Examples

### Up Next Activity Layout Pattern
```xml
<!-- activity_up_next.xml -->
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <!-- Blurred backdrop (full screen) -->
    <ImageView
        android:id="@+id/backdrop"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:scaleType="centerCrop" />

    <!-- Semi-transparent gradient overlay -->
    <View
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:background="#99000000" />

    <!-- Centered content card -->
    <LinearLayout
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:orientation="vertical"
        android:gravity="center"
        android:padding="32dp">

        <!-- Episode thumbnail -->
        <ImageView
            android:id="@+id/episodeThumbnail"
            android:layout_width="320dp"
            android:layout_height="180dp"
            android:scaleType="centerCrop" />

        <!-- "Up Next" label -->
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Up Next"
            android:textSize="14sp"
            android:textColor="#AAAAAA"
            android:layout_marginTop="16dp" />

        <!-- Series name -->
        <TextView
            android:id="@+id/seriesName"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textSize="20sp"
            android:textColor="#FFFFFF"
            android:textStyle="bold" />

        <!-- S##E## - Episode title -->
        <TextView
            android:id="@+id/episodeInfo"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textSize="16sp"
            android:textColor="#CCCCCC" />

        <!-- Countdown number -->
        <TextView
            android:id="@+id/countdown"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textSize="48sp"
            android:textColor="#FFFFFF"
            android:layout_marginTop="24dp" />

        <!-- Buttons row -->
        <LinearLayout
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:layout_marginTop="16dp">

            <Button
                android:id="@+id/btnPlayNow"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="Play Now"
                android:focusable="true"
                android:nextFocusRight="@id/btnCancel" />

            <Button
                android:id="@+id/btnCancel"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="Cancel"
                android:layout_marginStart="16dp"
                android:focusable="true"
                android:nextFocusLeft="@id/btnPlayNow" />
        </LinearLayout>
    </LinearLayout>
</FrameLayout>
```

### Glide Blur Loading (Memory-Efficient for Zidoo)
```java
// Source: Glide v4 docs + wasabeef/glide-transformations
// Load backdrop with blur, constrained for limited-RAM device
String backdropUrl = serverUrl + "/Items/" + seriesId + "/Images/Backdrop?maxWidth=1280";

Glide.with(this)
    .load(backdropUrl)
    .apply(new RequestOptions()
        .override(1280, 720)          // Constrain bitmap size
        .transform(new BlurTransformation(25, 3))  // radius=25, sampling=3 (downscale 3x before blur)
        .diskCacheStrategy(DiskCacheStrategy.ALL)   // Cache blurred result
        .skipMemoryCache(false))
    .into(backdropImageView);
```

### Reverse Path Substitution
```java
/**
 * Reverses doSubstitution(): converts a Zidoo SMB path back to a server-side path.
 * Strips SMB credentials, then applies substitution rules in reverse.
 *
 * @param zidooPath Path from Zidoo getPlayStatus (e.g., "smb://user:pass@host/share/show/ep.mkv")
 * @return Server-side path (e.g., "/media/show/ep.mkv"), or null if no rule matches
 */
private String reverseSubstitution(String zidooPath) {
    if (zidooPath == null || zidooPath.isEmpty()) return null;

    // Decode URI encoding
    String decoded = zidooPath;
    try {
        decoded = URLDecoder.decode(zidooPath, StandardCharsets.UTF_8.toString());
    } catch (Exception e) {
        // Use as-is
    }

    // Strip SMB credentials: smb://user:pass@host -> smb://host
    decoded = decoded.replaceAll("smb://[^@]+@", "smb://");

    // Try each substitution rule in reverse
    String[] pref_index = {"", "_02", "_03", "_04", "_05", "_06", "_07", "_08", "_09", "_10"};
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

    for (String s : pref_index) {
        String pathToReplace = prefs.getString("path_to_replace" + s, "").trim();
        String replacedWith = prefs.getString("replaced_with" + s, "").trim();

        if (!pathToReplace.isEmpty() && !replacedWith.isEmpty() && decoded.startsWith(replacedWith)) {
            // Reverse: replace the SMB prefix with the server-side prefix
            return pathToReplace + decoded.substring(replacedWith.length());
        }
    }

    return null; // No matching rule
}
```

### Jellyfin Item Search by Path (Reverse Lookup)
```java
/**
 * Searches Jellyfin for an episode item matching the given server-side file path.
 * Uses searchTerm with filename, then filters by full path match.
 */
public static void searchItemByPath(String serverUrl, String apiKey, String serverPath,
                                     Callback callback) {
    // Extract filename for search
    String filename = serverPath.substring(serverPath.lastIndexOf('/') + 1);
    // Remove file extension for broader search
    String searchName = filename.contains(".") ? filename.substring(0, filename.lastIndexOf('.')) : filename;

    String baseUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
    String url = baseUrl + "/Items?searchTerm=" + Uri.encode(searchName)
            + "&IncludeItemTypes=Episode&Fields=Path,MediaSources&Recursive=true&Limit=10";

    Request request = new Request.Builder()
            .url(url)
            .addHeader("Authorization", buildFullAuthHeader(apiKey))
            .build();

    getClient().newCall(request).enqueue(new okhttp3.Callback() {
        @Override
        public void onFailure(Call call, IOException e) {
            getMainHandler().post(() -> callback.onError("Search failed: " + e.getMessage()));
        }

        @Override
        public void onResponse(Call call, Response response) throws IOException {
            try {
                if (!response.isSuccessful() || response.body() == null) {
                    getMainHandler().post(() -> callback.onError("HTTP error"));
                    return;
                }
                String body = response.body().string();
                JsonObject root = JsonParser.parseString(body).getAsJsonObject();
                JsonArray items = root.getAsJsonArray("Items");
                if (items != null) {
                    for (JsonElement el : items) {
                        JsonObject item = el.getAsJsonObject();
                        String itemPath = item.has("Path") ? item.get("Path").getAsString() : "";
                        if (itemPath.equals(serverPath)) {
                            // Exact path match -- parse and return
                            String itemBody = item.toString();
                            ItemResult result = parseItemResponse(itemBody);
                            String itemId = item.get("Id").getAsString();
                            getMainHandler().post(() -> callback.onSuccess(
                                    result.path, result.positionTicks, result.title,
                                    result.durationTicks, result.seriesId));
                            return;
                        }
                    }
                }
                getMainHandler().post(() -> callback.onError("Episode not found by path"));
            } catch (Exception e) {
                getMainHandler().post(() -> callback.onError("Parse error: " + e.getMessage()));
            } finally {
                response.close();
            }
        }
    });
}
```

### Extended getNextUp with Full Metadata
```java
/**
 * Extended callback for getNextUp that returns full episode metadata for the Up Next screen.
 */
public interface NextUpDetailCallback {
    void onResult(String nextItemId, String seriesName, String episodeName,
                  int seasonNumber, int episodeNumber, String seriesId, String serverPath);
    void onNoNextEpisode();
}

public static void getNextUpWithDetails(String serverUrl, String apiKey, String seriesId,
                                         NextUpDetailCallback callback) {
    String baseUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
    String url = baseUrl + "/Shows/NextUp?seriesId=" + seriesId
            + "&limit=1&Fields=Path,MediaSources";

    Request request = new Request.Builder()
            .url(url)
            .addHeader("Authorization", buildFullAuthHeader(apiKey))
            .build();

    getClient().newCall(request).enqueue(new okhttp3.Callback() {
        @Override
        public void onFailure(Call call, IOException e) {
            getMainHandler().post(() -> callback.onNoNextEpisode());
        }

        @Override
        public void onResponse(Call call, Response response) throws IOException {
            try {
                if (!response.isSuccessful() || response.body() == null) {
                    getMainHandler().post(() -> callback.onNoNextEpisode());
                    return;
                }
                String body = response.body().string();
                JsonObject root = JsonParser.parseString(body).getAsJsonObject();
                JsonArray items = root.getAsJsonArray("Items");
                if (items != null && items.size() > 0) {
                    JsonObject item = items.get(0).getAsJsonObject();
                    String id = item.get("Id").getAsString();
                    String name = item.has("Name") ? item.get("Name").getAsString() : "";
                    String series = item.has("SeriesName") ? item.get("SeriesName").getAsString() : "";
                    int season = item.has("ParentIndexNumber") ? item.get("ParentIndexNumber").getAsInt() : 0;
                    int episode = item.has("IndexNumber") ? item.get("IndexNumber").getAsInt() : 0;
                    String sid = item.has("SeriesId") ? item.get("SeriesId").getAsString() : "";
                    // Extract path
                    String path = item.has("Path") ? item.get("Path").getAsString() : "";
                    if (path.isEmpty() && item.has("MediaSources")) {
                        JsonArray ms = item.getAsJsonArray("MediaSources");
                        if (ms.size() > 0) {
                            path = ms.get(0).getAsJsonObject().get("Path").getAsString();
                        }
                    }
                    final String fPath = path;
                    getMainHandler().post(() -> callback.onResult(id, series, name, season, episode, sid, fPath));
                } else {
                    getMainHandler().post(() -> callback.onNoNextEpisode());
                }
            } catch (Exception e) {
                getMainHandler().post(() -> callback.onNoNextEpisode());
            } finally {
                response.close();
            }
        }
    });
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| RenderScript for blur | Glide + wasabeef transformations | RenderScript deprecated API 31 (2021) | Use glide-transformations instead |
| Volley for HTTP | OkHttp | Already migrated in Phase 2 | All new API calls use OkHttp pattern |
| Open Jellyfin client for next ep | In-app Up Next screen | This phase | User stays in playback flow |

**Deprecated/outdated:**
- RenderScript: Deprecated in Android 12 (API 31). Do not use for blur.
- Glide v5: Too new, may have compatibility issues with Java 8 / API 28 target. Stick with v4.16.0.

## Open Questions

1. **Zidoo getPlayStatus path format with SMB credentials**
   - What we know: `doSubstitution()` embeds `user:pass@` in SMB URIs. Zidoo likely returns the path as it was given.
   - What's unclear: Whether Zidoo normalizes/decodes the path in its `getPlayStatus` response.
   - Recommendation: Implement credential stripping and URL decoding in reverse substitution. Test with actual Zidoo device and verify path format in getPlayStatus JSON output.

2. **Jellyfin Items search accuracy for reverse lookup**
   - What we know: `/Items` endpoint supports `searchTerm` but no direct path filter. Must search by filename then compare full paths.
   - What's unclear: Whether `searchTerm` on episode filenames is reliable (e.g., does it handle special characters, long filenames?).
   - Recommendation: Search by filename stem (no extension), filter results by exact path match. Include `IncludeItemTypes=Episode` to narrow results. If search returns nothing, try with a shorter search term (first 20 chars).

3. **Up Next Activity lifecycle when Zidoo is in foreground**
   - What we know: The existing app uses `onRestart->finish()` and `onStop`/`onDestroy` lifecycle patterns carefully because Zidoo player triggers onStop when it takes foreground.
   - What's unclear: Whether launching UpNextActivity between Zidoo exits and next launch will hit lifecycle edge cases.
   - Recommendation: UpNextActivity should be a simple Activity that finishes itself when launching Zidoo or canceling. It should not interact with the Zidoo HTTP API. Play activity manages all tracking state.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4 (already configured) |
| Config file | app/build.gradle (testImplementation 'junit:junit:4.+') |
| Quick run command | `./gradlew testDebugUnitTest --tests "com.jellyfintozidoo.*"` |
| Full suite command | `./gradlew testDebugUnitTest` |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| EPIS-01 | Path change detection triggers episode tracking reset | unit | `./gradlew testDebugUnitTest --tests "com.jellyfintozidoo.ReverseSubstitutionTest" -x` | No - Wave 0 |
| EPIS-02 | Reverse substitution resolves correct server path, search returns correct item | unit | `./gradlew testDebugUnitTest --tests "com.jellyfintozidoo.ReverseSubstitutionTest" -x` | No - Wave 0 |
| EPIS-03 | NextUp API response parsed correctly with full metadata | unit | `./gradlew testDebugUnitTest --tests "com.jellyfintozidoo.JellyfinApiTest" -x` | Partial (extend) |
| EPIS-04 | Season boundary handled by NextUp API (no client logic needed) | unit | `./gradlew testDebugUnitTest --tests "com.jellyfintozidoo.JellyfinApiTest" -x` | Partial (extend) |
| SETT-04 | Multiple substitution rules applied correctly | unit | `./gradlew testDebugUnitTest --tests "com.jellyfintozidoo.SubstitutionTest" -x` | No - Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew testDebugUnitTest --tests "com.jellyfintozidoo.*" -x`
- **Per wave merge:** `./gradlew testDebugUnitTest`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `app/src/test/java/com/jellyfintozidoo/ReverseSubstitutionTest.java` -- covers EPIS-01, EPIS-02 (reverse path substitution, credential stripping, URI decoding)
- [ ] `app/src/test/java/com/jellyfintozidoo/SubstitutionTest.java` -- covers SETT-04 (multi-slot forward + reverse substitution)
- [ ] Extend `JellyfinApiTest.java` -- covers EPIS-03, EPIS-04 (NextUp detail parsing, searchItemByPath response parsing)

## Sources

### Primary (HIGH confidence)
- Existing codebase: Play.java, JellyfinApi.java, SettingsActivity.java -- all patterns verified from source
- Jellyfin API `/Shows/NextUp` -- existing `getNextUp()` already implemented and working
- Jellyfin API `/Items/{id}` -- existing `getItem()` already implemented and working
- Glide v4 official docs (bumptech.github.io/glide) -- transformation API, download setup
- wasabeef/glide-transformations GitHub -- BlurTransformation(radius, sampling) API

### Secondary (MEDIUM confidence)
- [Jellyfin TypeScript SDK docs](https://typescript-sdk.jellyfin.org/interfaces/generated-client.ItemsApiGetItemsRequest.html) -- Items endpoint parameters (searchTerm, Fields, IncludeItemTypes)
- [Jellyfin API overview](https://jmshrv.com/posts/jellyfin-api/) -- NextUp API behavior
- [Android TV navigation docs](https://developer.android.com/training/tv/get-started/navigation) -- D-pad focus handling

### Tertiary (LOW confidence)
- Zidoo getPlayStatus path format with embedded SMB credentials -- needs device verification
- Jellyfin searchTerm accuracy for filename-based episode lookup -- needs testing

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Glide is well-established, versions verified against Maven Central
- Architecture: HIGH - Patterns follow existing codebase conventions exactly
- Pitfalls: HIGH - Derived from actual code analysis of doSubstitution(), startProgressPoller(), and lifecycle patterns
- Reverse lookup: MEDIUM - Jellyfin Items search API confirmed but path-matching approach needs validation

**Research date:** 2026-03-14
**Valid until:** 2026-04-14 (stable domain, no fast-moving dependencies)
