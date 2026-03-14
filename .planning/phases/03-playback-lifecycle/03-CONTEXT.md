# Phase 3: Playback Lifecycle - Context

**Gathered:** 2026-03-13
**Status:** Ready for planning

<domain>
## Phase Boundary

Bidirectional playback reporting and watch state sync with Jellyfin. After a Zidoo player session, Jellyfin accurately reflects what was watched, resume position, and watched status. During playback, Jellyfin shows "Now Playing." After playback ends, the originating Jellyfin client is relaunched (2-app limit workaround). No episode navigation tracking (Phase 4) or intro/credit skip (Phase 5).

</domain>

<decisions>
## Implementation Decisions

### Playback monitoring approach
- Follow PlexToZidoo pattern: `startActivityForResult()` → `onActivityResult()` for final position
- Zidoo player returns `position` extra in the result intent (milliseconds)
- Additionally: poll Zidoo's `localhost:9529/ZidooVideoPlay/getPlayStatus` during playback for periodic progress (PlexToZidoo doesn't do this, but Jellyfin requires it for "Now Playing")

### Playback reporting to Jellyfin
- **Start**: POST `/Sessions/Playing` immediately after launching Zidoo player
- **Progress**: POST `/Sessions/Playing/Progress` periodically during playback (poll Zidoo's local API for current position, convert ms → ticks, report to Jellyfin)
- **Stopped**: POST `/Sessions/Playing/Stopped` in `onActivityResult()` with final position from Zidoo player result

### Watched/resume threshold
- Follow PlexToZidoo pattern exactly: `position > (duration * 0.9)` → mark as watched
- Mark watched via Jellyfin API (POST `/Users/{userId}/PlayedItems/{itemId}` or equivalent)
- Otherwise: save resume position via the stopped report (`PositionTicks` in the stopped payload)
- Need to capture `RunTimeTicks` (duration) from Jellyfin item metadata — extend existing `getItem()` response parsing

### Client relaunch after playback
- Detect originating Jellyfin client automatically from the incoming intent's calling package
- Capture `getCallingActivity()` or `getCallingPackage()` during `onStart()`, store for relaunch
- After playback reporting completes, relaunch the detected client via `startActivity()`
- If caller detection fails, fall back to just finishing the activity (user returns to Android TV home)

### Claude's Discretion
- Progress polling interval (suggest ~10-15 seconds, match what feels responsive in Jellyfin dashboard)
- Whether to use a background thread, Handler, or ScheduledExecutor for polling during playback
- PlaySessionId generation strategy for Jellyfin session reporting
- Error handling for Jellyfin API reporting failures (silent fail vs toast — follow PlexToZidoo's toast pattern)
- Whether to show playback reporting status on debug screen

</decisions>

<specifics>
## Specific Ideas

- "However PlexToZidoo does it" — follow existing patterns exactly unless Jellyfin specifically requires something different
- PlexToZidoo's `onActivityResult()` (lines 833-889) is the direct template: check 90% threshold, scrobble or save progress, relaunch client
- PlexToZidoo uses Volley for HTTP — we use OkHttp (already established in JellyfinApi.java)
- Jellyfin uses ticks (1 tick = 100ns) — `JellyfinApi.ticksToMs()` and reverse already exist

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets
- `JellyfinApi.java`: OkHttp client singleton, auth header builder, async callback pattern, ticks↔ms conversion — extend with playback reporting methods
- `Play.java:onActivityResult()`: Empty hook ready for Phase 3 implementation (lines 829-892)
- `Play.java:buildZidooIntent()`: Already sets `return_result=true` (line 818), so Zidoo player will return result
- `SecureStorage.java`: Access token + userId already stored and retrievable

### Established Patterns
- Async API calls via OkHttp with `JellyfinApi.Callback` interface on main thread
- Error display via `message` field + debug screen yellow warning
- Toast for non-critical errors (PlexToZidoo line 875)
- `startActivityForResult()` with request code 98

### Integration Points
- `Play.java:onActivityResult()` — primary integration point for stop reporting + client relaunch
- `Play.java:onStart()` — capture calling package here before Zidoo player launches
- `JellyfinApi.java` — add `reportPlaybackStart()`, `reportPlaybackProgress()`, `reportPlaybackStopped()`, `markAsWatched()` methods
- `Play.java` between player launch and `onActivityResult()` — start progress polling thread here
- Item metadata response — extend to parse `RunTimeTicks` for duration (needed for 90% threshold)

</code_context>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 03-playback-lifecycle*
*Context gathered: 2026-03-13*
