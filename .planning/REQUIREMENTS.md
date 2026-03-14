# Requirements: JellyfinToZidoo

**Defined:** 2026-03-13
**Core Value:** When a user plays media through a Jellyfin client on a Zidoo device, the native Zidoo player handles playback with full hardware decode, and watch state syncs seamlessly back to Jellyfin.

## v1 Requirements

Requirements for initial release. Each maps to roadmap phases.

### Fork & Setup

- [x] **FORK-01**: Fork PlexToZidoo, rename package to com.jellyfintozidoo
- [x] **FORK-02**: Rename app to JellyfinToZidoo throughout (strings, manifest, branding)
- [x] **FORK-03**: Preserve attribution to PlexToZidoo/bowlingbeeg in README and license
- [x] **FORK-04**: Strip all Plex API imports and calls
- [x] **FORK-05**: Project builds cleanly after rename and Plex removal

### Authentication

- [x] **AUTH-01**: User can authenticate with Jellyfin server using API key
- [x] **AUTH-02**: User can authenticate with Jellyfin server using username/password
- [x] **AUTH-03**: Credentials stored securely using Android Keystore
- [x] **AUTH-04**: Auth token persists across app restarts

### Core Bridge

- [x] **BRDG-01**: App intercepts ACTION_VIEW intents for video MIME types from Jellyfin clients
- [x] **BRDG-02**: App extracts item ID from Jellyfin HTTP streaming URL
- [x] **BRDG-03**: App resolves server-side file path via Jellyfin API (/Items/{id}?Fields=Path,MediaSources)
- [x] **BRDG-04**: App applies path substitution to convert server path to SMB URI
- [x] **BRDG-05**: App launches native Zidoo player with SMB path
- [x] **BRDG-06**: App passes resume position to Zidoo player on launch (converting Jellyfin ticks to ms)

### Playback Lifecycle

- [x] **PLAY-01**: App reports playback start to Jellyfin (POST /Sessions/Playing)
- [x] **PLAY-02**: App reports playback progress periodically (POST /Sessions/Playing/Progress)
- [x] **PLAY-03**: App reports playback stopped with final position (POST /Sessions/Playing/Stopped)
- [x] **PLAY-04**: Resume position written back to Jellyfin on playback stop
- [x] **PLAY-05**: Media marked as watched when ≥90% played
- [x] **PLAY-06**: App relaunches originating Jellyfin client after playback ends (Zidoo 2-app limit workaround)

### Settings

- [x] **SETT-01**: User can configure Jellyfin server URL
- [x] **SETT-02**: User can configure API key or username/password credentials
- [x] **SETT-03**: User can configure path substitution rule (find/replace)
- [x] **SETT-04**: User can configure multiple path substitution rules
- [x] **SETT-05**: User can configure SMB username/password (optional)
- [x] **SETT-06**: User can toggle debug screen on/off
- [x] **SETT-07**: Settings UI is D-pad navigable (Android TV / Leanback compatible)
- [x] **SETT-08**: User can import/export settings as JSON

### Debug

- [x] **DEBG-01**: Debug screen shows parsed intent data (URI, extras, item ID)
- [x] **DEBG-02**: Debug screen shows resolved file path from Jellyfin API
- [x] **DEBG-03**: Debug screen shows substituted SMB path
- [x] **DEBG-04**: Debug screen has manual Play button to launch Zidoo player

### Episode Intelligence

- [x] **EPIS-01**: App detects when user navigates to a different episode in Zidoo player (via getPlayStatus path changes)
- [x] **EPIS-02**: Each episode navigated to in Zidoo player gets its watched status reported to Jellyfin
- [x] **EPIS-03**: App auto-launches next episode after current episode finishes
- [x] **EPIS-04**: App handles season boundaries for next episode resolution

### Advanced Playback

- [x] **ADVP-01**: App skips intros using Jellyfin Intro Skipper plugin data
- [x] **ADVP-02**: App stops or advances at credits using Intro Skipper outro data
- [x] **ADVP-03**: App passes audio stream selection to Zidoo player
- [x] **ADVP-04**: App passes subtitle stream selection to Zidoo player

## v2 Requirements

Deferred to future release. Tracked but not in current roadmap.

### Multi-User

- **MUSR-01**: Support multiple Jellyfin user profiles on same device

### Protocol

- **PROT-01**: NFS path support in addition to SMB

## Out of Scope

| Feature | Reason |
|---------|--------|
| Built-in media browsing / Jellyfin client UI | Bridge app, not a client. Use existing Jellyfin clients for browsing. |
| Transcoding / HTTP streaming fallback | Defeats the purpose — hardware decode via SMB is the whole point |
| Chromecast / DLNA support | Different playback paradigm, native Zidoo player only |
| Kotlin conversion | Staying Java for PlexToZidoo community continuity |
| Local intro/credit detection | Server plugin's job, we only consume timestamps |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| FORK-01 | Phase 1 | Complete |
| FORK-02 | Phase 1 | Complete |
| FORK-03 | Phase 1 | Complete |
| FORK-04 | Phase 1 | Complete |
| FORK-05 | Phase 1 | Complete |
| AUTH-01 | Phase 2 | Complete |
| AUTH-02 | Phase 2 | Complete |
| AUTH-03 | Phase 2 | Complete |
| AUTH-04 | Phase 2 | Complete |
| BRDG-01 | Phase 2 | Complete |
| BRDG-02 | Phase 2 | Complete |
| BRDG-03 | Phase 2 | Complete |
| BRDG-04 | Phase 2 | Complete |
| BRDG-05 | Phase 2 | Complete |
| BRDG-06 | Phase 2 | Complete |
| PLAY-01 | Phase 3 | In Progress |
| PLAY-02 | Phase 3 | In Progress |
| PLAY-03 | Phase 3 | In Progress |
| PLAY-04 | Phase 3 | In Progress |
| PLAY-05 | Phase 3 | In Progress |
| PLAY-06 | Phase 3 | Complete |
| SETT-01 | Phase 2 | Complete |
| SETT-02 | Phase 2 | Complete |
| SETT-03 | Phase 2 | Complete |
| SETT-04 | Phase 4 | Complete |
| SETT-05 | Phase 2 | Complete |
| SETT-06 | Phase 2 | Complete |
| SETT-07 | Phase 2 | Complete |
| SETT-08 | Phase 5 | Complete |
| DEBG-01 | Phase 2 | Complete |
| DEBG-02 | Phase 2 | Complete |
| DEBG-03 | Phase 2 | Complete |
| DEBG-04 | Phase 2 | Complete |
| EPIS-01 | Phase 4 | Complete |
| EPIS-02 | Phase 4 | Complete |
| EPIS-03 | Phase 4 | Complete |
| EPIS-04 | Phase 4 | Complete |
| ADVP-01 | Phase 5 | Complete |
| ADVP-02 | Phase 5 | Complete |
| ADVP-03 | Phase 5 | Complete |
| ADVP-04 | Phase 5 | Complete |

**Coverage:**
- v1 requirements: 41 total
- Mapped to phases: 41
- Unmapped: 0 ✓

---
*Requirements defined: 2026-03-13*
*Last updated: 2026-03-13 after initial definition*
