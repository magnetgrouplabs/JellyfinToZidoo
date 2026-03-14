# Phase 5: Advanced Playback - Context

**Gathered:** 2026-03-14
**Status:** Ready for planning

<domain>
## Phase Boundary

Polish the playback experience with intro/credit handling, audio/subtitle stream selection passthrough, and settings portability. Leverages Jellyfin Intro Skipper plugin data for intro/credit timestamps, Zidoo REST API for seek and track switching, and existing export/import code for settings. Does NOT include new browsing UI, new playback paradigms, or features beyond ADVP-01 through ADVP-04 and SETT-08.

</domain>

<decisions>
## Implementation Decisions

### Intro skip behavior
- Auto-skip intros silently using Zidoo `seekTo` API when poller detects position in intro range
- Settings toggle: "Skip intros" (default on) — when disabled, intros play normally
- Every play — skip intros on every episode, not just first watch
- Silent no-op when Intro Skipper plugin isn't installed or has no data for an episode
- **Disarm on manual seek**: track `introSkipArmed = true` per episode. If user manually seeks to any position before `introEnd`, set `introSkipArmed = false` — intro skip disabled for that episode. Resets to `true` on new episode. This prevents unexpected seek-away when user is navigating near the beginning.
- Only trigger intro skip during natural forward playback, NOT on resume into intro range
- Research needed: feasibility of a "Skip Intro" button overlay (user prefers a button if possible). If overlay on external Zidoo player app is too complex or impossible, fall back to the auto-skip with toggle approach.

### Credit/outro handling
- When credits timestamp is reached, trigger Up Next flow early: mark episode watched → stop Zidoo player → launch Up Next countdown screen (reuses Phase 4 Up Next infrastructure)
- Settings toggle: "Skip credits" (separate from intro skip toggle) — when disabled, credits play normally and standard end-of-file Up Next triggers
- Intro Skipper plugin exposes `IntroStart`/`IntroEnd` and `OutroStart`/`OutroEnd` as separate fields — separate toggles are trivial
- Same "disarm on manual seek" logic as intro skip — if user seeks before `outroStart`, credit skip is disarmed for that episode
- TV shows only — credit skip does not trigger for movies (matches Phase 4 Up Next behavior: only items with seriesId)

### Audio/subtitle passthrough
- Pass audio/subtitle track selection from Jellyfin client intent to Zidoo player via Zidoo REST API (`setAudio?index=N`, `setSubtitle?index=N`)
- Set tracks once after Zidoo player launches — Zidoo remembers per-file track selections on subsequent plays
- Index-based mapping: parse Jellyfin MediaStreams array (already in getItem response, just not parsed), filter by Type (Audio/Subtitle), map positional index to Zidoo's 0-based audio index and 1-based subtitle index
- Fallback chain: Jellyfin client intent selection → Jellyfin item default streams (IsDefault/IsForced) → don't set anything (let Zidoo pick)
- No settings toggle — always attempt passthrough, silent no-op if no data available
- Always-on feature, no toggle needed

### Settings import/export
- Verify and fix up existing PlexToZidoo export/import code (already functional in SettingsActivity.java)
- Keep .txt file extension (PlexToZidoo convention, import doesn't care about extension)
- Export password only — access_token and user_id are NOT exported (regenerated on login)
- New Phase 5 settings (intro skip toggle, credit skip toggle) auto-included via SharedPreferences — no code change needed for those
- After import, automatically call authenticate() with restored credentials to get a fresh token — user is ready to go immediately
- Export path remains Downloads/JellyfinToZidooSettings.txt

### Claude's Discretion
- Exact timing of setAudio/setSubtitle call after Zidoo player launch (delay needed for player initialization)
- seekTo call timing and retry logic if Zidoo player hasn't started yet
- How to detect "manual seek" vs "natural playback" for disarm logic (likely position jump detection in poller)
- Whether to fetch intro timestamps once at episode start or cache them
- Intro Skipper API endpoint choice (research which endpoint is standard)
- Settings screen layout for new toggles (placement within existing preferences hierarchy)

</decisions>

<specifics>
## Specific Ideas

- User strongly prefers a "Skip Intro" button (Netflix-style) if technically feasible — research whether a system overlay or floating window can display on top of the Zidoo player app. If not feasible, auto-skip with toggle is the accepted fallback.
- "I don't want to overcomplicate this" — keep implementation simple, especially around track index mapping
- Zidoo remembers per-file audio/subtitle selections — only need to set once per file, not on every play
- Disarm-on-seek approach: "if you want intro skipping just don't seek in the beginning" — user considers this a reasonable requirement

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets
- `Play.java`: `audioSelected`, `selectedAudioIndex`, `subtitleSelected`, `selectedSubtitleIndex` variables already declared (lines 85-88) and passed to Zidoo intent via `audio_idx`/`subtitle_idx` extras — extend to also use REST API
- `Play.java`: Progress poller already reads `video.currentPosition` and `video.duration` from Zidoo `getPlayStatus` — extend to check intro/credit ranges and read `audio.index`/`subtitle.index`
- `SettingsActivity.java`: `exportSettings()` (line 383) and `importSettings()` (line 264) already functional — verify and extend
- `SecureStorage.java`: Password export already special-cased in export code
- `UpNextActivity.java`: Full Up Next flow from Phase 4 — reuse for credit skip trigger
- Existing `handleEpisodeCompleted()` flow — credit skip triggers this same flow, just earlier

### Established Patterns
- Zidoo REST API calls to `localhost:9529` (only `getPlayStatus` currently used)
- `seekTo?positon={ms}` endpoint available (note: "positon" typo is real in the Zidoo API)
- `setAudio?index={0-based}` and `setSubtitle?index={1-based, 0=off}` endpoints researched
- SharedPreferences for non-sensitive settings, SecureStorage for credentials
- SwitchPreference pattern in `root_preferences.xml` for boolean toggles

### Integration Points
- Progress poller in `Play.java` — add intro/credit range checks to each poll cycle
- `getItem()` response — already fetches `Fields=MediaSources` which includes `MediaStreams[]`, just needs parsing
- `JellyfinApi.java` — new method to fetch Intro Skipper timestamps (new API endpoint)
- `root_preferences.xml` — add "Skip intros" and "Skip credits" SwitchPreference entries
- `SettingsActivity.java` — add auto-login after import

</code_context>

<deferred>
## Deferred Ideas

- Configurable intro skip button hold duration — hardcode behavior for now
- Per-series intro skip preferences — global toggle only
- External subtitle file selection from Jellyfin — only embedded streams for now
- NFS path support (v2 requirement PROT-01)

</deferred>

---

*Phase: 05-advanced-playback*
*Context gathered: 2026-03-14*
