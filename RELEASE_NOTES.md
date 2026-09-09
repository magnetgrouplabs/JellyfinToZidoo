# 2026.09.2: playback polish

**Still requires Jellyfin server 12.0 or later, same as 2026.09.1.**

## What changed

**Play from beginning now works**
- Choosing "Play from beginning" in the Jellyfin client on a partially watched item used to resume at the saved position instead. The app now honors the client's explicit start position, including zero, and only falls back to the server's resume point when the client sends none.

**Pausing right after playback starts no longer resumes on its own**
- The app now waits until the player is confirmed playing before applying the audio and subtitle track selection, and it reports the paused state to Jellyfin instead of always reporting playing.

**Smarter early stop**
- Previously, every episode stopped 30 seconds before the end so Up Next could appear, even when there was no credits data. This sometimes cut off cold endings and mid-credits scenes.
- Now the episode only stops early when Intro Skipper has marked a credits segment for it. Otherwise the episode plays all the way through, and Up Next appears right after.

**Settings cleanup**
- Removed the Auto Play switch in Settings. It never actually changed anything since Up Next always ran, so it was misleading. Up Next itself works exactly the same as before.

**Smaller app**
- Removed leftover code from this app's Plex origins that had been sitting unused since the fork. No visible change, just a smaller install. The Kodi/ZDMC integration is unaffected.

## Upgrade notes

Install over your existing 2026.09.1 install: your settings are kept.
