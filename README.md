# JellyfinToZidoo

A bridge between Jellyfin clients and the native Zidoo media player.

JellyfinToZidoo intercepts play intents from Jellyfin, converts media paths to SMB paths accessible by the Zidoo, and launches the native Zidoo player for best-in-class local playback.

> **Work in progress** -- This project is under active development. Jellyfin integration is being built in phases.

## Attribution

Forked from [PlexToZidoo](https://github.com/bowlingbeeg/PlexToZidoo) by **bowlingbeeg**.

PlexToZidoo provided the foundational Zidoo player integration, path substitution logic, and SMB playback pipeline that this project builds upon. Thank you to bowlingbeeg and all PlexToZidoo contributors for their work on Zidoo external player support.

## How It Works

1. A Jellyfin client sends a play intent (video URL or stream)
2. JellyfinToZidoo intercepts the intent
3. The media path is converted via user-configured substitution rules (e.g., `/media` becomes `smb://192.168.x.x/media`)
4. The native Zidoo player is launched with the correct SMB path, username, and password

## Requirements

- Zidoo media player with firmware 6.4.42+
- Zidoo needs direct access to Jellyfin media through SMB
- Zidoo Play mode set to "Single file" (Quick Settings > Playback > Play mode)

## Current Status

- Package renamed from PlexToZidoo to JellyfinToZidoo
- Plex API code commented out (preserved as reference with `PLEX_REMOVED` markers)
- Core Zidoo player launch and path substitution logic preserved intact
- Jellyfin server communication -- coming in Phase 2

## Building

```bash
./gradlew assembleDebug
```

The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## License

See [LICENSE](LICENSE) for details. This project is a fork of PlexToZidoo by bowlingbeeg.
