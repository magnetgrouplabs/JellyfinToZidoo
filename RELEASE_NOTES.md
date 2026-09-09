# 2026.09.1: upgraded and optimized for Jellyfin 12

**This version requires Jellyfin server 12.0 or later.** If your server is on Jellyfin 10.11 or earlier, do not upgrade: stay on release 2026.03.1.

## What changed

**Jellyfin 12**
- Intro skip and credit skip now read Jellyfin's native media segments, since Intro Skipper 12 removed the old endpoint this app used
- Mark as watched moved to the current Jellyfin route
- The app now sends its full client identity (app, device, device id, version) on every request instead of a bare token
- Each device now reports its own device id and real app version to the server, instead of a shared hardcoded value

**Security**
- Your Jellyfin password is no longer written to the unencrypted preferences file
- Passwords are excluded from settings export by default; a new switch lets you include them if you want to
- SMB credentials and API tokens are masked in logs and on the debug screen

**Reliability**
- Fixed playback reporting sometimes running twice after Up Next
- Fixed progress being attributed to the previous episode when a binge lookup fails
- Path substitution rules with commas now work correctly in both directions and match by prefix
- Fixed handling of special characters in SMB usernames, passwords, and replacement paths
- Added a guard against crashes on malformed segment responses from the server
- Playback stop and mark as watched now retry once if the final request fails

**Build**
- Release APKs are now signed, so they install by sideload without a security warning
- Continuous integration now runs the unit tests and lint checks on every build
- Dependencies refreshed

## Upgrade notes

Install over your existing app: your settings are kept. If a previous install fails to update because of a signature mismatch, this only affects people who built the app from source themselves; uninstall the old app first, then install this one.
