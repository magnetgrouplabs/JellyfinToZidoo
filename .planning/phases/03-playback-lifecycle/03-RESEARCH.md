# Phase 3: Playback Lifecycle - Research

**Researched:** 2026-03-13
**Domain:** Jellyfin playback session reporting + Zidoo player status polling
**Confidence:** HIGH

## Summary

Phase 3 implements bidirectional playback state sync: reporting playback events (start, progress, stop) to Jellyfin's session API, polling the Zidoo player's local HTTP API for current position during playback, applying the 90% watched threshold, and relaunching the originating Jellyfin client afterward.

The Jellyfin server exposes three POST endpoints under `/Sessions/Playing` that accept JSON bodies with `ItemId` and `PositionTicks` as the key fields. The Zidoo player exposes a local REST API at `localhost:9529` that returns current playback position in milliseconds. The existing `JellyfinApi.java` already has the OkHttp client, auth header builder, and ticks-to-ms conversion. A reverse `msToTicks()` helper is needed. The `onActivityResult()` hook in `Play.java` is stubbed and ready for implementation.

**Primary recommendation:** Extend `JellyfinApi.java` with three playback reporting methods and one `markAsWatched` method. Add a `msToTicks()` static helper. Implement a `ScheduledExecutorService`-based poller in `Play.java` that polls Zidoo's local API during playback and reports progress to Jellyfin. Wire `onActivityResult()` to report stopped + conditionally mark watched + relaunch caller.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- Follow PlexToZidoo pattern: `startActivityForResult()` -> `onActivityResult()` for final position
- Zidoo player returns `position` extra in the result intent (milliseconds)
- Additionally: poll Zidoo's `localhost:9529/ZidooVideoPlay/getPlayStatus` during playback for periodic progress
- Start: POST `/Sessions/Playing` immediately after launching Zidoo player
- Progress: POST `/Sessions/Playing/Progress` periodically during playback (poll Zidoo's local API for current position, convert ms to ticks, report to Jellyfin)
- Stopped: POST `/Sessions/Playing/Stopped` in `onActivityResult()` with final position from Zidoo player result
- Follow PlexToZidoo pattern exactly: `position > (duration * 0.9)` -> mark as watched
- Mark watched via Jellyfin API (POST `/Users/{userId}/PlayedItems/{itemId}`)
- Otherwise: save resume position via the stopped report (`PositionTicks` in the stopped payload)
- Need to capture `RunTimeTicks` (duration) from Jellyfin item metadata -- extend existing `getItem()` response parsing
- Detect originating Jellyfin client automatically from the incoming intent's calling package
- Capture `getCallingActivity()` or `getCallingPackage()` during `onStart()`, store for relaunch
- After playback reporting completes, relaunch the detected client via `startActivity()`
- If caller detection fails, fall back to just finishing the activity (user returns to Android TV home)

### Claude's Discretion
- Progress polling interval (suggest ~10-15 seconds)
- Whether to use a background thread, Handler, or ScheduledExecutor for polling during playback
- PlaySessionId generation strategy for Jellyfin session reporting
- Error handling for Jellyfin API reporting failures (silent fail vs toast -- follow PlexToZidoo's toast pattern)
- Whether to show playback reporting status on debug screen

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| PLAY-01 | App reports playback start to Jellyfin (POST /Sessions/Playing) | Jellyfin PlaystateController confirms POST /Sessions/Playing with PlaybackStartInfo body (ItemId required) |
| PLAY-02 | App reports playback progress periodically (POST /Sessions/Playing/Progress) | Jellyfin PlaybackProgressInfo body with ItemId + PositionTicks; Zidoo getPlayStatus returns currentPosition in ms |
| PLAY-03 | App reports playback stopped with final position (POST /Sessions/Playing/Stopped) | Jellyfin PlaybackStopInfo body with ItemId + PositionTicks; Zidoo returns position in onActivityResult intent extra |
| PLAY-04 | Resume position written back to Jellyfin on playback stop | PositionTicks in PlaybackStopInfo automatically updates server-side resume position |
| PLAY-05 | Media marked as watched when >=90% played | POST /UserPlayedItems/{itemId} endpoint confirmed; need RunTimeTicks from item metadata for threshold |
| PLAY-06 | App relaunches originating Jellyfin client after playback ends | Android getCallingPackage() + startActivity() pattern; PlexToZidoo lines 867-888 as template |
</phase_requirements>

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| OkHttp | 4.12.0 | HTTP client for Jellyfin API calls | Already in project, singleton pattern established |
| Gson | 2.8.6 | JSON parsing for Zidoo + Jellyfin responses | Already in project, used by JellyfinApi |
| Android ScheduledExecutorService | JDK 8 | Background polling thread for progress reporting | Lightweight, no extra dependency, clean shutdown |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Android Handler/Looper | Platform | Post results to main thread | Already used in JellyfinApi for callbacks |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| ScheduledExecutorService | Handler.postDelayed | Handler is simpler but ScheduledExecutor is cleaner for periodic tasks with shutdown |
| ScheduledExecutorService | Timer/TimerTask | Timer is older API, ScheduledExecutor preferred since Java 5 |

## Architecture Patterns

### Recommended Changes to Existing Files
```
app/src/main/java/com/jellyfintozidoo/
  JellyfinApi.java        # ADD: reportPlaybackStart(), reportPlaybackProgress(),
                           #      reportPlaybackStopped(), markAsWatched(), msToTicks()
                           # MODIFY: parseItemResponse() to extract RunTimeTicks
                           # MODIFY: ItemResult to include durationTicks field
                           # MODIFY: Callback interface to include durationTicks parameter
  Play.java               # ADD: Zidoo status poller (ScheduledExecutorService)
                           # ADD: onActivityResult() implementation
                           # ADD: caller package capture in onStart()
                           # ADD: client relaunch logic
app/src/test/java/com/jellyfintozidoo/
  JellyfinApiTest.java    # ADD: tests for msToTicks, RunTimeTicks parsing
  PlaybackReportingTest.java  # NEW: tests for JSON body construction
```

### Pattern 1: Jellyfin Playback Session Reporting
**What:** POST JSON bodies to three session endpoints with auth header
**When to use:** Start, progress, and stop of Zidoo player playback
**Example:**
```java
// POST /Sessions/Playing - Start report
// Source: Jellyfin PlaystateController.cs
JsonObject body = new JsonObject();
body.addProperty("ItemId", itemId);
body.addProperty("PlaySessionId", playSessionId);
body.addProperty("CanSeek", true);
body.addProperty("PlayMethod", "DirectPlay");
body.addProperty("PositionTicks", 0);

// POST /Sessions/Playing/Progress - Progress report
JsonObject body = new JsonObject();
body.addProperty("ItemId", itemId);
body.addProperty("PlaySessionId", playSessionId);
body.addProperty("PositionTicks", positionTicks);
body.addProperty("IsPaused", false);
body.addProperty("CanSeek", true);

// POST /Sessions/Playing/Stopped - Stop report
JsonObject body = new JsonObject();
body.addProperty("ItemId", itemId);
body.addProperty("PlaySessionId", playSessionId);
body.addProperty("PositionTicks", positionTicks);
```

### Pattern 2: Zidoo Local API Polling
**What:** HTTP GET to Zidoo's local REST API for current playback position
**When to use:** During active playback, on a periodic schedule
**Example:**
```java
// GET http://localhost:9529/ZidooVideoPlay/getPlayStatus
// Source: Zidoo API docs (apidoc.zidoo.tv)
// Response JSON structure:
// {
//   "status": 200,
//   "video": {
//     "status": "...",
//     "title": "...",
//     "path": "...",
//     "currentPosition": 123456,   // milliseconds
//     "duration": 7200000,          // milliseconds
//     "width": 1920,
//     "height": 1080,
//     ...
//   }
// }
// Extract: video.currentPosition (ms) -> convert to ticks for Jellyfin
```

### Pattern 3: Tick Conversion
**What:** Convert between Jellyfin ticks (100ns units) and Zidoo milliseconds
**When to use:** Every interaction between Zidoo position and Jellyfin API
**Example:**
```java
// Already exists: ticksToMs(long ticks) = ticks / 10000
// Need to add: msToTicks(long ms) = ms * 10000
public static long msToTicks(long ms) {
    return ms * TICKS_PER_MS;  // TICKS_PER_MS = 10000
}
```

### Pattern 4: PlaySessionId Generation
**What:** Unique session identifier for Jellyfin playback tracking
**When to use:** Generated once per playback session, sent with all three reports
**Example:**
```java
// Simple UUID-based approach
String playSessionId = java.util.UUID.randomUUID().toString().replace("-", "");
// Jellyfin expects a string identifier, no specific format required
```

### Anti-Patterns to Avoid
- **Polling on the main thread:** Zidoo HTTP polling MUST run on a background thread; OkHttp async or ScheduledExecutor
- **Not shutting down the executor:** MUST call `executor.shutdownNow()` in `onActivityResult()` and `onDestroy()` to prevent leaked threads
- **Reporting after activity destroyed:** Check `isFinishing()` before posting to Jellyfin on callbacks
- **Hardcoding localhost IP:** Use `127.0.0.1` or `localhost` -- both work since the Zidoo player runs on the same device

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| HTTP client | Custom HTTP code | OkHttp (already in project) | Connection pooling, async, error handling |
| JSON construction | String concatenation | Gson JsonObject | Escaping, nested objects, type safety |
| Background scheduling | Thread + sleep loop | ScheduledExecutorService | Clean shutdown, exception handling, reuse |
| UUID generation | Custom ID scheme | java.util.UUID | Standard, guaranteed uniqueness |

## Common Pitfalls

### Pitfall 1: Zidoo getPlayStatus Returns Error When Not Playing
**What goes wrong:** Polling the Zidoo API when player hasn't fully started or has already exited returns non-200 or missing `video` object
**Why it happens:** Race condition between player launch and poller start, or player exits before poller stops
**How to avoid:** Check `status == 200` and `video` object exists before reading `currentPosition`; treat errors as "player not ready, skip this cycle"
**Warning signs:** NullPointerException or JsonParseException in polling loop

### Pitfall 2: onActivityResult Position is 0 on Back Press
**What goes wrong:** User presses Back to exit Zidoo player -- `resultCode` may be RESULT_CANCELED and position data may be missing
**Why it happens:** Zidoo only returns RESULT_OK with position when playback ends normally or user explicitly stops
**How to avoid:** If `resultCode != RESULT_OK` or position is 0, use the last known position from the most recent successful poll as fallback
**Warning signs:** Resume position resets to 0 in Jellyfin after user backs out of playback

### Pitfall 3: Callback Interface Change Breaks Existing Code
**What goes wrong:** Adding `durationTicks` to the Callback.onSuccess() signature breaks the existing call site in Play.java
**Why it happens:** Java interfaces require all implementations to match
**How to avoid:** Add `durationTicks` as a new parameter to the existing Callback.onSuccess(), update the single call site in Play.java simultaneously
**Warning signs:** Compilation error at Play.java line 714

### Pitfall 4: getCallingPackage() Returns Null
**What goes wrong:** `getCallingPackage()` returns null when the activity wasn't started with `startActivityForResult()`
**Why it happens:** Jellyfin clients use `startActivity()` not `startActivityForResult()` to launch ACTION_VIEW intents
**How to avoid:** Use `getReferrer()` as a fallback (returns the URI of the referring app on Android 5.1+), or detect Jellyfin client packages from intent components. If all detection fails, fall back to finishing the activity.
**Warning signs:** Client relaunch never works, user always returns to Android TV home

### Pitfall 5: Ticks Overflow
**What goes wrong:** Multiplying large ms values by 10000 overflows int
**Why it happens:** A 3-hour movie at 10,800,000 ms * 10,000 = 108,000,000,000 which exceeds Integer.MAX_VALUE
**How to avoid:** Use `long` for ALL tick arithmetic, never `int`. The existing `TICKS_PER_MS` and `ticksToMs` already use `long`.
**Warning signs:** Negative or wildly wrong position values

### Pitfall 6: Auth Header Format for POST Requests
**What goes wrong:** POST requests to Jellyfin fail with 401
**Why it happens:** Jellyfin session endpoints may require the full MediaBrowser auth header (with Client, Device, DeviceId, Version) not just the Token
**How to avoid:** Use the same auth header format as `authenticate()` method but include the access token: `MediaBrowser Client="JellyfinToZidoo", Device="Zidoo", DeviceId="jellyfintozidoo", Version="1.0.0", Token="{token}"`
**Warning signs:** 401 responses on playback reporting endpoints

## Code Examples

### Playback Start Report
```java
// Source: Jellyfin PlaystateController.cs (POST /Sessions/Playing)
public static void reportPlaybackStart(String serverUrl, String apiKey,
        String itemId, String playSessionId, SimpleCallback callback) {
    String baseUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
    String url = baseUrl + "/Sessions/Playing";

    JsonObject body = new JsonObject();
    body.addProperty("ItemId", itemId);
    body.addProperty("PlaySessionId", playSessionId);
    body.addProperty("CanSeek", true);
    body.addProperty("PlayMethod", "DirectPlay");
    body.addProperty("PositionTicks", 0);

    Request request = new Request.Builder()
            .url(url)
            .addHeader("Authorization", buildAuthHeader(apiKey))
            .addHeader("Content-Type", "application/json")
            .post(RequestBody.create(body.toString(), MediaType.get("application/json")))
            .build();

    // Use existing async pattern with getClient().newCall(request).enqueue(...)
}
```

### Zidoo Status Polling
```java
// Source: Zidoo API (apidoc.zidoo.tv)
private void pollZidooPlayStatus() {
    Request request = new Request.Builder()
            .url("http://127.0.0.1:9529/ZidooVideoPlay/getPlayStatus")
            .build();

    // Synchronous call is OK here -- runs on ScheduledExecutor thread
    try (Response response = getClient().newCall(request).execute()) {
        if (response.isSuccessful() && response.body() != null) {
            JsonObject root = JsonParser.parseString(response.body().string()).getAsJsonObject();
            if (root.has("video")) {
                JsonObject video = root.getAsJsonObject("video");
                long currentPositionMs = video.get("currentPosition").getAsLong();
                long positionTicks = JellyfinApi.msToTicks(currentPositionMs);
                // Report progress to Jellyfin
                lastKnownPositionMs = currentPositionMs;
            }
        }
    } catch (Exception e) {
        // Player not ready or exited -- skip this cycle
        Log.w(TAG, "Zidoo poll failed: " + e.getMessage());
    }
}
```

### onActivityResult Implementation
```java
// Source: PlexToZidoo lines 833-889 (adapted for Jellyfin)
@Override
protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    // Stop the progress poller
    if (progressPoller != null) {
        progressPoller.shutdownNow();
        progressPoller = null;
    }

    // Get final position
    long finalPositionMs = 0;
    if (resultCode == Activity.RESULT_OK && requestCode == 98 && data != null) {
        finalPositionMs = data.getIntExtra("position", 0);
    }
    // Fallback to last polled position if Zidoo didn't return one
    if (finalPositionMs <= 0) {
        finalPositionMs = lastKnownPositionMs;
    }

    long finalPositionTicks = JellyfinApi.msToTicks(finalPositionMs);

    // Report playback stopped
    JellyfinApi.reportPlaybackStopped(serverUrl, accessToken, jellyfinItemId,
            playSessionId, finalPositionTicks, new JellyfinApi.SimpleCallback() {
        @Override
        public void onSuccess(String msg) {
            // Check 90% threshold
            if (durationTicks > 0 && finalPositionTicks > (long)(durationTicks * 0.9)) {
                JellyfinApi.markAsWatched(serverUrl, accessToken, userId,
                        jellyfinItemId, new JellyfinApi.SimpleCallback() { ... });
            }
            relaunchCallerOrFinish();
        }
        @Override
        public void onError(String error) {
            Toast.makeText(getApplicationContext(),
                    "Couldn't update progress or watched status", Toast.LENGTH_LONG).show();
            relaunchCallerOrFinish();
        }
    });
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Volley HTTP client (PlexToZidoo) | OkHttp (already migrated) | Phase 2 | All new API calls use OkHttp |
| Plex scrobble endpoint | Jellyfin /Sessions/Playing/* | Phase 3 (this phase) | Different JSON body format, three endpoints instead of one |
| Plex progress endpoint | Jellyfin /Sessions/Playing/Progress | Phase 3 | Ticks instead of ms, POST instead of GET |

## Open Questions

1. **getCallingPackage() behavior with Jellyfin clients**
   - What we know: PlexToZidoo hardcodes `com.plexapp.android` for relaunch; Jellyfin has multiple clients (Findroid, jellyfin-androidtv, Swiftfin, etc.)
   - What's unclear: Whether Jellyfin clients use `startActivity()` or `startActivityForResult()` for ACTION_VIEW intents (affects `getCallingPackage()`)
   - Recommendation: Try `getCallingPackage()` first, fall back to `getReferrer()`, then to finishing the activity. Test on device with actual Jellyfin client.

2. **Zidoo getPlayStatus availability timing**
   - What we know: The API is at `localhost:9529` and returns video.currentPosition in ms
   - What's unclear: How quickly after player launch the endpoint becomes responsive
   - Recommendation: Add a brief initial delay (2-3 seconds) before starting the poller, and handle non-200 responses gracefully.

3. **Auth header format for session endpoints**
   - What we know: `buildAuthHeader()` uses `MediaBrowser Token="..."` format. The authenticate endpoint uses the full Client/Device/DeviceId/Version format.
   - What's unclear: Whether the simple Token-only format works for POST /Sessions/Playing endpoints
   - Recommendation: Start with simple Token format (it works for GET /Items). If 401 errors occur, switch to full MediaBrowser header with Token.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4 |
| Config file | app/build.gradle (testImplementation 'junit:junit:4.+') |
| Quick run command | `./gradlew test --tests "com.jellyfintozidoo.*" -x lint` |
| Full suite command | `./gradlew test -x lint` |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| PLAY-01 | Start report JSON body construction | unit | `./gradlew test --tests "com.jellyfintozidoo.PlaybackReportingTest.startReport*" -x lint` | Wave 0 |
| PLAY-02 | Progress report JSON body + ms-to-ticks | unit | `./gradlew test --tests "com.jellyfintozidoo.PlaybackReportingTest.progressReport*" -x lint` | Wave 0 |
| PLAY-03 | Stop report JSON body construction | unit | `./gradlew test --tests "com.jellyfintozidoo.PlaybackReportingTest.stopReport*" -x lint` | Wave 0 |
| PLAY-04 | PositionTicks included in stop report | unit | `./gradlew test --tests "com.jellyfintozidoo.PlaybackReportingTest.stopReport*" -x lint` | Wave 0 |
| PLAY-05 | 90% threshold calculation | unit | `./gradlew test --tests "com.jellyfintozidoo.PlaybackReportingTest.watchedThreshold*" -x lint` | Wave 0 |
| PLAY-05 | RunTimeTicks parsed from item response | unit | `./gradlew test --tests "com.jellyfintozidoo.JellyfinApiTest.parseItemResponse*Duration*" -x lint` | Wave 0 |
| PLAY-06 | Client relaunch logic | manual-only | N/A -- requires real device with Jellyfin client | N/A |

### Sampling Rate
- **Per task commit:** `./gradlew test --tests "com.jellyfintozidoo.*" -x lint`
- **Per wave merge:** `./gradlew test -x lint`
- **Phase gate:** Full suite green + on-device E2E test

### Wave 0 Gaps
- [ ] `app/src/test/java/com/jellyfintozidoo/PlaybackReportingTest.java` -- covers PLAY-01 through PLAY-05
- [ ] Add msToTicks tests to existing `TickConversionTest.java`
- [ ] Add RunTimeTicks parsing tests to existing `JellyfinApiTest.java`

## Sources

### Primary (HIGH confidence)
- Jellyfin PlaystateController.cs (GitHub master) -- confirmed POST /Sessions/Playing, /Progress, /Stopped endpoints with PlaybackStartInfo, PlaybackProgressInfo, PlaybackStopInfo bodies; POST /UserPlayedItems/{itemId} for mark-as-watched
- Jellyfin TypeScript SDK PlaybackProgressInfo interface -- all fields are optional; ItemId, PositionTicks, PlaySessionId, IsPaused, CanSeek are the key fields
- Zidoo API docs (apidoc.zidoo.tv) -- confirmed getPlayStatus response structure with video.currentPosition and video.duration in milliseconds
- Existing codebase: JellyfinApi.java, Play.java -- confirmed integration points, existing patterns, auth header format

### Secondary (MEDIUM confidence)
- PlexToZidoo onActivityResult() lines 833-889 -- template for stop reporting + 90% threshold + client relaunch pattern
- wizmo2/zidoo-player Home Assistant integration -- confirmed Zidoo API field names (currentPosition, duration, path, status)

### Tertiary (LOW confidence)
- getCallingPackage() behavior with Jellyfin clients -- needs on-device testing to confirm

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- all libraries already in project, APIs confirmed via source code
- Architecture: HIGH -- follows established patterns in codebase + confirmed API contracts
- Pitfalls: HIGH -- based on direct code analysis and API documentation review

**Research date:** 2026-03-13
**Valid until:** 2026-04-13 (stable APIs, unlikely to change)
