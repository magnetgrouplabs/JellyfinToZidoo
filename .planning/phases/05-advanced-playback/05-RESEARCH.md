# Phase 5: Advanced Playback - Research

**Researched:** 2026-03-14
**Domain:** Jellyfin Intro Skipper API, Zidoo REST API (seek/track control), Jellyfin MediaStreams, Android overlay feasibility, settings import/export
**Confidence:** HIGH

## Summary

Phase 5 extends the existing progress poller and Zidoo REST API integration to add intro/credit skipping, audio/subtitle track passthrough, and settings portability. The research confirms all required APIs exist and are well-documented: the Intro Skipper plugin exposes `/Episode/{id}/IntroSkipperSegments` returning both Introduction and Credits timestamps, the Zidoo REST API supports `seekTo`, `setAudio`, and `setSubtitle` endpoints, and the Jellyfin streaming URL already contains `AudioStreamIndex` and `SubtitleStreamIndex` query parameters that can be parsed from the intercepted intent.

The user's preference for a Netflix-style "Skip Intro" button overlay is technically possible via Android's `SYSTEM_ALERT_WINDOW` permission and `TYPE_APPLICATION_OVERLAY` window, but requires ADB-granted permission on Android TV devices (no settings UI available). This adds friction and complexity. The recommended approach is auto-skip with toggle as the primary path, with the overlay button as a stretch goal if time permits.

**Primary recommendation:** Extend the existing progress poller with intro/credit range checks, add a new JellyfinApi method for Intro Skipper timestamps, parse MediaStreams from the existing getItem response for track selection, and set tracks via Zidoo REST API after player launch. Settings import/export already works -- just needs auto-login after import and exclusion of access_token/user_id from export.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- Auto-skip intros silently using Zidoo `seekTo` API when poller detects position in intro range
- Settings toggle: "Skip intros" (default on) -- when disabled, intros play normally
- Every play -- skip intros on every episode, not just first watch
- Silent no-op when Intro Skipper plugin isn't installed or has no data for an episode
- **Disarm on manual seek**: track `introSkipArmed = true` per episode. If user manually seeks to any position before `introEnd`, set `introSkipArmed = false` -- intro skip disabled for that episode. Resets to `true` on new episode. Only trigger intro skip during natural forward playback, NOT on resume into intro range
- When credits timestamp is reached, trigger Up Next flow early: mark episode watched -> stop Zidoo player -> launch Up Next countdown screen (reuses Phase 4 Up Next infrastructure)
- Settings toggle: "Skip credits" (separate from intro skip toggle) -- when disabled, credits play normally and standard end-of-file Up Next triggers
- Same "disarm on manual seek" logic as intro skip -- if user seeks before `outroStart`, credit skip is disarmed for that episode
- TV shows only -- credit skip does not trigger for movies
- Pass audio/subtitle track selection from Jellyfin client intent to Zidoo player via Zidoo REST API (`setAudio?index=N`, `setSubtitle?index=N`)
- Set tracks once after Zidoo player launches -- Zidoo remembers per-file track selections on subsequent plays
- Index-based mapping: parse Jellyfin MediaStreams array, filter by Type (Audio/Subtitle), map positional index to Zidoo's 0-based audio index and 1-based subtitle index
- Fallback chain: Jellyfin client intent selection -> Jellyfin item default streams (IsDefault/IsForced) -> don't set anything (let Zidoo pick)
- No settings toggle for audio/subtitle -- always attempt passthrough, silent no-op if no data available
- Verify and fix up existing PlexToZidoo export/import code (already functional in SettingsActivity.java)
- Keep .txt file extension (PlexToZidoo convention)
- Export password only -- access_token and user_id are NOT exported
- After import, automatically call authenticate() with restored credentials to get a fresh token
- Export path remains Downloads/JellyfinToZidooSettings.txt

### Claude's Discretion
- Exact timing of setAudio/setSubtitle call after Zidoo player launch (delay needed for player initialization)
- seekTo call timing and retry logic if Zidoo player hasn't started yet
- How to detect "manual seek" vs "natural playback" for disarm logic (likely position jump detection in poller)
- Whether to fetch intro timestamps once at episode start or cache them
- Intro Skipper API endpoint choice (research which endpoint is standard)
- Settings screen layout for new toggles (placement within existing preferences hierarchy)

### Deferred Ideas (OUT OF SCOPE)
- Configurable intro skip button hold duration -- hardcode behavior for now
- Per-series intro skip preferences -- global toggle only
- External subtitle file selection from Jellyfin -- only embedded streams for now
- NFS path support (v2 requirement PROT-01)
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| ADVP-01 | App skips intros using Jellyfin Intro Skipper plugin data | Intro Skipper API documented (IntroSkipperSegments endpoint), seekTo API confirmed, disarm-on-seek pattern researched |
| ADVP-02 | App stops or advances at credits using Intro Skipper outro data | Same IntroSkipperSegments endpoint returns Credits object, triggers existing handleEpisodeCompleted/launchUpNext flow |
| ADVP-03 | App passes audio stream selection to Zidoo player | Zidoo setAudio API confirmed (0-based index), Jellyfin streaming URL contains AudioStreamIndex, MediaStreams array available in getItem response |
| ADVP-04 | App passes subtitle stream selection to Zidoo player | Zidoo setSubtitle API confirmed (1-based index, 0=off), Jellyfin streaming URL contains SubtitleStreamIndex, MediaStreams available |
| SETT-08 | User can import/export settings as JSON | Existing export/import code functional, needs auto-login after import and token exclusion from export |
</phase_requirements>

## Standard Stack

### Core (Already in Project)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| OkHttp | 4.12.0 | HTTP client for Zidoo REST API and Jellyfin API | Already used throughout project |
| Gson | 2.8.6 | JSON parsing for API responses | Already used throughout project |
| AndroidX Preference | 1.1.1 | Settings UI (SwitchPreference for new toggles) | Already used for all settings |

### No New Dependencies Required
All Phase 5 features use existing libraries. No new dependencies needed.

## Architecture Patterns

### Recommended Changes to Existing Structure
```
app/src/main/java/com/jellyfintozidoo/
  Play.java              # Extend poller: intro/credit range checks, track setting
  JellyfinApi.java       # Add: getIntroSkipperSegments(), parseMediaStreams()
  SettingsActivity.java   # Add: auto-login after import, token exclusion from export
app/src/main/res/xml/
  root_preferences.xml    # Add: skip_intros and skip_credits SwitchPreference entries
app/src/test/java/com/jellyfintozidoo/
  IntroSkipperApiTest.java  # New: parsing tests for IntroSkipperSegments response
  MediaStreamParsingTest.java  # New: parsing tests for MediaStreams array
```

### Pattern 1: Intro Skipper API Integration
**What:** Fetch intro/credit timestamps from the Intro Skipper plugin, store per-episode, check during polling
**When to use:** Once per episode start (or on first poll after Zidoo starts playing)

**API Endpoint:**
```
GET /Episode/{itemId}/IntroSkipperSegments
Authorization: MediaBrowser Token="{accessToken}"
```

**Response Format (verified from intro-skipper/intro-skipper repo):**
```json
{
  "Introduction": {
    "EpisodeId": "1e3cf41f1ae051f68ab56980f937f745",
    "Valid": true,
    "IntroStart": 145.792,
    "IntroEnd": 228.497,
    "ShowSkipPromptAt": 140.792,
    "HideSkipPromptAt": 155.792
  },
  "Credits": {
    "EpisodeId": "1e3cf41f1ae051f68ab56980f937f745",
    "Valid": true,
    "IntroStart": 1313.264,
    "IntroEnd": 1408.264,
    "ShowSkipPromptAt": 1308.264,
    "HideSkipPromptAt": 1323.264
  }
}
```

Notes:
- All timestamps in **seconds** (not milliseconds). Must convert to ms for Zidoo seekTo.
- Returns 404 if plugin not installed or no data for episode -- treat as silent no-op.
- Field names are `IntroStart`/`IntroEnd` for BOTH intro and credits sections (not `OutroStart`/`OutroEnd`).
- The `Credits` key contains the outro/credits data. The `Introduction` key contains intro data.
- **Confidence: HIGH** -- verified via multiple sources including DeepWiki and Findroid GitHub issue.

**Recommendation (Claude's Discretion):** Use `/Episode/{id}/IntroSkipperSegments` as the primary endpoint. It returns both intro and credits in a single request, reducing API calls. Fetch once when episode starts playing (after getItem succeeds), store in instance variables. No caching across episodes needed.

### Pattern 2: Seek and Disarm Logic
**What:** Auto-skip intros/credits with manual seek disarm
**Implementation in poller:**

```java
// Per-episode state variables
private boolean introSkipArmed = true;
private boolean creditSkipArmed = true;
private long introStartMs = -1, introEndMs = -1;
private long creditStartMs = -1, creditEndMs = -1;
private long lastPollPositionMs = -1;

// In poll callback, after reading currentPositionMs:
// 1. Detect manual seek (position jump)
long positionDelta = currentPositionMs - lastPollPositionMs;
boolean likelyManualSeek = lastPollPositionMs >= 0
    && (positionDelta < -3000 || positionDelta > 30000);
// Negative jump or large forward jump = manual seek

// 2. If manual seek detected, check disarm conditions
if (likelyManualSeek) {
    if (introSkipArmed && currentPositionMs < introEndMs) {
        introSkipArmed = false; // Disarm intro skip
    }
    if (creditSkipArmed && currentPositionMs < creditStartMs) {
        creditSkipArmed = false; // Disarm credit skip
    }
}

// 3. Check intro range
if (introSkipArmed && introStartMs >= 0
    && currentPositionMs >= introStartMs && currentPositionMs < introEndMs) {
    // Seek past intro
    seekZidoo(introEndMs);
    introSkipArmed = false; // Prevent re-triggering
}

// 4. Check credit range
if (creditSkipArmed && creditStartMs >= 0
    && currentPositionMs >= creditStartMs) {
    // Trigger Up Next flow (same as existing end-of-episode handling)
    upNextTriggered = true;
    // ... handleEpisodeCompleted() flow
}

lastPollPositionMs = currentPositionMs;
```

**Key design decisions:**
- Detect manual seek via position delta: if position jumps backward or forward >30s between polls (10s interval), treat as manual seek. The 30s threshold accounts for normal playback + polling jitter.
- Do NOT trigger intro skip on resume (when lastPollPositionMs is -1, skip the seek check on the first poll -- only start checking after we have a baseline position).
- Reset armed flags on new episode (when `nowPlayingPath` changes).

### Pattern 3: Audio/Subtitle Track Passthrough
**What:** Extract track selection from Jellyfin streaming URL and set via Zidoo REST API
**When to use:** After Zidoo player launches, with a delay for player initialization

**Step 1: Parse from streaming URL:**
The Jellyfin streaming URL intercepted by the app looks like:
```
http://server:8096/Videos/{uuid}/stream?static=true&MediaSourceId={id}&AudioStreamIndex=1&SubtitleStreamIndex=3&api_key={token}
```

Extract `AudioStreamIndex` and `SubtitleStreamIndex` query parameters from the intent data URL.

**Step 2: Map Jellyfin index to Zidoo index:**
- Jellyfin `AudioStreamIndex` is the index within ALL MediaStreams (including video). Need to count only Audio-type streams to get the Zidoo audio index (0-based).
- Jellyfin `SubtitleStreamIndex` similarly. Zidoo subtitle index is 1-based (0 = off).
- Example: MediaStreams = [Video(0), Audio(1), Audio(2), Subtitle(3), Subtitle(4)]
  - Jellyfin AudioStreamIndex=2 -> 2nd audio stream -> Zidoo audio index 1 (0-based among audio tracks)
  - Jellyfin SubtitleStreamIndex=3 -> 1st subtitle -> Zidoo subtitle index 1 (1-based among subtitle tracks)

**Step 3: Set via Zidoo REST API:**
```
GET http://127.0.0.1:9529/ZidooVideoPlay/setAudio?index={0-based}
GET http://127.0.0.1:9529/ZidooVideoPlay/setSubtitle?index={1-based, 0=off}
```

**Timing (Claude's Discretion recommendation):** Wait for first successful `getPlayStatus` poll (confirms player is running), then add ~500ms delay before sending setAudio/setSubtitle. Add retry logic: if the call fails, retry once after 1s. Do not retry more than once -- Zidoo will remember tracks on subsequent plays.

**Fallback (if no URL params):** Parse MediaStreams from the getItem response, find streams with `IsDefault: true` or `IsForced: true`, and use those indices. This handles the case where Jellyfin client didn't include index params in the URL.

### Pattern 4: Zidoo seekTo API
**What:** Seek to a position in the currently playing video
**Endpoint:**
```
GET http://127.0.0.1:9529/ZidooVideoPlay/seekTo?positon={ms}
```
**IMPORTANT:** The parameter name is `positon` (missing an 'i') -- this is a real typo in the Zidoo API, not a documentation error.

**Timing:** The seekTo call happens during the normal poll cycle when position is detected in intro range. Since the poller already confirms the player is running (it read currentPosition), no additional startup delay is needed.

### Anti-Patterns to Avoid
- **Don't poll Intro Skipper API repeatedly:** Fetch timestamps once per episode, not on every poll cycle.
- **Don't use IntroTimestamps/v1 endpoint:** Use IntroSkipperSegments instead -- it returns both intro AND credits in one call.
- **Don't hardcode track indices:** Always parse from URL params or MediaStreams; never assume audio is index 0.
- **Don't set tracks before player is confirmed running:** Wait for first successful getPlayStatus.
- **Don't trigger intro skip on first poll:** Need baseline position first to distinguish resume-into-intro-range from natural playback into intro.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Intro detection | Local audio fingerprinting | Intro Skipper plugin API | Server-side analysis is orders of magnitude better, already done |
| JSON parsing | Manual string parsing | Gson (already in project) | Edge cases, escaping, nested objects |
| HTTP requests | HttpURLConnection | OkHttp (already in project) | Connection pooling, timeouts, async callbacks |
| Overlay window | Custom drawing framework | Android WindowManager TYPE_APPLICATION_OVERLAY | Standard Android API for drawing over other apps |

## Common Pitfalls

### Pitfall 1: Zidoo API "positon" Typo
**What goes wrong:** API call silently fails or returns 404 because `position` is spelled correctly.
**Why it happens:** Zidoo firmware has a typo in the endpoint parameter name.
**How to avoid:** Always use `positon` (without second 'i'): `seekTo?positon={ms}`
**Warning signs:** seekTo returns error but getPlayStatus works fine.

### Pitfall 2: Zidoo Subtitle Index Off-by-One
**What goes wrong:** Wrong subtitle selected or subtitles disabled unexpectedly.
**Why it happens:** Zidoo uses 1-based indexing for subtitles (0 = off) but 0-based for audio.
**How to avoid:** Always add 1 to the positional subtitle index when calling setSubtitle. Index 0 means "no subtitles."
**Warning signs:** First subtitle track in a file cannot be selected; selecting any track gets the wrong one.

### Pitfall 3: Intro Skip Triggers on Resume
**What goes wrong:** User resumes an episode partway through the intro and gets immediately seeked past it.
**Why it happens:** Resume position falls within intro range, and the skip logic fires on the first poll.
**How to avoid:** Skip the intro range check on the very first poll cycle (when lastPollPositionMs is -1). Only check after we have two consecutive position readings to establish natural forward playback.
**Warning signs:** Resuming any episode that was paused during the intro causes an unexpected seek.

### Pitfall 4: Zidoo Track Setting Before Player Ready
**What goes wrong:** setAudio/setSubtitle calls fail silently or have no effect.
**Why it happens:** Zidoo player hasn't fully loaded the file's track list yet.
**How to avoid:** Wait for first successful getPlayStatus poll (confirms player is active), then add 500ms delay. The poller's initial 3s delay should be sufficient, but verify with first poll success.
**Warning signs:** Track calls return success but player doesn't switch tracks.

### Pitfall 5: Credit Skip Conflicts with Existing End-of-Episode Logic
**What goes wrong:** Both the credit skip and the existing 30s-before-end stop trigger fire.
**Why it happens:** Credit timestamps may overlap with the existing stop-before-end window.
**How to avoid:** When credit skip is armed and has valid timestamps, disable the existing generic 30s-before-end stop for that episode. Let the credit skip handle the Up Next trigger instead.
**Warning signs:** Double-stop attempts, duplicate handleEpisodeCompleted calls, countdown timer issues.

### Pitfall 6: Settings Export Leaks Access Token
**What goes wrong:** Exported JSON file contains access_token and user_id from SecureStorage.
**Why it happens:** Current export code only explicitly includes password from SecureStorage; SharedPreferences.getAll() should not include secure storage values. But verify this is true.
**How to avoid:** Explicitly exclude keys: `jellyfin_access_token`, `jellyfin_user_id` from the export map. Also verify they aren't accidentally stored in default SharedPreferences.
**Warning signs:** Exported JSON contains token-like strings in unexpected keys.

## Code Examples

### Fetching Intro Skipper Segments
```java
// Source: intro-skipper/intro-skipper GitHub + DeepWiki
// In JellyfinApi.java:

static class IntroSkipperResult {
    final double introStartSec;  // -1 if no intro
    final double introEndSec;
    final double creditStartSec; // -1 if no credits
    final double creditEndSec;

    // Convert to ms for Zidoo API
    long introStartMs() { return introStartSec < 0 ? -1 : (long)(introStartSec * 1000); }
    long introEndMs() { return introEndSec < 0 ? -1 : (long)(introEndSec * 1000); }
    long creditStartMs() { return creditStartSec < 0 ? -1 : (long)(creditStartSec * 1000); }
    long creditEndMs() { return creditEndSec < 0 ? -1 : (long)(creditEndSec * 1000); }
}

static IntroSkipperResult parseIntroSkipperResponse(String jsonBody) {
    JsonObject root = JsonParser.parseString(jsonBody).getAsJsonObject();

    double introStart = -1, introEnd = -1, creditStart = -1, creditEnd = -1;

    if (root.has("Introduction")) {
        JsonObject intro = root.getAsJsonObject("Introduction");
        if (intro.has("Valid") && intro.get("Valid").getAsBoolean()) {
            introStart = intro.get("IntroStart").getAsDouble();
            introEnd = intro.get("IntroEnd").getAsDouble();
        }
    }

    if (root.has("Credits")) {
        JsonObject credits = root.getAsJsonObject("Credits");
        if (credits.has("Valid") && credits.get("Valid").getAsBoolean()) {
            creditStart = credits.get("IntroStart").getAsDouble();
            creditEnd = credits.get("IntroEnd").getAsDouble();
        }
    }

    return new IntroSkipperResult(introStart, introEnd, creditStart, creditEnd);
}
```

### Parsing AudioStreamIndex/SubtitleStreamIndex from URL
```java
// Source: Jellyfin streaming URL format
// Extract from the intercepted intent URL in Play.java:

private int parseUrlParam(String url, String paramName) {
    try {
        android.net.Uri uri = android.net.Uri.parse(url);
        String value = uri.getQueryParameter(paramName);
        if (value != null) {
            return Integer.parseInt(value);
        }
    } catch (Exception e) {
        Log.w("Play", "Failed to parse " + paramName + " from URL: " + e.getMessage());
    }
    return -1; // Not found
}

// Usage:
int jellyfinAudioIndex = parseUrlParam(inputString, "AudioStreamIndex");
int jellyfinSubtitleIndex = parseUrlParam(inputString, "SubtitleStreamIndex");
```

### Mapping Jellyfin Stream Index to Zidoo Index
```java
// Source: Jellyfin API + Zidoo REST API documentation
// MediaStreams from getItem response:
// [{"Index":0,"Type":"Video"}, {"Index":1,"Type":"Audio"}, {"Index":2,"Type":"Audio"},
//  {"Index":3,"Type":"Subtitle"}, {"Index":4,"Type":"Subtitle"}]

// Jellyfin AudioStreamIndex=2 means "stream at global index 2"
// which is the 2nd Audio stream -> Zidoo audio index 1 (0-based among audio only)

static int jellyfinToZidooAudioIndex(JsonArray mediaStreams, int jellyfinIndex) {
    int audioCount = 0;
    for (int i = 0; i < mediaStreams.size(); i++) {
        JsonObject stream = mediaStreams.get(i).getAsJsonObject();
        String type = stream.get("Type").getAsString();
        if ("Audio".equals(type)) {
            if (stream.get("Index").getAsInt() == jellyfinIndex) {
                return audioCount; // 0-based Zidoo audio index
            }
            audioCount++;
        }
    }
    return -1; // Not found
}

static int jellyfinToZidooSubtitleIndex(JsonArray mediaStreams, int jellyfinIndex) {
    int subtitleCount = 0;
    for (int i = 0; i < mediaStreams.size(); i++) {
        JsonObject stream = mediaStreams.get(i).getAsJsonObject();
        String type = stream.get("Type").getAsString();
        if ("Subtitle".equals(type)) {
            if (stream.get("Index").getAsInt() == jellyfinIndex) {
                return subtitleCount + 1; // 1-based Zidoo subtitle index (0 = off)
            }
            subtitleCount++;
        }
    }
    return -1; // Not found
}
```

### Zidoo REST API Calls
```java
// Source: Zidoo forum + wizmo2/zidoo-player HA integration
// All calls are synchronous GET requests to localhost:9529

// Seek to position (ms) -- note the "positon" typo is real
private void seekZidoo(long positionMs) {
    try {
        okhttp3.Request request = new okhttp3.Request.Builder()
            .url("http://127.0.0.1:9529/ZidooVideoPlay/seekTo?positon=" + positionMs)
            .build();
        getLocalClient().newCall(request).execute().close();
    } catch (Exception e) {
        Log.w("Play", "seekTo failed: " + e.getMessage());
    }
}

// Set audio track (0-based index)
private void setZidooAudio(int index) {
    try {
        okhttp3.Request request = new okhttp3.Request.Builder()
            .url("http://127.0.0.1:9529/ZidooVideoPlay/setAudio?index=" + index)
            .build();
        getLocalClient().newCall(request).execute().close();
    } catch (Exception e) {
        Log.w("Play", "setAudio failed: " + e.getMessage());
    }
}

// Set subtitle track (1-based index, 0 = off)
private void setZidooSubtitle(int index) {
    try {
        okhttp3.Request request = new okhttp3.Request.Builder()
            .url("http://127.0.0.1:9529/ZidooVideoPlay/setSubtitle?index=" + index)
            .build();
        getLocalClient().newCall(request).execute().close();
    } catch (Exception e) {
        Log.w("Play", "setSubtitle failed: " + e.getMessage());
    }
}
```

### Settings Preferences XML (New Toggles)
```xml
<!-- Add to root_preferences.xml, inside the Player Settings category -->
<SwitchPreference
    android:defaultValue="true"
    android:key="skip_intros"
    android:title="@string/skip_intros_title"
    android:dependency="useZidooPlayer" />

<SwitchPreference
    android:defaultValue="true"
    android:key="skip_credits"
    android:title="@string/skip_credits_title"
    android:dependency="useZidooPlayer" />
```

## Skip Intro Button Overlay -- Feasibility Analysis

**User preference:** Netflix-style "Skip Intro" button visible while Zidoo player is running.

**Technical feasibility:** POSSIBLE but with significant friction.
- Requires `SYSTEM_ALERT_WINDOW` permission
- On Android TV (including Zidoo), this permission CANNOT be granted through normal settings UI
- Must be granted via ADB: `adb shell appops set com.jellyfintozidoo SYSTEM_ALERT_WINDOW allow`
- Uses `WindowManager.addView()` with `TYPE_APPLICATION_OVERLAY` layout params
- Button would need to auto-dismiss after intro window passes

**Recommendation:** Do NOT implement overlay for v1. The ADB requirement creates a poor user experience and support burden. Auto-skip with toggle (default on) is cleaner and simpler. The user acknowledged auto-skip with toggle is the accepted fallback. Document overlay as a potential v2 enhancement if user demand exists.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| ConfusedPolarBear/intro-skipper (archived) | intro-skipper/intro-skipper (active fork) | 2024 | New fork supports credits/outro, requires Jellyfin 10.11.6+ |
| `/Episode/{id}/IntroTimestamps/v1` (intro only) | `/Episode/{id}/IntroSkipperSegments` (intro + credits) | 2024 | Single endpoint for both, simpler integration |
| Jellyfin 10.10 MediaSegments API | Still evolving, Intro Skipper supports both | 2024 | IntroSkipperSegments is more stable for external clients |

## Open Questions

1. **Exact getPlayStatus response for audio/subtitle info**
   - What we know: Response includes `video.path`, `video.currentPosition`, `video.duration`
   - What's unclear: Whether `audio.index` and `subtitle.index` fields exist in getPlayStatus (mentioned in CONTEXT.md but not confirmed in research)
   - Recommendation: Not critical -- we set tracks proactively, don't need to read current track from Zidoo

2. **Jellyfin Android TV external player intent extras**
   - What we know: Streaming URL contains `AudioStreamIndex` and `SubtitleStreamIndex` as query params
   - What's unclear: Whether some Jellyfin clients (e.g., Findroid, Swiftfin) might send these differently
   - Recommendation: Parse from URL query params as primary source. This works with jellyfin-androidtv which is the primary client on Zidoo.

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
| ADVP-01 | Parse IntroSkipperSegments response | unit | `./gradlew test --tests "com.jellyfintozidoo.IntroSkipperApiTest" -x lint` | No -- Wave 0 |
| ADVP-01 | Disarm-on-seek logic | unit | `./gradlew test --tests "com.jellyfintozidoo.IntroSkipperApiTest" -x lint` | No -- Wave 0 |
| ADVP-02 | Parse credit timestamps from segments | unit | `./gradlew test --tests "com.jellyfintozidoo.IntroSkipperApiTest" -x lint` | No -- Wave 0 |
| ADVP-03 | Map Jellyfin audio index to Zidoo index | unit | `./gradlew test --tests "com.jellyfintozidoo.MediaStreamParsingTest" -x lint` | No -- Wave 0 |
| ADVP-04 | Map Jellyfin subtitle index to Zidoo index (1-based) | unit | `./gradlew test --tests "com.jellyfintozidoo.MediaStreamParsingTest" -x lint` | No -- Wave 0 |
| ADVP-03/04 | Parse AudioStreamIndex/SubtitleStreamIndex from URL | unit | `./gradlew test --tests "com.jellyfintozidoo.MediaStreamParsingTest" -x lint` | No -- Wave 0 |
| SETT-08 | Export excludes access_token/user_id | unit | `./gradlew test --tests "com.jellyfintozidoo.SettingsExportTest" -x lint` | No -- Wave 0 |
| SETT-08 | Import triggers auto-authenticate | manual-only | Manual: import settings, verify auto-login | N/A |

### Sampling Rate
- **Per task commit:** `./gradlew test --tests "com.jellyfintozidoo.*" -x lint`
- **Per wave merge:** `./gradlew test -x lint`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `app/src/test/java/com/jellyfintozidoo/IntroSkipperApiTest.java` -- covers ADVP-01, ADVP-02
- [ ] `app/src/test/java/com/jellyfintozidoo/MediaStreamParsingTest.java` -- covers ADVP-03, ADVP-04
- [ ] `app/src/test/java/com/jellyfintozidoo/SettingsExportTest.java` -- covers SETT-08

## Sources

### Primary (HIGH confidence)
- [intro-skipper/intro-skipper GitHub](https://github.com/intro-skipper/intro-skipper) -- Active fork, IntroSkipperSegments endpoint
- [ConfusedPolarBear/intro-skipper API docs](https://github.com/ConfusedPolarBear/intro-skipper/blob/master/docs/api.md) -- Legacy v1 endpoint format
- [DeepWiki: intro-skipper Auto-Skip and Playback Integration](https://deepwiki.com/intro-skipper/intro-skipper/4.3-auto-skip-and-playback-integration) -- Segment type mapping
- [Zidoo REST API Forum Post](http://forum.zidoo.tv/index.php?threads/my-experiences-using-the-zidoo-rest-api-for-playing-video-and-selecting-audio-subtitle-tracks.99732/) -- setAudio/setSubtitle indexing, timing requirements
- [wizmo2/zidoo-player HA integration](https://github.com/wizmo2/zidoo-player) -- API endpoint documentation
- Existing codebase: Play.java, JellyfinApi.java, SettingsActivity.java -- confirmed existing patterns

### Secondary (MEDIUM confidence)
- [Findroid Issue #620](https://github.com/jarnedemeulemeester/findroid/issues/620) -- IntroSkipperSegments JSON response format with both Introduction and Credits
- [Jellyfin streaming URL format](https://github.com/jellyfin/jellyfin/issues/9631) -- AudioStreamIndex/SubtitleStreamIndex as query params

### Tertiary (LOW confidence)
- Android TV overlay feasibility -- based on general Android documentation, not Zidoo-specific testing

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- no new dependencies, all APIs documented
- Architecture: HIGH -- extends existing patterns (poller, REST calls, preferences)
- Pitfalls: HIGH -- common issues documented in Zidoo forum and existing codebase experience
- Intro Skipper API: HIGH -- multiple sources confirm IntroSkipperSegments endpoint
- Audio/Subtitle mapping: MEDIUM -- index mapping logic is straightforward but untested on actual Zidoo hardware
- Overlay button: LOW -- technically possible but not recommended for v1

**Research date:** 2026-03-14
**Valid until:** 2026-04-14 (stable APIs, unlikely to change)
