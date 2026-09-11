# 2026.09.3: resume position on the official Android TV client

**Still requires Jellyfin server 12.0 or later, same as 2026.09.1.**

## What changed

**Stopping mid playback no longer loses the resume point (issue #4)**
- On the official Jellyfin Android TV client and on Moonfin, stopping a film or episode part way through lost the resume point and marked the item watched. Those clients send their own playback stop when the app hands control back, and the app returned no position with it, which the server reads as played to the end.
- The app now returns the final playback position to the launching client, so the client stores the same resume point the app already saved.
- After an item is watched, or after Up Next hands off to the next episode, the app returns 0 instead. The client then neither wipes a real resume point nor starts its own next episode on top of the one the app launched.
- When playback never started, the app returns the resume point the item already had, so nothing is lost on an error exit.

**Slow-opening files no longer lose their progress**
- On a file that takes a few seconds to open, such as a large 4K remux, the app could mistake the Zidoo's own report of the same file for a jump to a different file, stop tracking it, and never report the stop. The resume point was lost on any client, Wholphin included. The app now recognises the Zidoo's mounted path and the launch path as the same file, which also makes the Zidoo's real auto advance to the next file map correctly.

**Wholphin users see no change**
- Wholphin does not re-report playback, so the client side issue never affected it. It gains the slow-open fix like every other client.

**README**
- The README now names Wholphin as the tested client and explains how the different clients handle the return from an external player.

## Upgrade notes

Install over your existing 2026.09.2 install: your settings are kept.
