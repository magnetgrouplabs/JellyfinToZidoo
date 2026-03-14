# Phase 4: Episode Intelligence - Context

**Gathered:** 2026-03-14
**Status:** Ready for planning

<domain>
## Phase Boundary

Seamless multi-episode tracking when navigating within the Zidoo player. Users can binge TV shows with every episode's watch status correctly synced to Jellyfin. Includes: Up Next countdown screen between episodes, per-episode playback tracking when Zidoo auto-advances, reverse path resolution for episode identification, and multiple path substitution rule UI. Does NOT include intro/credit skip (Phase 5) or settings import/export (Phase 5).

</domain>

<decisions>
## Implementation Decisions

### Auto-play behavior
- Build an "Up Next" countdown screen that shows between episodes
- 10-second countdown timer, auto-plays next episode when it reaches zero
- "Play Now" button (skip countdown) and "Cancel" button (return to Jellyfin client)
- Query Jellyfin NextUp API to get next episode, resolve file path, do substitution, launch Zidoo player
- Up Next screen only triggers for TV episodes (items with seriesId) — never for movies
- Up Next only appears when episode is completed (≥90% watched) — mid-episode stop saves resume and goes back to Jellyfin client (current Phase 3 behavior)
- When no next episode exists (series finale), skip Up Next and go back to Jellyfin client

### Up Next screen design
- Blurred series backdrop image (Jellyfin `/Items/{id}/Images/Backdrop`) as full-screen background
- Use Glide for image loading + blur transform
- Semi-transparent gradient overlay for readability
- Centered card on top showing: episode thumbnail, series name, S##E## label, episode title
- Countdown timer visual (ring, bar, or number — Claude's discretion on exact style)
- D-pad navigable (Play Now + Cancel buttons) for Android TV
- Pull all metadata from Jellyfin API: Name, SeriesName, ParentIndexNumber, IndexNumber, ImageTags

### Per-episode tracking cycle
- When Zidoo auto-advances to next file (path change detected in poller): mark previous episode watched, then identify new episode and start fresh tracking
- Fresh tracking = new getItem() call, new playSessionId, new playback start report, reset poller state
- Reverse path substitution to convert Zidoo SMB path back to server-side path, then search Jellyfin items by path to resolve item ID
- Track ANY path change regardless of direction (forward or backward navigation in Zidoo)
- If reverse lookup fails to identify new episode: Claude's discretion on fallback behavior (silent log vs toast warning — follow PlexToZidoo error patterns)

### Season boundary handling
- Jellyfin NextUp API handles season boundaries natively — it returns the next unwatched episode across seasons
- No special client-side logic needed for cross-season auto-play

### Claude's Discretion
- Countdown timer visual style (circular ring, linear bar, or simple number)
- Up Next screen layout specifics (exact spacing, typography, animation)
- Fallback behavior when reverse path lookup fails (silent log vs toast)
- Glide configuration details (cache strategy, blur radius)
- How to structure the reverse substitution logic (inline vs separate utility)
- Whether to create a new Activity for Up Next or use a Dialog/Fragment overlay

</decisions>

<specifics>
## Specific Ideas

- "Something stylish and up to date" — blurred backdrop with foreground card, Netflix-like feel
- Balance visual quality with implementation effort — look good but don't go crazy
- Jellyfin backdrop images available at `/Items/{id}/Images/Backdrop`
- Existing `getNextUp()` in JellyfinApi.java already queries `/Shows/NextUp?seriesId=X&limit=1`

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets
- `JellyfinApi.getNextUp()`: Already queries NextUp API, returns next item ID — extend to return full metadata
- `JellyfinApi.getItem()`: Fetches item metadata (path, title, duration, seriesId) — reuse for next episode resolution
- `JellyfinApi.parseItemResponse()`: Already extracts Name, RunTimeTicks, SeriesId, Path — extend for ParentIndexNumber, IndexNumber, ImageTags
- `handleEpisodeCompleted()`: Already marks previous ep watched on path change — extend to feed into Up Next flow
- `doSubstitution()`: 10-slot path substitution loop — reuse as-is for next episode path conversion
- `buildZidooIntent()`: Zidoo player launch with resume position — reuse for auto-play launch
- All playback reporting methods (start, progress, stopped, markAsWatched) — reuse for each episode cycle

### Established Patterns
- Async API calls via OkHttp with callback on main thread
- `startActivityForResult()` with request code 98 for Zidoo player
- ScheduledExecutorService for progress polling (3s initial delay, 10s interval)
- Error display via Toast for non-critical errors
- PlexToZidoo's `searchFiles()` pattern → our `searchNextEpisode()` is the Jellyfin equivalent

### Integration Points
- `handleEpisodeCompleted()` → currently calls `searchNextEpisode()` which opens Jellyfin client — replace with Up Next flow
- `onActivityResult()` watched branch → currently calls `searchNextEpisode()` — replace with Up Next flow
- `startProgressPoller()` → needs reset logic when tracking switches to a new episode mid-session
- New Activity needed: `UpNextActivity.java` (or similar) for the countdown screen
- New layout XML needed: `activity_up_next.xml` for the Up Next UI
- `root_preferences.xml` → expose multiple substitution rule slots for SETT-04 (existing 10-slot backend already works)

</code_context>

<deferred>
## Deferred Ideas

- Configurable countdown timer duration in settings — hardcode 10s for now
- "Continue watching" prompt when stopping mid-episode — just save resume for now
- Series completion celebration screen — just go back to Jellyfin

</deferred>

---

*Phase: 04-episode-intelligence*
*Context gathered: 2026-03-14*
