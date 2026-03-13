# Phase 2: Core Bridge - Context

**Gathered:** 2026-03-13
**Status:** Ready for planning

<domain>
## Phase Boundary

End-to-end intent interception from Jellyfin clients through Zidoo player launch with authentication, settings, and debug. User can play media from a Jellyfin client and have it launch in the native Zidoo player at the correct resume position via SMB path substitution. No playback reporting back to Jellyfin — that's Phase 3.

</domain>

<decisions>
## Implementation Decisions

### Jellyfin client targeting
- Primary targets: Moonfin and stock Jellyfin Android TV client
- If other clients (Findroid, etc.) use the same intent mechanism, they'll work too — but don't add complexity for them
- Jellyfin clients send HTTP streaming URLs (e.g., `http://server:8096/Videos/{itemId}/stream`); we extract the item ID and call the Jellyfin API to get the server-side file path
- Research phase should investigate exact intent format from Moonfin and stock Jellyfin Android TV

### Authentication
- **API key only** — no username/password auth. Simple, one field.
- If both were ever added later, API key would take priority
- Credentials stored using EncryptedSharedPreferences (AndroidX Security library) — upgrade from PlexToZidoo's plain SharedPreferences
- Auth token (API key) persists across app restarts

### Settings screen layout
- New "Jellyfin Server" section at the TOP of the settings screen with: Server URL, API Key
- Below that: General (debug toggle), Player, Substitution, Import/Export — same order as current
- Settings layout decisions (sub-screens vs flat) are Claude's discretion

### Error handling
- Follow PlexToZidoo's existing error patterns exactly — don't reinvent
- Errors show on the debug screen with warning messages, same as current behavior
- No test connection button unless Claude determines it's trivially simple

### Debug screen
- Follow PlexToZidoo's existing debug screen pattern
- Replace Plex-specific fields with Jellyfin equivalents (item ID, Jellyfin API path, etc.)
- Claude decides the exact debug info shown — pipeline trace vs minimal is Claude's call
- Don't show calling app package name — not needed

### Resume position
- Jellyfin uses ticks (1 tick = 100ns), Zidoo player uses milliseconds
- Convert ticks → ms when passing to Zidoo player
- Claude decides whether to show both values on debug screen or just the final ms

### Claude's Discretion
- Test connection button in settings (if trivially simple to add)
- Debug screen information density (pipeline trace vs minimal)
- Settings sub-screen layout (flat vs sub-pages)
- Resume position display format on debug screen
- HTTP client choice for Jellyfin API calls (OkHttp preferred per PROJECT.md)
- Error handling specifics — match PlexToZidoo patterns

</decisions>

<specifics>
## Specific Ideas

- "Whatever PlexToZidoo does" — follow the existing app's patterns for error handling, debug display, and UX. Don't reinvent anything.
- Keep it simple — KISS approach throughout. Don't overcomplicate the fork.
- API key is generated from Jellyfin admin dashboard → user pastes it into settings
- Jellyfin API endpoint for file path: `/Items/{id}?Fields=Path,MediaSources`

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets
- `Play.java:doSubstitution()` — path substitution logic, fully working, handles 10 substitution rule slots
- `Play.java:buildZidooIntent()` — Zidoo player launch with resume position, audio/subtitle index, title
- `SettingsActivity.java` — PreferenceScreen-based settings with import/export, D-pad navigable
- `root_preferences.xml` — existing settings structure to extend with Jellyfin section
- `AndroidManifest.xml` — intent filters for `ACTION_VIEW` + `video/*` already registered

### Established Patterns
- Java, Gradle, Android TV Leanback, PreferenceScreen for settings
- PlexToZidoo used Volley for HTTP requests (commented out) — PROJECT.md says OkHttp preferred
- Errors displayed on debug screen via `textView1` with yellow background for warnings
- `message` field accumulates error/warning text, displayed before play
- SharedPreferences for all settings (will upgrade credential fields to EncryptedSharedPreferences)

### Integration Points
- `Play.java:onStart()` — after the `!zdmc` fallback block (line 659-663), replace with Jellyfin API call flow
- `Play.java` — new fields needed: Jellyfin item ID, server URL, API key
- `root_preferences.xml` — add Jellyfin Server section at top
- `AndroidManifest.xml` — intent filters may need adjustment based on Jellyfin client intent format research
- New class needed: `JellyfinApi.java` (or similar) for API communication

</code_context>

<deferred>
## Deferred Ideas

- Username/password authentication — if community requests it, add in a future update
- Identifying calling app (Moonfin vs stock) on debug screen — not needed now
- Findroid-specific support — only if it doesn't work with the standard approach

</deferred>

---

*Phase: 02-core-bridge*
*Context gathered: 2026-03-13*
