# JellyfinToZidoo

A bridge between Jellyfin clients and the native Zidoo media player. Play media from any Jellyfin client — the Zidoo handles playback with full hardware decoding (Dolby Vision, DTS, etc.), and watch state syncs seamlessly back to Jellyfin.

## Features

- **Native Zidoo playback** — Launches the Zidoo player via SMB for best-in-class hardware video processing
- **Watch state sync** — Playback progress and watched status report back to Jellyfin automatically
- **Resume position** — Pick up where you left off, synced between Jellyfin and Zidoo
- **Up Next** — Countdown screen between episodes with Play Now / Cancel (like streaming apps)
- **Binge watching** — When Zidoo auto-advances to the next file, each episode is tracked individually in Jellyfin
- **Intro skip** — Automatically skips intros using data from Jellyfin's [Intro Skipper](https://github.com/intro-skipper/intro-skipper) plugin
- **Credit skip** — Stops playback at credits and triggers Up Next early
- **Audio/subtitle passthrough** — Track selections from the Jellyfin client carry through to the Zidoo player
- **Disarm-on-seek** — Manual seeking disables auto-skip so your intent is respected
- **Path substitution** — Up to 10 configurable rules to map Jellyfin server paths to SMB URIs
- **Settings import/export** — Back up and restore configuration (tokens excluded for security)
- **Works with any Jellyfin client** — Moonfin, stock Jellyfin Android TV, Findroid, or anything that sends an external player intent

## Requirements

- **Zidoo** media player (Z9X, Z9X Pro, Z2000, etc.) with firmware **6.4.42+**
- **Zidoo Play mode** set to **"Single file"** (Quick Settings > Playback > Play mode)
- Zidoo must have **SMB access** to the same media files Jellyfin serves
- A **Jellyfin server** with username/password authentication
- Optional: [Intro Skipper](https://github.com/intro-skipper/intro-skipper) plugin on the Jellyfin server for intro/credit skip

## Setup

1. Install the APK on your Zidoo device
2. Open JellyfinToZidoo Settings
3. Enter your Jellyfin server URL and log in
4. Configure path substitution rules to map server paths to SMB paths
   - Example: `/media/tv` → `smb://192.168.1.100/media/tv`
5. In your Jellyfin client, set JellyfinToZidoo as the external player
6. Play something — JellyfinToZidoo handles the rest

## Building

```bash
./gradlew assembleDebug
```

The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

Install via ADB:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## How It Works

1. A Jellyfin client sends a play intent (streaming URL with item ID)
2. JellyfinToZidoo resolves the server-side file path via the Jellyfin API
3. Path substitution converts the server path to an SMB URI
4. The native Zidoo player launches with the SMB path, resume position, and track selections
5. A background poller monitors playback and reports progress to Jellyfin
6. When an episode ends, the Up Next screen offers the next episode or returns to Jellyfin

## Attribution

Forked from [PlexToZidoo](https://github.com/bowlingbeeg/PlexToZidoo) by **bowlingbeeg**.

PlexToZidoo provided the foundational Zidoo player integration, path substitution logic, and SMB playback pipeline that this project builds upon. The original Plex API code is preserved in the source as commented blocks marked with `PLEX_REMOVED` for reference.

Thank you to bowlingbeeg and all PlexToZidoo contributors for their work on Zidoo external player support.

## License

MIT — See [LICENSE](LICENSE) for details.
