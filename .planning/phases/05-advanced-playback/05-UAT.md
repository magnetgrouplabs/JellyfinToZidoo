---
status: complete
phase: 05-advanced-playback
source: 05-01-SUMMARY.md, 05-02-SUMMARY.md
started: 2026-03-14T19:30:00Z
updated: 2026-03-14T19:35:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Intro Skip
expected: Play an episode with IntroSkipper data. When playback reaches the intro, the player automatically seeks past it.
result: pass

### 2. Credit Skip triggers Up Next
expected: Let an episode play until the credits start (or fast-forward near the end). When the credit timestamp is reached, playback stops and the Up Next countdown screen appears showing the next episode.
result: pass

### 3. Up Next countdown and Play Now
expected: On the Up Next screen, a 10-second countdown ticks down. The next episode info (series name, season/episode number, title) is displayed. Pressing Play Now (or letting the countdown finish) launches the next episode in the Zidoo player.
result: pass

### 4. Up Next Cancel
expected: On the Up Next screen, pressing Cancel returns you to the Jellyfin app instead of playing the next episode.
result: pass

### 5. Disarm-on-seek
expected: During playback, manually seek forward past the intro (or backward). The intro skip should NOT re-trigger after a manual seek.
result: pass

### 6. Audio/Subtitle Track Passthrough
expected: In Jellyfin, select a non-default audio track or subtitle track before launching playback. The Zidoo player should use the selected tracks (not the defaults).
result: pass

### 7. Skip Intros / Skip Credits Settings
expected: In JellyfinToZidoo Settings, there are "Skip Intros" and "Skip Credits" toggles under the Player section. Turning one off should disable that skip behavior during playback.
result: pass

### 8. Settings Export excludes tokens
expected: Export settings from JellyfinToZidoo. The exported JSON should NOT contain jellyfin_access_token or jellyfin_user_id (security — tokens should not be shared).
result: pass (verified by unit tests — 4/4 SettingsExportTest pass)

## Summary

total: 8
passed: 8
issues: 0
pending: 0
skipped: 0

## Gaps

[none]
