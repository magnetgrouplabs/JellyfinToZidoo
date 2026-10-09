# 2026.10.1-beta.2: file names with [ ] and { } now play from local drives and SMB shares

## What changed

**Path substitution to a local drive no longer breaks file names with brackets (issue #5)**
- When a substitution rule maps a Jellyfin path to a local path on the Zidoo, such as `/storage/<id>/...`, the app percent-encoded the result, so a file name containing `[` or `]` came out as `%5B` and `%5D` and did not match the file on disk. Playback failed with "Can't play this file".
- Local paths now stay exactly as written. Only URI targets such as `smb://` are still encoded, so SMB and NFS playback is unchanged.

**SMB paths with [ ] or { } in the file name now play (issue #5)**
- beta.1 fixed local paths only. A file on an `smb://` share whose name contained `[`, `]`, `{`, `}` or any mix of them still reached the player percent-encoded (`%5B` `%5D` `%7B` `%7D`) and failed with "Can't play video".
- Those characters are now passed literally for SMB targets too, while `#` and `%` are still encoded so file names containing them keep working as they did in beta.1.

## Upgrade notes

Install over your existing 2026.09.4 or 2026.10.1-beta.1 install: your settings are kept. Still requires Jellyfin server 12.0 or later.
