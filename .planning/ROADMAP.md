# Roadmap: JellyfinToZidoo

## Overview

JellyfinToZidoo is a fork of PlexToZidoo that replaces the Plex API layer with Jellyfin equivalents, keeping the proven Zidoo integration intact. The roadmap follows a strict dependency chain: fork and clean the codebase, build the core intent-to-player pipeline with auth/settings/debug, add playback lifecycle reporting, layer on episode intelligence (the key differentiator), and finish with advanced playback features. Each phase delivers a testable capability on a real Zidoo device.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [ ] **Phase 1: Fork Setup** - Clean fork with package rename, Plex removal, and verified build
- [ ] **Phase 2: Core Bridge** - End-to-end intent interception through Zidoo player launch with auth, settings, and debug
- [ ] **Phase 3: Playback Lifecycle** - Bidirectional playback reporting and watch state sync with Jellyfin
- [ ] **Phase 4: Episode Intelligence** - Seamless multi-episode tracking when navigating within Zidoo player
- [ ] **Phase 5: Advanced Playback** - Intro/credit skip, audio/subtitle passthrough, and settings portability

## Phase Details

### Phase 1: Fork Setup
**Goal**: A clean, building JellyfinToZidoo project with all Plex code removed and proper attribution
**Depends on**: Nothing (first phase)
**Requirements**: FORK-01, FORK-02, FORK-03, FORK-04, FORK-05
**Success Criteria** (what must be TRUE):
  1. Project builds successfully as com.jellyfintozidoo with zero Plex imports or references
  2. App installs on a Zidoo device alongside PlexToZidoo without conflict
  3. README and license credit PlexToZidoo/bowlingbeeg as the upstream fork
**Plans:** 1/2 plans executed

Plans:
- [ ] 01-01-PLAN.md — Clone PlexToZidoo, rename package, comment out Plex code, update strings
- [ ] 01-02-PLAN.md — Build verification, attribution, CI setup, app icon, device deploy

### Phase 2: Core Bridge
**Goal**: User can play media from a Jellyfin client and have it launch in the native Zidoo player at the correct resume position
**Depends on**: Phase 1
**Requirements**: AUTH-01, AUTH-02, AUTH-03, AUTH-04, BRDG-01, BRDG-02, BRDG-03, BRDG-04, BRDG-05, BRDG-06, SETT-01, SETT-02, SETT-03, SETT-05, SETT-06, SETT-07, DEBG-01, DEBG-02, DEBG-03, DEBG-04
**Success Criteria** (what must be TRUE):
  1. User can configure Jellyfin server URL and credentials in a D-pad-navigable settings screen
  2. Pressing play on a Jellyfin client launches the Zidoo player with the correct SMB file via path substitution
  3. Playback resumes at the position stored in Jellyfin (ticks converted to milliseconds)
  4. Debug screen shows the full pipeline: raw intent data, resolved path, substituted SMB URI, with a manual Play button
  5. Auth token persists across app restarts without re-entering credentials
**Plans**: TBD

Plans:
- [ ] 02-01: TBD
- [ ] 02-02: TBD
- [ ] 02-03: TBD
- [ ] 02-04: TBD

### Phase 3: Playback Lifecycle
**Goal**: Jellyfin accurately reflects what the user watched, how far they got, and what to resume next
**Depends on**: Phase 2
**Requirements**: PLAY-01, PLAY-02, PLAY-03, PLAY-04, PLAY-05, PLAY-06
**Success Criteria** (what must be TRUE):
  1. Jellyfin shows "Now Playing" during active Zidoo playback
  2. Stopping playback mid-movie saves the exact resume position in Jellyfin
  3. Watching 90% or more of a movie marks it as watched in Jellyfin
  4. After playback ends, the originating Jellyfin client relaunches (Zidoo 2-app limit workaround)
**Plans**: TBD

Plans:
- [ ] 03-01: TBD
- [ ] 03-02: TBD
- [ ] 03-03: TBD

### Phase 4: Episode Intelligence
**Goal**: Users can binge TV shows by navigating episodes directly in the Zidoo player, with every episode's watch status correctly synced to Jellyfin
**Depends on**: Phase 3
**Requirements**: EPIS-01, EPIS-02, EPIS-03, EPIS-04, SETT-04
**Success Criteria** (what must be TRUE):
  1. Pressing "next episode" in the Zidoo player marks the previous episode as watched in Jellyfin
  2. Each episode navigated to gets its own playback start/progress/stop reporting in Jellyfin
  3. Next episode auto-plays after current episode finishes (including across season boundaries)
  4. Multiple path substitution rules can be configured for multi-share setups
**Plans**: TBD

Plans:
- [ ] 04-01: TBD
- [ ] 04-02: TBD
- [ ] 04-03: TBD

### Phase 5: Advanced Playback
**Goal**: Polish the playback experience with intro/credit handling, stream selection, and settings portability
**Depends on**: Phase 4
**Requirements**: ADVP-01, ADVP-02, ADVP-03, ADVP-04, SETT-08
**Success Criteria** (what must be TRUE):
  1. Intros are skipped automatically using Jellyfin Intro Skipper plugin data
  2. Playback stops or advances at credits using Intro Skipper outro timestamps
  3. Audio and subtitle track selections from the Jellyfin client are applied in the Zidoo player
  4. User can export settings to JSON and import them on another device
**Plans**: TBD

Plans:
- [ ] 05-01: TBD
- [ ] 05-02: TBD
- [ ] 05-03: TBD

## Progress

**Execution Order:**
Phases execute in numeric order: 1 > 2 > 3 > 4 > 5

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Fork Setup | 1/2 | In Progress|  |
| 2. Core Bridge | 0/4 | Not started | - |
| 3. Playback Lifecycle | 0/3 | Not started | - |
| 4. Episode Intelligence | 0/3 | Not started | - |
| 5. Advanced Playback | 0/3 | Not started | - |
