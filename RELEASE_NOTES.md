# 2026.09.4: playback reporting survives slow opens and player shutdown

## What changed

**Slow-opening files and closing the player no longer stop playback reporting (issue #4)**
- While a file is still opening, and again in the moment the player closes, the Zidoo briefly reports no file at all. The app took that for a switch to a different file, stopped tracking the one you were watching, and never saved where you stopped.
- The app now ignores those moments and keeps tracking the file you are watching, so the resume point is saved in Jellyfin and the position handed back to the launching client is correct.

**A real switch to the next file saves the previous one first**
- When the Zidoo moves on to the next file by itself, the app now saves the previous file's position in Jellyfin before it starts tracking the new one, and never reports the new file's progress against the previous one.

**Clearer logs**
- When the app skips the stop report at the end of playback, the log now says why.

## Upgrade notes

Install over your existing 2026.09.3 or 2026.09.4-beta.1 install: your settings are kept. Still requires Jellyfin server 12.0 or later.
