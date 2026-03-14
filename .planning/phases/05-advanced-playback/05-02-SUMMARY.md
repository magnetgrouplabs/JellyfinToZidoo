---
phase: 05-advanced-playback
plan: 02
subsystem: playback
tags: [intro-skip, credit-skip, audio-passthrough, subtitle-passthrough, zidoo-rest-api, introskipper]

# Dependency graph
requires:
  - phase: 05-advanced-playback
    provides: IntroSkipperResult parsing, stream index mapping, parseUrlParam, findDefaultStreamIndex from Plan 01
  - phase: 04-episode-intelligence
    provides: Progress poller, handleEpisodeCompleted, Up Next flow, searchItemByPath
provides:
  - Intro skip via Zidoo seekTo REST API triggered by poller position detection
  - Credit skip triggering early Up Next flow when position enters credit range
  - Audio/subtitle track passthrough from Jellyfin intent URL to Zidoo player via REST API
  - Disarm-on-seek safety mechanism for both intro and credit skip
  - Per-episode state reset for binge watching (including track indices and MediaStreams)
  - Settings toggles for Skip Intros and Skip Credits (default on)
  - getIntroSkipperSegments network method in JellyfinApi
  - getItemDetailed with DetailedCallback for MediaStreams extraction
affects: [05-03-PLAN]

# Tech tracking
tech-stack:
  added: []
  patterns: [DetailedCallback for raw JSON body access, fire-and-forget Zidoo REST API helpers, SharedPreferences read per poll cycle]

key-files:
  created: []
  modified:
    - app/src/main/java/com/jellyfintozidoo/JellyfinApi.java
    - app/src/main/java/com/jellyfintozidoo/Play.java
    - app/src/main/res/xml/root_preferences.xml
    - app/src/main/res/values/strings.xml

key-decisions:
  - "Added DetailedCallback interface and getItemDetailed method to pass raw JSON body for MediaStreams extraction without breaking existing Callback interface"
  - "Credit skip overrides generic 30s-before-end stop when armed and data available, preventing duplicate Up Next triggers"
  - "Binge episode path change triggers full re-fetch of item details, MediaStreams, and IntroSkipper segments"
  - "Audio/subtitle tracks set once per episode with 500ms delay to let Zidoo fully load track list"

patterns-established:
  - "Fire-and-forget Zidoo REST API helpers (seekZidoo, setZidooAudio, setZidooSubtitle) run on background threads"
  - "Per-episode state reset pattern covers all skip/track/segment fields on path change and Up Next Play Now"

requirements-completed: [ADVP-01, ADVP-02, ADVP-03, ADVP-04]

# Metrics
duration: 6min
completed: 2026-03-14
---

# Phase 5 Plan 02: Play.java Integration Summary

**Intro/credit skip via Zidoo seekTo API with disarm-on-seek safety, audio/subtitle track passthrough from Jellyfin intent URL, and binge episode re-fetch wiring**

## Performance

- **Duration:** 6 min
- **Started:** 2026-03-14T19:13:12Z
- **Completed:** 2026-03-14T19:19:00Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments
- Complete intro skip pipeline: poller detects position in intro range, seeks Zidoo player to intro end, disarms to prevent re-trigger
- Complete credit skip pipeline: poller detects position in credit range, triggers handleEpisodeCompleted for early Up Next flow
- Audio/subtitle track passthrough: parses indices from Jellyfin intent URL, maps via jellyfinToZidoo*Index, sets in Zidoo player via REST API after first successful poll
- Disarm-on-seek safety: detects manual seek (>30s forward or any backward jump) and disarms intro/credit skip to respect user intent
- Binge episode support: path change detection triggers full re-fetch of item details, MediaStreams, and IntroSkipper segments via searchItemByPath
- Settings toggles: Skip Intros and Skip Credits SwitchPreference entries in Player settings (default on, dependent on useZidooPlayer)

## Task Commits

Each task was committed atomically:

1. **Task 1: Add JellyfinApi network method, settings toggles, and string resources** - `f69e309` (feat)
2. **Task 2: Wire intro/credit skip and audio/subtitle passthrough into Play.java** - `99a3a96` (feat)

## Files Created/Modified
- `app/src/main/java/com/jellyfintozidoo/JellyfinApi.java` - Added getIntroSkipperSegments network method, DetailedCallback interface, getItemDetailed method
- `app/src/main/java/com/jellyfintozidoo/Play.java` - Added intro/credit skip logic in poller, audio/subtitle track setting, seekZidoo/setZidooAudio/setZidooSubtitle helpers, per-episode state reset, binge episode re-fetch wiring
- `app/src/main/res/xml/root_preferences.xml` - Added skip_intros and skip_credits SwitchPreference entries
- `app/src/main/res/values/strings.xml` - Added skip_intros_title and skip_credits_title string resources

## Decisions Made
- Added DetailedCallback interface (with rawBody parameter) and getItemDetailed method rather than modifying existing Callback interface, to avoid breaking the simpler getItem callers that don't need MediaStreams
- Credit skip check overrides the generic 30s-before-end stop by adding `!(creditSkipArmed && creditStartMs >= 0)` guard, preventing duplicate Up Next triggers
- Binge episode path change triggers searchItemByPath followed by getItemDetailed and getIntroSkipperSegments in parallel, ensuring full metadata refresh for second and subsequent episodes
- Audio/subtitle track setting uses 500ms Thread.sleep delay to allow Zidoo player to fully load its track list before setting indices

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Added DetailedCallback and getItemDetailed for MediaStreams access**
- **Found during:** Task 2 (Section C - Extract MediaStreams from getItem response)
- **Issue:** The existing getItem callback only returns parsed fields (path, positionTicks, title, durationTicks, seriesId) but MediaStreams extraction requires the raw JSON body. The plan assumed in-callback access to raw JSON.
- **Fix:** Added DetailedCallback interface with rawBody parameter and getItemDetailed method that passes raw JSON alongside parsed fields
- **Files modified:** JellyfinApi.java
- **Verification:** Build succeeds, all tests pass
- **Committed in:** 99a3a96 (Task 2 commit)

**2. [Rule 2 - Missing Critical] Added binge episode path resolution via searchItemByPath**
- **Found during:** Task 2 (Section J - Binge episode re-fetch wiring)
- **Issue:** Plan referenced existing searchItemByPath call in Play.java but no such call existed. The episode-change detection in the poller only tracked the path but never resolved the new item ID.
- **Fix:** Added searchItemByPath call in the episode-change detection block, with full chain: resolve itemId -> getItemDetailed for MediaStreams -> getIntroSkipperSegments for skip data -> reportPlaybackStart
- **Files modified:** Play.java
- **Verification:** Build succeeds, all tests pass
- **Committed in:** 99a3a96 (Task 2 commit)

---

**Total deviations:** 2 auto-fixed (2 missing critical)
**Impact on plan:** Both auto-fixes necessary for correctness. DetailedCallback enables MediaStreams access without breaking existing interface. Binge path resolution enables episode tracking for auto-advanced episodes. No scope creep.

## Issues Encountered
None

## User Setup Required
None - IntroSkipper plugin must already be installed on the Jellyfin server for intro/credit data to be available. If not installed, getIntroSkipperSegments returns 404 which silently no-ops to all -1 sentinels.

## Next Phase Readiness
- All intro/credit skip and audio/subtitle passthrough logic is wired and ready for live testing
- Plan 03 (settings export/import fixes) can proceed independently
- No blockers

---
*Phase: 05-advanced-playback*
*Completed: 2026-03-14*
