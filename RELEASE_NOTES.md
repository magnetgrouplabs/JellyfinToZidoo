# 2026.10.1-beta.1: local paths with [ ] in the file name now play

## What changed

**Path substitution to a local drive no longer breaks file names with brackets (issue #5)**
- When a substitution rule maps a Jellyfin path to a local path on the Zidoo, such as `/storage/<id>/...`, the app percent-encoded the result, so a file name containing `[` or `]` came out as `%5B` and `%5D` and did not match the file on disk. Playback failed with "Can't play this file".
- Local paths now stay exactly as written. Only URI targets such as `smb://` are still encoded, so SMB and NFS playback is unchanged.

## Upgrade notes

Install over your existing 2026.09.4 install: your settings are kept. Still requires Jellyfin server 12.0 or later.
