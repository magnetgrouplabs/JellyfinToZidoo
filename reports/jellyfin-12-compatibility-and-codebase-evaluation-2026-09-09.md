# JellyfinToZidoo: Jellyfin 12.0 compatibility and codebase evaluation

Date: 2026-09-09. Repo `C:\Users\anthony\claude-projects\JellyfinToZidoo` at commit `f21f5ff` (main, clean). Server: MKZFlix, Jellyfin 12.0.0, Intro Skipper 12.0.3.0, legacy authorization off.

## Executive summary

1. The app works against Jellyfin 12.0 today except one feature: intro and credit skipping is dead. Twelve of thirteen server calls pass; the auth headers are already the only form 12.0 accepts, and the app never parses the server version, so the 10.x to 12.0 rename costs nothing.
2. The break is not Jellyfin's, it is Intro Skipper's: its 12.0 line deleted the `/Episode/{id}/IntroSkipperSegments` route the app calls. The live server answers 404 and the app swallows it silently, so every episode now plays with no skips and no error.
3. Fix: switch to Jellyfin's native `GET /MediaSegments/{itemId}` (same token header, live probe 401 as expected) and rewrite the parser for its shape (Items list, Type Intro/Outro, ticks not seconds). One method, one parser, one test class. About an hour.
4. One more call, mark as watched, uses an obsolete hidden route that still works; a one line move to `/UserPlayedItems/{itemId}` retires it before a future release drops it.
5. The Fable evaluation found three P0 problems unrelated to Jellyfin 12: the published release APK is unsigned and cannot be installed; the Jellyfin password is silently written in plaintext to default preferences, exported to Downloads, and the SMB password is logged; and CI never runs the 86 unit tests.
6. All 86 unit tests pass locally. Lint: 1 error (missing super call), 32 warnings, mostly 2020 era dependency versions.
7. Needs your approval: whether I make the Jellyfin 12 fix now, whether the P0 items go in the same pass, and the product decisions under "Decisions for you". Nothing has been changed in the repo.

## 1. Jellyfin 12.0 compatibility

Method: every HTTP call in the app was inventoried with file and line, checked against the 10.11.11 and 12.0 OpenAPI documents, the v12.0 controller sources on GitHub, the Intro Skipper 10.11 and 12.0 branches, and the four Jellyfin 12 research reports in `homelab/reports`. Then each route was probed on the live server with unauthenticated GETs only (401 or 405 proves a route exists, 404 proves it is gone; no POSTs, no credentials). Full inventory and probe table in Appendix A.

| Call | Route | Status on 12.0 |
|---|---|---|
| Resolve item, get path and streams | GET `/Items/{id}?Fields=Path,MediaSources` | Works |
| Login | POST `/Users/AuthenticateByName` | Works |
| Playback start, progress, stopped | POST `/Sessions/Playing`, `/Progress`, `/Stopped` | Works, already on the current forms |
| Mark as watched | POST `/Users/{userId}/PlayedItems/{itemId}` | Works, obsolete hidden route, move to `/UserPlayedItems/{itemId}` |
| Up Next lookup | GET `/Shows/NextUp` | Works |
| Server name check | GET `/System/Info` | Works |
| Binge fallback search | GET `/Items?searchTerm=...&Limit=10` | Works; 12.0 re-ranks search results so the exact path match could fall outside the top 10 on common titles |
| Intro and credit segments | GET `/Episode/{id}/IntroSkipperSegments` | Broken: route removed by Intro Skipper 12.0, live 404 |
| Backdrop and thumbnail images | GET `/Items/{id}/Images/...` | Works, still anonymous |
| Inbound URL regex, query parsing, version handling | n/a | No change needed; no `/emby`, no `X-Emby-*`, no `api_key`, no version parse anywhere |

Required change, `JellyfinApi.java:1193-1227` and the parser at `:169-193`:

- Call `GET /MediaSegments/{itemId}` with the existing Authorization header.
- The response is `{"Items":[{"Id","ItemId","Type","StartTicks","EndTicks"}, ...]}`. Type `Intro` is the introduction, `Outro` is the credits (confirmed in the plugin's `AnalysisHelpers.cs` on the 12.0 branch).
- Start and end are .NET ticks; use the existing `ticksToMs` instead of the seconds times 1000 conversion.
- Rewrite `IntroSkipperApiTest.java` for the new shape.
- Keep the 404 swallow so servers without the plugin stay optional.

Recommended in the same pass: `JellyfinApi.java:894` to `/UserPlayedItems/{itemId}` (the `userId` argument becomes unused), and the four short `MediaBrowser Token=` headers switched to the full header the POST calls already use.

## 2. Codebase evaluation (Fable)

Full text in Appendix B. Overall: the pipeline is sound and the parsing layer is clean, null checked and tested, but the app has never had a release quality pass. The findings that matter cluster in four places.

P0, fix now (each under two hours):

| # | Finding | Evidence |
|---|---|---|
| P0-1 | The published release APK is unsigned and uninstallable. The release workflow renames `app-release-unsigned.apk` and publishes it; there is no signing config. The agent downloaded v2026.03.1 and confirmed no signature block. | `.github/workflows/release.yml:37-41`, `app/build.gradle` (no signingConfigs) |
| P0-2 | Jellyfin password persisted in plaintext to default SharedPreferences on every Settings open, because the preference is persistent and `setText` is called with the real value. SecureStorage is decorative for the password. | `root_preferences.xml:22-28`, `SettingsActivity.java:138`, `:379` |
| P0-3 | SMB password logged to logcat inside the `smb://user:pass@host` path; unquoted password used as a regex in the masking code; the debug screen shows the client's `api_key` from the inbound URL. | `Play.java:1047`, `:1465`, `:262`, `:1645-1669` |
| P0-4 | Crash path: intro segment parsing runs on the main thread with no try/catch; any non JSON 200 body (proxy error page) kills the app mid playback. | `Play.java:902`, `JellyfinApi.java:169-193` |
| P0-5 | CI runs `assembleDebug` only; the unit tests have never run in CI. `junit:4.+` is unpinned. | `build.yml:35-36`, `app/build.gradle:46` |
| P0-6 | `handoff.md` on disk holds a plaintext SMB password and a Jellyfin API key. It is gitignored and was never committed (verified across all refs), so it never left the machine, but it should be scrubbed. | `handoff.md` lines 60 and 64 |

P1, next release: duplicate poller window and unsynchronized per episode state in the playback loop (`Play.java:1015-1276`, `:1397-1402`); binge lookup can attribute progress and the final stop to the previous episode (`JellyfinApi.java:1126-1142`); forward and reverse path substitution are not inverses (comma split on one side only, `contains` vs `startsWith`, `$` in replacement throws); export writes all passwords to a world readable file while the UI string says it does not; `auto_play` toggle is never read; an unconditional 30 second early stop on every episode that README does not mention; every Zidoo presents the same hardcoded DeviceId to the server; dependency refresh (AGP 8.2.2, appcompat 1.2.0, material 1.2.1, gson 2.8.6, security-crypto alpha).

P2: collapse `JellyfinApi` into an instance client, split `Play.java`, Storage Access Framework so targetSdk can leave 28, backup rules, MockWebServer tests, housekeeping.

## 3. Build, tests and lint (run locally today)

| Check | Result |
|---|---|
| `gradlew testDebugUnitTest` | BUILD SUCCESSFUL, 86 tests in 8 classes, 0 failures, 0 skipped |
| `gradlew lintDebug` | 1 error: `MissingSuperCall` (`onRequestPermissionsResult` does not call super). 32 warnings: 9 outdated dependencies, 7 unused resources, 3 hardcoded text, 1 dynamic version (`junit:4.+`), rest cosmetic |
| Toolchain | JDK 17 Temurin, Android SDK at `C:\Users\anthony\Android\Sdk`, Gradle 8.5 wrapper downloaded fresh |

Note: the first test attempt failed on a stale locked `app/build` folder from August; deleting the ignored build output cleared it.

## 4. Decisions for you

1. Make the Jellyfin 12 fix now (MediaSegments migration plus the mark-as-watched route move)? Recommended yes.
2. Include the P0 items in the same pass (signing, password persistence, logging, crash guard, CI tests, handoff scrub)? Signing needs a release keystore: if none exists I would generate one and store it as GitHub repository secrets, which needs your go.
3. Plex reference material: `handoff.md` records your instruction to keep the commented Plex code and the four empty Plex classes. Keep as is, or move to a docs file or reference branch?
4. The unconditional 30 second early stop on every TV episode: keep (document it, gate it on `auto_play`) or remove?
5. Settings export: exclude passwords by default with an "include passwords" checkbox (recommended), or keep exporting them and fix the UI text?
6. targetSdk 28: stay (Zidoo runs Android 9 and 11, sideload only) or plan the storage rewrite to move up? Recommended stay for this release.

Nothing in the repo was modified. This `reports/` folder is untracked and not committed; the repo is public, so say the word before any commit that includes it.


---

# Appendix A: Jellyfin 12 compatibility audit (full findings)

# JellyfinToZidoo compatibility audit against Jellyfin 12.0

Audited 2026-09-09 against Anthony's server https://jellyfin.mkznet.me, confirmed live at `Version` `12.0.0`, `ServerName` `MKZFlix`, with `EnableLegacyAuthorization` false and Intro Skipper 12.0.3.0.

Repo audited: `C:\Users\anthony\claude-projects\JellyfinToZidoo` (read only, nothing edited). All probes against the live server were unauthenticated GETs with no headers and no credentials.

## (a) Verdict

The app works against Jellyfin 12.0 today except for one feature: intro and credit skipping is dead. Twelve of the thirteen HTTP interactions are fine, and none of them are affected by the 12.0 auth tightening, because the app already uses the only supported scheme (`Authorization: MediaBrowser ...`) and never touches `/emby`, `/mediabrowser`, `X-Emby-*` or `?api_key=`. Playback resolution, playback reporting to `/Sessions/Playing*`, Up Next, mark as watched, the path search fallback and the settings login all keep working, and the live probes confirm every one of those routes still resolves on the server. The single break is `GET /Episode/{id}/IntroSkipperSegments` (JellyfinApi.java:1196): Intro Skipper removed that route in its 12.0 line and the live server answers it with a Jellyfin-generated 404. The app swallows the 404 silently (JellyfinApi.java:1213-1218), so nothing crashes, but every episode now plays with no intro skip and no credit skip and no user-visible error. The replacement is Jellyfin's own `GET /MediaSegments/{itemId}`, which Intro Skipper 12.0 mirrors its segments into. Two further items work but are living on borrowed time: `POST /Users/{userId}/PlayedItems/{itemId}` (JellyfinApi.java:894) is an `[Obsolete]` compatibility route hidden from the OpenAPI document, and the client's short `MediaBrowser Token="..."` header, while accepted, relies on the server backfilling the missing device fields. The app does no server version-string parsing at all, so the 10.x to 12.0 rename breaks nothing in it.

## (b) Inventory and classification

Auth header forms used, both in JellyfinApi.java:

- short: `MediaBrowser Token="<token>"` (`buildAuthHeader`, JellyfinApi.java:399-401)
- full: `MediaBrowser Client="JellyfinToZidoo", Device="Zidoo", DeviceId="jellyfintozidoo", Version="1.0.0", Token="<token>"` (`buildFullAuthHeader`, JellyfinApi.java:815-818)

Both are the `MediaBrowser` scheme on the `Authorization` header, which is one of the only two mechanisms a default 12.0 server accepts. No `X-Emby-Authorization`, no `X-Emby-Token`, no `X-MediaBrowser-Token`, no `?api_key=`, no `?ApiKey=`, no `/emby` or `/mediabrowser` prefix appears anywhere in the app's own request construction. (`api_key` appears only inside test fixture strings, JellyfinUrlParserTest.java:13 and :47, as part of a sample inbound URL whose query the app never reads for auth.)

| # | Interaction | Method and path (query, Fields) | Auth header | file:line | Verdict | Citation |
|---|---|---|---|---|---|---|
| 1 | `getItem` | GET `/Items/{itemId}?Fields=Path,MediaSources` | short | JellyfinApi.java:653, url :656, header :660 | WORKS UNCHANGED | Route present in both specs (`/Items/{itemId}` get). Its 12.0 signature takes only `itemId` and `userId`, so `Fields` is inert and always was; the DTO is built with `new DtoOptions()`, which enables every `ItemFields` value: UserLibraryController.cs:82-107 at v12.0 and DtoOptions.cs:28-45 at v12.0. `Path`, `MediaSources`, `RunTimeTicks`, `SeriesId`, `Name`, `UserData` are all still on `BaseItemDto` (155 properties, none removed vs 10.11.11, two added). |
| 2 | `getItemDetailed` | GET `/Items/{itemId}?Fields=Path,MediaSources` | short | JellyfinApi.java:698, url :700, header :704 | WORKS UNCHANGED | Same route and same evidence as row 1. `MediaSources[0].MediaStreams` with `Index`, `Type`, `IsDefault`, `IsForced` (parsed at JellyfinApi.java:204-296) all survive: `MediaSourceInfo` 45 properties unchanged, `MediaStream` gains three properties and loses none. |
| 3 | `authenticate` | POST `/Users/AuthenticateByName`, body `{Username, Pw}` | `MediaBrowser Client/Device/DeviceId/Version`, no Token | JellyfinApi.java:755, url :757, header :765, body :759-761 | WORKS UNCHANGED | Present in both specs; request schema `AuthenticateUserByName` and response `AuthenticationResult` (4 properties) identical between 10.11.11 and 12.0. The app reads `AccessToken`, `User.Id`, `ServerId` (JellyfinApi.java:788-793), all still present. One 12.0 behavior note, not a break: usernames are now case insensitive. |
| 4 | `reportPlaybackStart` | POST `/Sessions/Playing`, body `{ItemId, PlaySessionId, CanSeek, PlayMethod:"DirectPlay", PositionTicks}` | full | JellyfinApi.java:826, url :829, header :835, body builder :343-351 | WORKS UNCHANGED | `PlaystateController.cs` v12.0 line 201 `[HttpPost("Sessions/Playing")]`, no `[Obsolete]`. `PlaybackStartInfo` unchanged at 21 properties. Live probe: GET returns 405, route present. |
| 5 | `reportPlaybackProgress` | POST `/Sessions/Playing/Progress`, body `{ItemId, PlaySessionId, PositionTicks, IsPaused, CanSeek}` | full | JellyfinApi.java:847, url :851, header :857, body builder :357-366 | WORKS UNCHANGED | `PlaystateController.cs` v12.0 line 217. `PlaybackProgressInfo` unchanged at 21 properties. Live probe 405. |
| 6 | `reportPlaybackStopped` | POST `/Sessions/Playing/Stopped`, body `{ItemId, PlaySessionId, PositionTicks}` | full | JellyfinApi.java:869, url :873, header :879, body builder :372-379 | WORKS UNCHANGED | `PlaystateController.cs` v12.0 line 247. `PlaybackStopInfo` unchanged at 11 properties. Live probe 405. |
| 7 | `markAsWatched` | POST `/Users/{userId}/PlayedItems/{itemId}`, empty body | full | JellyfinApi.java:891, url :894, header :898, body :899 | DEPRECATED BUT WORKING | The route exists at v12.0 but is an explicit compatibility shim. `Jellyfin.Api/Controllers/PlaystateController.cs` at v12.0, lines 120-129: `[HttpPost("Users/{userId}/PlayedItems/{itemId}")]`, `[ProducesResponseType(StatusCodes.Status200OK)]`, `[ProducesResponseType(StatusCodes.Status404NotFound)]`, `[Obsolete("Kept for backwards compatibility")]`, `[ApiExplorerSettings(IgnoreApi = true)]`, forwarding to `MarkPlayedItem`. Absent from both the 10.11.11 and the 12.0 OpenAPI documents because of that `IgnoreApi`. Live probe: GET returns 405, so the route is registered and only the verb is wrong; a 404 would have meant it was gone. |
| 8 | `getNextUp` | GET `/Shows/NextUp?seriesId={id}&limit=1` | full | JellyfinApi.java:916, url :919, header :923 | WORKS UNCHANGED | `TvShowsController.cs` at v12.0, class `[Route("Shows")]` line 28, `[HttpGet("NextUp")]` line 75. `userId` is optional and resolved from the token by `RequestHelpers.GetUserId(User, userId)`. Spec parameter diff 10.11.11 to 12.0: only `disableFirstEpisode` removed, which the app does not send. Live probe 401, route present. |
| 9 | `testConnection` | GET `/System/Info` | short | JellyfinApi.java:999, url :1001, header :1005 | WORKS UNCHANGED | Present in both specs, `SystemInfo` unchanged at 27 properties. The app reads only `ServerName` (JellyfinApi.java:1026-1029) and does not touch `Version`. Live probe 401, route present. |
| 10 | `getNextUpWithDetails` | GET `/Shows/NextUp?seriesId={id}&userId={uid}&limit=1&Fields=Path,MediaSources` | full | JellyfinApi.java:1062, url :1065, header :1069 | WORKS UNCHANGED | Same route evidence as row 8. `Fields` binds case insensitively to the `fields` parameter through `CommaDelimitedCollectionModelBinder`; `Path` and `MediaSources` are both still valid `ItemFields` values (the enum is byte for byte identical between 10.11.11 and 12.0). Parsed fields `Id`, `SeriesName`, `Name`, `ParentIndexNumber`, `IndexNumber`, `SeriesId`, `Path` (JellyfinApi.java:539-604) all still on `BaseItemDto`. |
| 11 | `searchItemByPath` | GET `/Items?searchTerm={name}&IncludeItemTypes=Episode&Fields=Path,MediaSources&Recursive=true&Limit=10` | full | JellyfinApi.java:1126, url :1141-1142, header :1146 | WORKS, ONE BEHAVIOR RISK | Route present in both specs; parameter diff adds only `audioLanguages` and `subtitleLanguages`, removes nothing. The 12.0 change that "`GET /Items` applies `recursive` when `includeItemTypes` is present" cannot affect the app because it already passes `Recursive=true` explicitly (`ItemsController.cs` v12.0 lines 335-339). The real risk is ranking: when `searchTerm` is set, 12.0 routes the query through the new `ISearchManager` search providers instead of the old SQL search, asks the provider for `limit * 3` candidates and re-scores them (`ItemsController.cs` v12.0 lines 353-374 and 447). The app takes `Limit=10` and then does an exact `Path` string match over the returned items (`parseSearchByPathResponse`, JellyfinApi.java:605-651), so a changed ranking could push the correct episode out of the top 10 on a common title. Classify as UNKNOWN until exercised against real data; it is a fallback path used only when the inbound URL is not a Jellyfin stream URL. |
| 12 | `getIntroSkipperSegments` | GET `/Episode/{itemId}/IntroSkipperSegments`, parses `Introduction.Start`, `Introduction.End`, `Credits.Start`, `Credits.End` as seconds | short | JellyfinApi.java:1193, url :1196, header :1200, parser :169-193 | **BROKEN** | The route was removed in Intro Skipper's 12.0 line. It exists on the 10.11 branch: `IntroSkipper/Controllers/SkipIntroController.cs` line 142, `[HttpGet("Episode/{id}/IntroSkipperSegments")]` (https://raw.githubusercontent.com/intro-skipper/intro-skipper/refs/heads/10.11/IntroSkipper/Controllers/SkipIntroController.cs). At tag `12.0/v12.0.3.0` that file has only `[HttpPost("Intros/EraseTimestamps")]` (line 47) and `[HttpPost("Intros/RebuildDatabase")]` (line 80), and the string `IntroSkipperSegments` occurs zero times in any of the seven controllers on the 12.0 branch. Absent from both OpenAPI documents (it is a plugin route). Live probe: 404 with `X-Response-Time-ms` and `Content-Language: en-US`, that is Jellyfin itself answering with no route match, not a proxy 404. |
| 13 | Image URLs loaded by Glide, no `Authorization` header | GET `/Items/{seriesId}/Images/Backdrop` and GET `/Items/{itemId}/Images/Primary?maxWidth=640` | none | Play.java:1364 (backdrop URL built), UpNextActivity.java:94 (primary URL built), loaded UpNextActivity.java:80-102 | WORKS UNCHANGED | `/Items/{itemId}/Images/{imageType}` present with `get` and `head` in both specs. Live probe with no credentials returned 400 (bad GUID), not 401, which confirms the image routes are still anonymous in 12.0. One cosmetic 12.0 change: image endpoints no longer upscale past the source resolution, so a low resolution backdrop or thumbnail now renders smaller rather than being enlarged. |

Non-HTTP items that were checked:

| Item | file:line | Finding |
|---|---|---|
| Inbound intent URL regex | JellyfinApi.java:36-39, `"/Videos/([a-f0-9]{32}\|[a-f0-9-]{36})/stream"`, case insensitive; used by `extractItemId` JellyfinApi.java:309-316 | WORKS UNCHANGED. It matches the path shape the Jellyfin Android client hands over. `/Videos/{itemId}/stream` is still in the 12.0 spec with `get` and `head`. The regex would not match an `/emby/Videos/.../stream` prefix, but since 12.0 removed that prefix entirely no client can send it any more, so nothing regresses. Tests: JellyfinUrlParserTest.java:11-55. |
| Query parameters read off the inbound URL | `parseUrlParam`, JellyfinApi.java:252-271; called for `AudioStreamIndex` and `SubtitleStreamIndex` at Play.java:828-829 | WORKS UNCHANGED. These are read out of the URL the calling client supplies, not sent to the server. The app never reads `api_key` from the inbound URL, so the server's rejection of `?api_key=` is irrelevant to it. |
| Outbound intent result URI | Play.java:1411, `serverUrl + "/Videos/" + jellyfinItemId + "/stream"` | WORKS UNCHANGED. Handed back to the calling Jellyfin client as `setResult` data with no token, purely so that client can identify the last played item. Not an HTTP request from this app. |
| Server version parsing | none found | The repo contains no comparison or parse of a Jellyfin server version. `testConnection` reads only `ServerName` (JellyfinApi.java:1026-1029) and discards the rest of `SystemInfo`. Grepped the whole app source tree for `ServerVersion`, `"Version"` reads, and `10.` prefixes; the only `10.` hits are `Start: 10.0` inside IntroSkipperApiTest.java:40 and :72. So the 10.x to 12.0 version-string rename, which the release blog explicitly warns about, costs this app nothing. |
| The client's own `Version="1.0.0"` | JellyfinApi.java:765 and :817 | Harmless. It is the client application's self-reported version, stored on the device row as `AppVersion`. `AuthorizationContext.cs` at v12.0 lines 167-178 backfills it from the device when absent and updates the device when it differs; there is no minimum client version check. |
| Short auth header with no `Client`/`Device`/`DeviceId` | `buildAuthHeader`, JellyfinApi.java:399-401, used by rows 1, 2, 9, 12 | Accepted. `AuthorizationContext.cs` at v12.0 lines 86-90 reads the four fields as optional, and lines 141-213 backfill any that are missing from the device row (user token) or from the server identity (API key). Not a 12.0 change, but worth knowing it depends on server-side backfill rather than on the request. |

## (c) Live route-existence probe

All requests were plain unauthenticated `GET` with no headers, no token, no API key, against `https://jellyfin.mkznet.me` on 2026-09-09.

| Status | Request | Reading |
|---|---|---|
| 200 | GET `/System/Info/Public` | Body: `{"LocalAddress":"http://[::1]:8096","ServerName":"MKZFlix","Version":"12.0.0","ProductName":"Jellyfin Server","OperatingSystem":"","Id":"66c5dbca283149ac91e39c878cd55e4e","StartupWizardCompleted":true}`. Version confirmed 12.0.0. |
| 401 | GET `/System/Info` | Route exists, auth required, as expected. |
| 401 | GET `/Shows/NextUp` | Route exists. |
| 405 | GET `/Users/00000000000000000000000000000000/PlayedItems/00000000000000000000000000000000` | Route exists and only the verb is wrong, so the legacy POST form the app uses is still registered. A 404 here would have meant removal. No POST was ever sent. |
| 405 | GET `/UserPlayedItems/00000000000000000000000000000000` | The modern replacement route also exists. |
| 404 | GET `/Episode/00000000000000000000000000000000/IntroSkipperSegments` | Route gone. Answered by Jellyfin itself (`X-Response-Time-ms: 0.0413`, `Content-Language: en-US`, `Content-Length: 0`), not by the reverse proxy. |
| 401 | GET `/Episode/00000000000000000000000000000000/Segments` | Intro Skipper 12.0's own segment route exists, but it is admin only (`[Authorize(Policy = Policies.RequiresElevation)]`). |
| 401 | GET `/MediaSegments/00000000000000000000000000000000` | Jellyfin's native media segment route exists and needs only a normal authenticated user. This is the replacement. |
| 404 | GET `/emby/System/Info/Public` | Legacy prefix gone, as the 12.0 release notes state. |
| 404 | GET `/mediabrowser/System/Info/Public` | Legacy prefix gone. |
| 405 | GET `/Sessions/Playing` | Route exists, POST only. |
| 405 | GET `/Sessions/Playing/Progress` | Route exists, POST only. |
| 405 | GET `/Sessions/Playing/Stopped` | Route exists, POST only. |
| 401 | GET `/Items` | Route exists. |
| 401 | GET `/Items/00000000000000000000000000000000` | Route exists. |
| 401 | GET `/Users/AuthenticateByName` | Route registered (the POST form is in the 12.0 spec). |
| 400 | GET `/Videos/00000000000000000000000000000000/stream` | Route exists and is anonymous; 400 is the all-zero GUID being rejected. |
| 400 | GET `/Items/00000000000000000000000000000000/Images/Primary` | Route exists and is anonymous; 400 is the all-zero GUID. Confirms the unauthenticated Glide image loads still work. |

## (d) Required changes

### 1. Required now: replace the Intro Skipper endpoint

`JellyfinApi.getIntroSkipperSegments`, JellyfinApi.java:1193-1226, and its parser `parseIntroSkipperResponse`, JellyfinApi.java:169-193.

Change the URL at **JellyfinApi.java:1196** from

```
String url = baseUrl + "/Episode/" + itemId + "/IntroSkipperSegments";
```

to Jellyfin's native media segment route, optionally filtered:

```
String url = baseUrl + "/MediaSegments/" + itemId + "?includeSegmentTypes=Intro&includeSegmentTypes=Outro";
```

Keep the existing `Authorization` header at JellyfinApi.java:1200 as is; `MediaSegmentsController` at v12.0 carries a plain `[Authorize]` (line 21), no elevation, and the live 401 probe agrees.

Then rewrite the parser at **JellyfinApi.java:169-193**. The shape is different in three ways and all three must change together:

- The response is a `QueryResult<MediaSegmentDto>`, that is `{"Items":[...],"TotalRecordCount":n,"StartIndex":0}`, not a single object with `Introduction` and `Credits` keys. Iterate `Items`.
- Each element is `{"Id","ItemId","Type","StartTicks","EndTicks"}`. Select on `Type`: `"Intro"` is what the app calls Introduction and `"Outro"` is what it calls Credits. Mapping confirmed in the plugin at `IntroSkipper/Data/AnalysisHelpers.cs` on the 12.0 branch, lines 20 and 23: `[AnalysisMode.Introduction] = MediaSegmentType.Intro` and `[AnalysisMode.Credits] = MediaSegmentType.Outro`. The full enum is `Unknown, Commercial, Preview, Recap, Outro, Intro`.
- `StartTicks` and `EndTicks` are .NET ticks (long), not seconds (double). The app already has the converter: use `JellyfinApi.ticksToMs` (JellyfinApi.java:325-327) instead of the `* 1000` in `IntroSkipperResult.introStartMs()` and its three siblings at JellyfinApi.java:155-158. The cleanest change is to store milliseconds in `IntroSkipperResult` directly and drop the seconds fields.

The 404 swallow at JellyfinApi.java:1213-1218 can stay; it is what stops the current breakage from crashing anything, and it keeps the feature optional for servers without the plugin.

Tests to update in the same change: IntroSkipperApiTest.java, all of it (it asserts the old `Introduction`/`Credits` seconds shape at lines 15-60 and beyond).

One behavior note to expect after the fix: Intro Skipper 12.0 ships `IntroSkipper/Filters/MediaSegmentsFirstEpisodeFilter.cs`, which at line 179 strips `MediaSegmentType.Intro` segments from the first episode of a season when that option is on. An empty result for episode 1 is therefore normal, not a bug.

Required now, because the feature is currently dead on Anthony's server.

### 2. Required soon: move off the legacy mark-as-watched route

`JellyfinApi.markAsWatched`, url at **JellyfinApi.java:894**:

```
String url = baseUrl + "/Users/" + userId + "/PlayedItems/" + itemId;
```

becomes

```
String url = baseUrl + "/UserPlayedItems/" + itemId;
```

The `userId` parameter of `markAsWatched` (JellyfinApi.java:891) becomes unused; the server resolves the user from the token. Callers are Play.java:1301 and Play.java:1604. Method, headers and empty body stay as they are.

Timing: this is not urgent. The 12.0 route carries `[Obsolete("Kept for backwards compatibility")]`, not a removal notice, and the research reports found no Jellyfin release that has announced its removal, so no release can be named here. What can be named is the general policy the 12.0 release notes state: "If an endpoint isn't listed in the OpenAPI specification it should not be used by clients ... These can be removed in any major release without warning." This route is hidden from the specification by `[ApiExplorerSettings(IgnoreApi = true)]`, so it sits squarely inside that policy. Do it whenever the file is next open.

### 3. Optional: use the full auth header everywhere

Four calls use the short `MediaBrowser Token="..."` form: JellyfinApi.java:660, :704, :1005, :1200. They work, because `AuthorizationContext.cs` at v12.0 backfills `Client`, `Device`, `DeviceId` and `Version` from the device row. Switching them to `buildFullAuthHeader` (JellyfinApi.java:815-818) removes that dependency and makes the four calls consistent with the rest. Cosmetic, no deadline.

### 4. Optional: guard the path search fallback

`searchItemByPath`, JellyfinApi.java:1126-1183, takes the first 10 results and exact-matches on `Path`. Since 12.0 re-ranks `searchTerm` queries through the new search providers, consider raising `Limit=10` (JellyfinApi.java:1142) or, better, replacing the search with `GET /Items?path=...`-style filtering if a suitable filter exists, so correctness does not depend on ranking. This is a robustness improvement, not a known break; nothing was observed failing.

### 5. Nothing to do

- Auth scheme, headers and prefixes: already correct for a default 12.0 server. No change needed for `EnableLegacyAuthorization` being false.
- Version parsing: none exists, so the 10.x to 12.0 rename needs no code change.
- `/Sessions/Playing`, `/Sessions/Playing/Progress`, `/Sessions/Playing/Stopped`: already on the current forms, not the removed `/PlayingItems/{itemId}` ones.
- `/Shows/NextUp`, `/Items`, `/Items/{itemId}`, `/Users/AuthenticateByName`, `/System/Info`, the image routes and `/Videos/{id}/stream`: unchanged.

## (e) Sources

Every URL below was opened for this audit.

Prior research read first (all four, in full):
- `C:\Users\anthony\claude-projects\homelab\reports\jellyfin-12-api-and-plugin-changes-2026-09-09.md`
- `C:\Users\anthony\claude-projects\homelab\reports\jellyfin-12-plugin-compatibility-check-2026-09-09.md`
- `C:\Users\anthony\claude-projects\homelab\reports\jellyfin-12-plugin-state-after-cutover-2026-09-09.md`
- `C:\Users\anthony\claude-projects\homelab\reports\jellyfin-12-plugin-importance-2026-09-09.md`

OpenAPI specifications, downloaded to the scratchpad and diffed path by path and schema by schema:
- https://repo.jellyfin.org/files/openapi/stable/jellyfin-openapi-12.0.json (`info.version` 12.0.0, 294 paths)
- https://repo.jellyfin.org/files/openapi/stable/jellyfin-openapi-10.11.11.json (`info.version` 10.11.11, 315 paths)

Jellyfin server source at tag v12.0:
- https://raw.githubusercontent.com/jellyfin/jellyfin/v12.0/Jellyfin.Api/Controllers/PlaystateController.cs
- https://raw.githubusercontent.com/jellyfin/jellyfin/v12.0/Jellyfin.Api/Controllers/UserLibraryController.cs
- https://raw.githubusercontent.com/jellyfin/jellyfin/v12.0/Jellyfin.Api/Controllers/ItemsController.cs
- https://raw.githubusercontent.com/jellyfin/jellyfin/v12.0/Jellyfin.Api/Controllers/TvShowsController.cs
- https://raw.githubusercontent.com/jellyfin/jellyfin/v12.0/Jellyfin.Api/Controllers/MediaSegmentsController.cs
- https://raw.githubusercontent.com/jellyfin/jellyfin/v12.0/Jellyfin.Server.Implementations/Security/AuthorizationContext.cs
- https://raw.githubusercontent.com/jellyfin/jellyfin/v12.0/MediaBrowser.Controller/Dto/DtoOptions.cs
- https://api.github.com/repos/jellyfin/jellyfin/contents/Jellyfin.Api/Controllers?ref=v12.0

Jellyfin server source at tag v10.11.0, for comparison:
- https://raw.githubusercontent.com/jellyfin/jellyfin/v10.11.0/Jellyfin.Api/Controllers/UserLibraryController.cs

Intro Skipper plugin:
- https://raw.githubusercontent.com/intro-skipper/intro-skipper/refs/tags/12.0/v12.0.3.0/IntroSkipper/Controllers/SkipIntroController.cs (the exact version installed on the server; no `IntroSkipperSegments` route)
- https://raw.githubusercontent.com/intro-skipper/intro-skipper/refs/tags/12.0/v12.0.3.0/IntroSkipper/Controllers/SegmentsController.cs
- https://raw.githubusercontent.com/intro-skipper/intro-skipper/refs/tags/12.0/v12.0.3.0/IntroSkipper/Controllers/SegmentEditorController.cs
- https://raw.githubusercontent.com/intro-skipper/intro-skipper/refs/heads/10.11/IntroSkipper/Controllers/SkipIntroController.cs (line 142 carries the route the app calls)
- https://raw.githubusercontent.com/intro-skipper/intro-skipper/refs/heads/12.0/IntroSkipper/Data/AnalysisHelpers.cs (Introduction to Intro, Credits to Outro)
- https://raw.githubusercontent.com/intro-skipper/intro-skipper/refs/heads/12.0/IntroSkipper/Data/AnalysisMode.cs
- https://raw.githubusercontent.com/intro-skipper/intro-skipper/refs/heads/12.0/IntroSkipper/Manager/JellyfinSegmentStore.cs
- https://raw.githubusercontent.com/intro-skipper/intro-skipper/refs/heads/12.0/IntroSkipper/Manager/IJellyfinSegmentStore.cs
- https://raw.githubusercontent.com/intro-skipper/intro-skipper/refs/heads/12.0/IntroSkipper/Manager/MediaSegmentMirror.cs
- https://raw.githubusercontent.com/intro-skipper/intro-skipper/refs/heads/12.0/IntroSkipper/Filters/MediaSegmentsFirstEpisodeFilter.cs
- https://api.github.com/repos/intro-skipper/intro-skipper/git/trees/12.0?recursive=1 (full 342 path listing of the 12.0 branch)

Live server, unauthenticated GET only:
- https://jellyfin.mkznet.me/System/Info/Public and the eighteen probe paths listed in section (c)


---

# Appendix B: Fable codebase evaluation (full text)

# JellyfinToZidoo: full codebase evaluation

Repo: `C:\Users\anthony\claude-projects\JellyfinToZidoo` at commit `f21f5ff` (main, clean). Evaluated 2026-09-09. All file:line references are to the working tree at that commit. Every claim below was checked against the source; where I rely on knowledge rather than a verified source (dependency currency, Android platform behavior) it is marked as such.

## Executive summary

1. Overall health: the core pipeline is sound and the parsing layer is tidy and tested, but the app was built fast, has never had a release-quality pass, and carries several real defects that testing on one device did not expose.
2. Top issue (P0): the published release asset `JellyfinToZidoo.apk` (v2026.03.1) is unsigned. I downloaded it and confirmed there is no v1 signature (no META-INF signature files) and no v2/v3 APK Signing Block. Android refuses to install unsigned APKs, so nobody can install the app from the release page or the Downloader code in the README. The release workflow has no signing step (`.github/workflows/release.yml:37-41`).
3. Top issue (P0): the Jellyfin password is silently copied in plaintext into default SharedPreferences every time Settings opens (`SettingsActivity.java:138`, `:379`), which defeats SecureStorage; the same plaintext file is also exported to the world-readable Downloads folder and the SMB password is written to logcat (`Play.java:1047`, `:1465`).
4. Top issue (P1): the playback loop has data races and a duplicate-poller window (`Play.java:1023-1276`, `:1566`, `:1526`), a crash path in intro-skip parsing on the main thread (`Play.java:902`), and a binge-mode episode lookup that can silently attribute progress to the wrong episode (`JellyfinApi.java:1126-1142`).
5. CI never runs the unit tests (`build.yml:35-36` only runs `assembleDebug`), so the 70 existing tests protect nothing.
6. Needs the owner's decision: (a) keep or remove the ~330 lines of `PLEX_REMOVED` comments and four empty Plex classes (handoff.md records an explicit "do not delete" instruction); (b) whether to keep the unconditional 30 second early stop on every TV episode (`Play.java:1249-1263`), which the README does not describe; (c) whether `debug` should default to on for new installs (`root_preferences.xml:46`); (d) targetSdk 28 stays (scoped storage rewrite needed to move) or moves.

## 1. Correctness bugs and crash risks

### 1.1 Crash paths

| # | Where | What | Severity |
|-|-|-|-|
| C1 | `Play.java:902`, `:1126`, `:1501` | `JellyfinApi.parseIntroSkipperResponse(message)` runs on the main thread with no try/catch. `getIntroSkipperSegments` (`JellyfinApi.java:1193-1227`) returns the raw body of any 2xx response unchecked, and `parseIntroSkipperResponse` (`:170`) calls `JsonParser.parseString(...).getAsJsonObject()` and `.getAsDouble()` unguarded. A reverse proxy returning a 200 HTML page, a JSON array, or `"End": null` throws `IllegalStateException` or `JsonSyntaxException` on the UI thread and kills the app mid playback. | High |
| C2 | `Play.java:262` | `pathToPrint.replaceFirst(":" + password + "@", ...)` uses the raw SMB password as a regex. A password containing `(`, `[`, `*`, `+`, `?` or `\` throws `PatternSyntaxException` (uncaught, debug mode only); a password containing `$` or `.` silently fails to match, so the password is shown unmasked on the debug screen. Use `Pattern.quote`. | Medium |
| C3 | `Play.java:433` | `path.replaceFirst(Pattern.quote(path_to_replace), replaced_with)`: the search side is quoted, the replacement is not. A `replaced_with` value containing `$` (for example an SMB share like `smb://nas/media$`) throws `IllegalArgumentException` or `IndexOutOfBoundsException` from `Matcher.appendReplacement`; a `\` is swallowed. Use `Matcher.quoteReplacement`. Windows style share names with `$` are common. | Medium |
| C4 | `Play.java:624`, `:959` | `originalIntent.getDataString()` can be null for an explicit intent from another app (the activity is exported). The NPE at `:661` is caught (`:735`) and routed to `showDebugPageOrSendIntent`, which calls `buildZidooIntent(directPath=null, ...)`; `path.startsWith("/")` at `:959` then NPEs uncaught. | Low (needs a misbehaving caller) |
| C5 | `JellyfinApi.java:208`, `:232` | `stream.get("Type").getAsString()` and `stream.get("Index").getAsInt()` NPE if either key is absent. These are called from a raw `new Thread` at `Play.java:1161-1171`; an uncaught exception on any thread kills an Android process. `findDefaultStreamIndex` at `:285` guards the same access, so the inconsistency is accidental. | Low |
| C6 | `Play.java:1189` | `seriesId.substring(0, 8)` in a log line throws `StringIndexOutOfBoundsException` if a server ever returns a SeriesId shorter than 8 characters. Inside the poll try/catch so it only aborts that poll iteration, but it is a silent skip of the whole intro/credit/stop logic for that tick. | Low |

### 1.2 Threading and callback lifecycle

- **Unsynchronized shared state.** The poller executor thread (`Play.java:1026-1275`) reads and writes `jellyfinItemId`, `playSessionId`, `durationTicks`, `introStartMs..creditEndMs`, `introSkipArmed`, `creditSkipArmed`, `lastPollPositionMs`, `tracksSet`, `introSegmentsFetched`, `mediaStreams`, `jellyfinAudioStreamIndex`, `currentPlayingPath`, `lastKnownPositionMs`, `lastKnownDurationMs`. The same fields are written on the main thread by the OkHttp callbacks (`:865-925`, `:1073-1136`, `:1472-1528`) and by `onActivityResult` (`:1440-1459`). Only the four boolean flags at `:165-168` are `volatile`. Nothing else has a happens-before edge, so a poll can observe a half updated episode (new item id, old play session, old skip points). This is a correctness risk on a multi-core ARM SoC, not just theory.
- **Duplicate poller window.** `stopProgressPoller()` (`:1397-1402`) calls `shutdownNow()` and nulls the field, then the Play Now path creates a fresh executor via `startProgressPoller()` (`:1526`, `:1538`). A poll task already in flight (blocked up to 3 s in `execute()` at `:1034`) survives `shutdownNow`, finishes, and calls `scheduleNextPoll(nextDelay)` (`:1274`), which checks the *field* (`:1024`), finds the new executor, and schedules onto it. Result: two independent polling chains on one executor, doubling progress reports and running the skip and stop logic twice. Fix: capture the executor in the closure and compare, or use a generation counter.
- **Progress attributed to the wrong episode in binge mode.** When Zidoo auto advances (`:1045-1144`), the app immediately resets skip state but keeps reporting progress against the *old* `jellyfinItemId` and `playSessionId` (`:1174-1185`) until `searchItemByPath` returns. If it never matches (see 1.4), every subsequent progress and the final `reportPlaybackStopped` for the new file are posted against the previous episode, overwriting its resume position with the new file's position. `onNotFound` at `:1139` only logs.
- **Leaked activity through callbacks.** Every `JellyfinApi` callback is an anonymous inner class holding `Play.this`, posted to the main handler after a network round trip of up to 10 s (`JellyfinApi.java:49-50`). If the activity is destroyed during that window, the callbacks still execute against a dead activity (`runOnUiThread`, `startActivityForResult` at `:1367`, `:1525`). Not a crash in practice, but it is why there are four boolean guard flags fighting the lifecycle (`:236`, `:605`).
- **Play button listener re-registered every `onStart`** (`:629`): harmless because the early return at `:605` skips it on re-entry, but it shows the initialization belongs in `onCreate`.
- **Zidoo REST calls spawn raw threads** (`:72`, `:88`, `:105`, `:1161`) instead of using the executor that already exists; a `Thread.sleep(500)` inside one of them (`:1162`).
- **`IsPaused` is always `false`** (`:1178`). Zidoo's `getPlayStatus` response carries a status field; the app never reads it, so Jellyfin shows the session as playing while paused and the 10 s poll keeps advancing nothing.

### 1.3 Intent pipeline

- The `zdmc` branch (`:660-716`) parses `PlexToZidoo-*` query parameters: this is the Kodi/ZDMC integration inherited from upstream and still named for Plex. `Integer.parseInt` at `:668`, `:673`, `:678` is inside the try, so a malformed value degrades to the debug page rather than crashing. Fine, but the naming is now wrong and `tracksSet`/REST track selection is bypassed on that path (audio/subtitle go by intent extras at `:1001-1009` instead).
- `ITEM_ID_PATTERN` (`JellyfinApi.java:36-39`) only matches `/Videos/{id}/stream`. Clients that hand off an HLS transcode URL (`/videos/{id}/main.m3u8`) or a download URL (`/Items/{id}/Download`) fall through to `doSubstitution(directPath)` at `:938` with an `http` URL, and Zidoo is asked to play the transcode stream with no watch state sync. Worth deciding whether to match `/Videos/{id}/` generally, or reject non direct play URLs with a clear message.
- `intentPosition = getIntExtra("position", 0)` (`:835`) and the intent position wins over the server's `PlaybackPositionTicks` (`:914-921`). Correct priority, but a client that sends `position` in ticks or seconds would seek wildly; no sanity check against `durationTicks`.
- `finishWithResult()` (`:1408-1416`) returns `serverUrl + "/Videos/" + itemId + "/stream"` so the client can navigate to the last episode. Good idea. It is only set when `jellyfinItemId` is non empty; the `else` branch at `:1547-1551` for a cancelled Up Next also uses it. Fine.
- `onRestart()` (`:229-240`) finishes the activity whenever none of the four flags are set. That is the Zidoo "back to caller" behavior from upstream and works, but it depends on flag choreography spread across 15 places. A single explicit state enum would replace `awaitingPlaybackResult`, `handlingPlaybackResult`, `waitingForUpNext`, `upNextTriggered`.

### 1.4 Path substitution and reverse lookup

- **Forward and reverse rules disagree.** `doSubstitution` (`:422-423`) splits each slot on commas so one slot can hold several pairs. `getSubstitutionRules` (`:1383-1395`) does not split; it passes the raw comma string to `reverseSubstitution` (`JellyfinApi.java:475-503`), whose `startsWith` never matches. Anyone using the documented comma feature (`strings.xml:36`) loses binge tracking silently.
- **`contains` not `startsWith`** (`:431`): a rule `/media` also matches `/mnt/media/...` mid string and rewrites the middle of the path. Reverse substitution uses `startsWith`, so the two are not inverses. Document as prefix match and use `startsWith` on both sides.
- **Credentials are not URL encoded** (`:452`): a username or password containing `@`, `/`, `:`, `#` or `%` produces a malformed `smb://` URI. `reverseSubstitution` strips with `smb://[^@]+@` (`:489`), which also breaks on an `@` in the password.
- **`Uri.encode(path, "/ :")`** (`:445`) leaves spaces unencoded but encodes everything else; then credentials are spliced in raw. The reverse path URL decodes the whole string first (`:483`). It round trips for typical names, but a filename containing a literal `%` (`100%.mkv`) is decoded to garbage on the way back.
- **Binge lookup by filename stem** (`JellyfinApi.java:513-529`, `:1141-1142`): `searchTerm=S01E02&Limit=10` across the whole library. For a filename like `S01E02.mkv` this hits every show with that stem and the correct item may not be in the first ten, so `parseSearchByPathResponse` returns null and the wrong episode keeps receiving progress (see 1.2). The `/Items` endpoint has a `path` filter parameter in Jellyfin's OpenAPI spec (verify on the spec for the target server version); filtering by full path is exact and one request.
- **The 30 second cut.** `:1249-1263` stops any episode with a `seriesId` when `remainingMs <= 30000` (the three way OR at `:1252` reduces to `<= 30000`), regardless of the `auto_play` toggle (never read anywhere, see section 3) and regardless of whether credits are actually 30 s long. Users lose the last 30 s of every episode that has no Intro Skipper credit segment. README does not mention this.
- **Seek heuristic** `:1202`: a jump of more than 30 s forward or 3 s back in a 10 s poll disarms skips. A 10 s poll of a file playing at 1.5x speed is a 15 s delta, fine; but the check also disarms on the app's own `seekZidoo(introEndMs)` if the next poll lands before the seek completes, which is why intro skip is single shot (`:1222`). Works, brittle.

### 1.5 Tick and unit conversions

- `ticksToMs`/`msToTicks` (`JellyfinApi.java:325-337`) are correct and tested. `viewOffset` is an `int` in ms (`Play.java:133`, `:920`): overflows past 24.8 days, irrelevant for video. `getIntExtra("position")` at `:1588` matches the Zidoo contract.
- `IntroSkipperResult.*Ms()` (`:155-158`) truncate rather than round; 1 ms error, fine.
- `isWatched` (`:388-390`) uses a `double` multiply then casts; exact enough.

### 1.6 Network error handling in the API layer (general fragility, not the endpoint audit)

- No retry anywhere, and the only timeout is 10 s connect/read (`JellyfinApi.java:48-51`); no call timeout, no write timeout. Progress reports are fire and forget so retry is not needed there, but `reportPlaybackStopped` and `markAsWatched` (`Play.java:1598`, `:1604`) are the one shot calls that decide watch state and they fail permanently on one dropped packet; a single retry with backoff for those two would be cheap.
- `enqueueSimpleRequest` (`:966-989`) discards the response body, so a 400 from the server on a malformed body gives the user "HTTP error 400" with no detail. Log the first 200 bytes of the body at warn level.
- `getIntroSkipperSegments` (`:1214-1218`) treats every non 2xx as "no segments" including 401. That masks an expired token; the first hint the user gets is a failed progress report.
- Two request builder styles: `buildAuthHeader` (token only, `:399-401`) for GETs and `buildFullAuthHeader` (`:815-818`) for POSTs. Jellyfin has been tightening header requirements since 10.9 (the separate endpoint audit owns the verdict); at minimum the two should be one function so a fix lands once.
- `authenticate` (`:755-806`) posts `Pw` in JSON with a hardcoded header at `:765` (third copy of the client identity string).
- `testConnection` (`:999-1041`), `getItem` (`:653-691`), `getNextUp` (`:916-960`), `Callback` (`:72-75`) and `NextUpCallback` (`:908-910`) are not called from anywhere in `app/src/main`. Dead API surface.

## 2. Security

| # | Finding | Evidence | Severity |
|-|-|-|-|
| S1 | **Jellyfin password persisted in plaintext to default SharedPreferences.** `jellyfin_password` is a persistent `EditTextPreference` (`root_preferences.xml:22-28`, no `android:persistent="false"`). `setJellyfinPasswordPreference` calls `passwordPref.setText(existing)` on every Settings open (`SettingsActivity.java:138`) and import calls `setText(value)` (`:379`). androidx `EditTextPreference.setText` persists the value when the preference is persistent, so the secret lands in `shared_prefs/com.jellyfintozidoo_preferences.xml` unencrypted. The `onPreferenceChangeListener` returning `false` (`:146-152`) only prevents the *user edit* path; the programmatic writes bypass it. SecureStorage is therefore decorative for the password. | High |
| S2 | **Export writes secrets to a world readable file.** `exportSettings` (`:446-496`) writes to `Downloads/JellyfinToZidooSettings.txt` (`:40`) and explicitly adds the Jellyfin password (`:466-470`); all ten SMB passwords are in default prefs and go too. On API 28 with legacy storage that file is readable by every app with storage permission. The UI string says "These will not import/export your password" (`strings.xml:45`), which is false; README says only tokens are excluded, which is true but understates it. | High |
| S3 | **SMB password logged to logcat.** `Log.d("Play", "Zidoo advanced to next file: " + nowPlayingPath)` (`Play.java:1047`) logs the path Zidoo reports, which is the `smb://user:pass@host/...` URI the app built; `Log.d(... "directPath=" + directPath)` (`:1465`) logs the same. Logcat on Android 9 is readable by adb and by system apps. | Medium |
| S4 | **Client token displayed on the debug screen.** `intentToString` (`:1645-1669`) dumps the full intent data string, which for Jellyfin clients contains `api_key=` or `ApiKey=` in the stream URL (see the test fixture at `JellyfinUrlParserTest.java:13`). Upstream had `getPrintableString` to mask this; it was commented out (`:242-251`). Debug is on by default for new installs (`root_preferences.xml:46`). | Medium |
| S5 | **`allowBackup="true"`** (`AndroidManifest.xml:10`) with no backup rules: default SharedPreferences (with S1 and every SMB password) are eligible for Android auto backup. Zidoo boxes mostly lack Google services, so exposure is limited, but the flag should be false or scoped. | Low |
| S6 | **`usesCleartextTraffic="true"` globally** (`:16`). Necessary for LAN Jellyfin over HTTP. A network security config that permits cleartext only would be identical in effect since the server host is user supplied; acceptable, just document it. | Info |
| S7 | **Exported `Play` activity with broad filters** (`:28-67`): `http`/`https` with `video/*`, plus `smb`, `nfs`, `rtsp`, `content`, `file`. Any app can send an intent that makes this app call the Jellyfin server with the user's stored token for an attacker chosen item id, report playback, and mark it watched. No host check against the configured `jellyfin_server_url`. Low impact (watch state only), but a one line `startsWith(serverUrl)` check would close it. | Low |
| S8 | **SecureStorage silently degrades to plaintext** (`SecureStorage.java:60-63`) on any keystore failure and never tells the user. Pair with S1: on a device where the keystore fails, both copies are plaintext. `security-crypto 1.1.0-alpha06` (`app/build.gradle:45`): the library reached 1.1.0 stable and was deprecated by Google in 2025 (knowledge, not verified against the release page). | Low |
| S9 | **Access token passed as an intent extra to `UpNextActivity`** (`Play.java:1366`) and read but never used (`UpNextActivity.java:63`). The activity is not exported (`AndroidManifest.xml:71`), so the risk is only that it sits in the activity record; remove the extra. | Low |
| S10 | **`handoff.md` on disk contains a plaintext SMB password and a Jellyfin API key** (lines 60 and 64; values deliberately not reproduced here). It is gitignored and was never committed (verified against the full git history, all refs), so it never left the machine, but it should be scrubbed or deleted and the API key rotated on the server. | Medium (local) |
| S11 | `gson 2.8.6` (`app/build.gradle:50`) predates the 2.8.9 fix for CVE-2022-25647 (deserialization DoS via `writeReplace`). The app only parses trusted server JSON into `JsonObject`, so exploitability is negligible, but it is a free upgrade. Knowledge, not verified. | Low |

## 3. Dead code and leftovers

- **Plex classes.** `PlexLibraryInfo.java:29`, `PlexLibraryXmlParser.java:75`, `PlexMediaType.java:22`, `PlexXmlParser.java:114` are each an empty `public class X {}` placeholder below a fully commented body. Nothing references them (grep across `app/src/main`: zero non comment hits). `Play.java` carries about 330 lines of `PLEX_REMOVED` blocks (`:12-45`, `:118-172`, `:242-251`, `:335-595`, `:717-733`, `:742-814`). `strings.xml:8-10`, `:31-34` and `root_preferences.xml:85-103` have the XML equivalents. `handoff.md:70` records the owner's explicit instruction not to delete any of it, and README says the original code is preserved for reference. **Owner's decision.** Recommendation: the git history and the `upstream` remote already preserve every line; move the blocks to `docs/plex-reference.md` or a `plex-reference` branch and drop the four placeholder classes. If the instruction stands, at least drop the placeholders since they compile into the APK for no reason.
- **Unused dependency.** `com.android.volley:volley:1.2.0` (`app/build.gradle:49`) has no live imports (all commented, `Play.java:21-26`). Remove.
- **Unused API methods.** `getItem`, `getNextUp`, `testConnection`, `Callback`, `NextUpCallback`, `buildAuthHeader`'s only live callers are inside `JellyfinApi` itself (see 1.6). About 200 lines.
- **Dead preference.** `auto_play` (`root_preferences.xml:65-69`, `strings.xml:20`) is never read (grep of `app/src/main/java`: zero hits). The toggle is shown to users and does nothing; Up Next always runs.
- **`accessToken` extra** in `UpNextActivity` (see S9). `arrays.xml` is an empty resource file.
- **Three "uncommitted" fixes in handoff.md and CLAUDE.md:** all three are in the tree and were committed in `0a6a981` on 2026-03-13 (`fix(02-04): E2E device testing bug fixes`): `usesCleartextTraffic` at `AndroidManifest.xml:16`; `useSimpleSummaryProvider="false"` at `root_preferences.xml:12`; import writing the secret directly to SecureStorage at `SettingsActivity.java:369-383`. Item 3 is also superseded: the code moved from API key to username/password login in the same commit, so the description ("handles API key") is stale. `handoff.md` (dated 2026-03-13) and `CLAUDE.md:17-20` should be rewritten; the handoff's "400 error" section describes a bug that was fixed by that same commit.
- **`.playwright-mcp/`** contains six 140 byte console logs from 2026-03-15 (favicon 404s while previewing an HTML file). Untracked and ignored via `*.log`; delete the folder.
- **`icon-preview.html`** (ignored, listed in `CLAUDE.md:15`) and `screenshots/*.html`, `screenshots/*.jpg` (ignored via `screenshots/.gitignore`) are build time scratch for the README images. Fine to keep locally; remove from CLAUDE.md or move to a `design/` folder.
- **`JellyfinToZidoo-ClaudeCode-Prompt.md`** (ignored): the original build prompt. Harmless.
- **Inherited tags** `v1.1` through `v3.0` are upstream PlexToZidoo tags. `release.yml:4-6` triggers on any `v*` tag push, so pushing tags to origin would cut 17 bogus releases. Delete the local tags or narrow the trigger to `v20*`.
- **Naming leftovers:** the `PlexToZidoo-` intent parameter prefix (`Play.java:660`) is a real external contract with ZDMC/Kodi, so it must stay, but it deserves a comment saying so. `useNewZdiooPlayer` (typo, `:192`), `jp.wasabeef` blur transform naming, `Locale.ENGLISH` string formats are cosmetic.
- **Stale docs:** README "Requirements" says username/password auth is required (true) but the "How It Works" section omits the 30 s stop and the binge path lookup. `strings.xml:45` export note is wrong (see S2). `SettingsActivity.java:335` has an em dash in a user facing Toast.

## 4. Build and dependency health

Everything in the "current" column is from knowledge as of mid 2026 and is not verified against Maven or the AGP release page; treat the exact numbers as approximate.

| Item | In repo | Current (knowledge) | Note |
|-|-|-|-|
| AGP | 8.2.2 (`build.gradle:8`) | 8.13.x / 9.0.x | 8.2 is Feb 2024; 9.0 removes the legacy `buildscript` classpath style and Jetifier (`gradle.properties:19`) is a warning today, an error soon. |
| Gradle wrapper | 8.5 (`gradle-wrapper.properties:5`) | 8.14 / 9.x | Needed for newer AGP. |
| compileSdk | 34 | 36 | Fine for now; 35 is where edge to edge and 16 KB page size warnings start. |
| targetSdk | 28 (`app/build.gradle:12`) | 35 for Play; sideload has no floor above 23 | See below. |
| Java | 1.8 source/target (`:33-34`) | 17 | JDK 17 already runs the build (`build.yml:16-20`); `java.util.stream` at `SettingsActivity.java:36` is fine on minSdk 28. Moving to 17 is a two line change. |
| appcompat | 1.2.0 | 1.7.x | 2020 release. |
| material | 1.2.1 | 1.12.x / 1.13 | 2020 release; theme parents still resolve. |
| preference | 1.1.1 | 1.2.1 | 1.2.x fixed several `PreferenceFragmentCompat` lifecycle issues relevant to `SettingsActivity`'s static fragment. |
| constraintlayout | 2.0.4 | 2.2.x | |
| okhttp | 4.12.0 | 4.12.0 is the last 4.x; 5.x is out | Fine as is. |
| security-crypto | 1.1.0-alpha06 | 1.1.0 (deprecated) | See S8. |
| gson | 2.8.6 | 2.13.x | See S11. |
| junit | `4.+` (`:46`) | pin 4.13.2 | Dynamic version breaks reproducible builds and the Gradle cache key in CI. |
| glide | 4.16.0 | 4.16.0 | Current. |
| glide-transformations | 4.3.0 | 4.3.0 | Current. |
| foojay resolver | 0.8.0 (`settings.gradle:2`) | 0.10 / 1.0 | Cosmetic. |
| volley | 1.2.0 | n/a | Unused, remove. |
| androidx.test ext/espresso | 1.1.2 / 3.3.0 | 1.2.x / 3.6.x | No instrumented tests exist, so these pull nothing; remove or update. |

**targetSdk 28 and the lint suppression.** `lint { disable 'ExpiredTargetSdkVersion' }` (`app/build.gradle:29-31`, commit `8ea59bc`) exists because the release build's lint task fails on targetSdk 28. Disabling the check is the honest way to say "we are staying on 28 on purpose"; it does not affect runtime. What it implies: the app relies on legacy external storage (`requestLegacyExternalStorage`, `READ/WRITE_EXTERNAL_STORAGE`, raw `File` I/O on `Downloads` at `SettingsActivity.java:40`, `:270`, `:451`). Raising targetSdk to 30 or higher breaks import/export until it moves to the Storage Access Framework or `MediaStore.Downloads`; raising to 31 or higher also requires explicit `android:exported` (already present) and `PendingIntent` flags (none used). The Zidoo Z9X line runs Android 9 (API 28) and the 8K line runs Android 11 (knowledge), so targetSdk 28 is a defensible choice for this hardware; the cost is that Android 14 and later devices apply compatibility shims and the app cannot be listed on Play. Decision for the owner; my recommendation is stay on 28 for this release and plan the SAF rewrite of import/export as a P2.

**CI (`build.yml`).** Runs `assembleDebug` only (`:35-36`): no `test`, no `lint`. The unit tests have never run in CI. The Gradle cache key hashes `**/*.gradle*` (`:31`) which is fine, but `actions/cache` with a manually built key is superseded by `gradle/actions/setup-gradle` which also handles the wrapper validation. No `concurrency` group, so overlapping pushes double build. No Dependabot config.

**Release (`release.yml`).** Builds `assembleRelease` with no `signingConfig` in `app/build.gradle:23-28`, so Gradle emits `app-release-unsigned.apk`, which the workflow renames and publishes (`:40-46`). I downloaded the v2026.03.1 asset (10,468,058 bytes) into the scratchpad and inspected it: no `META-INF/*.RSA|*.SF|*.MF`, no `APK Sig Block 42` marker. It is unsigned and uninstallable. The workflow ran twice on the same tag (2026-03-15 and 2026-07-06 per `gh run list`), publishing the same unsigned file both times. Fix: add a release keystore as repository secrets, a `signingConfigs.release` block reading them from env, and make `release` use it; or sign with `apksigner` in the workflow. Additionally `generate_release_notes: true` is fine, `permissions: contents: write` is correct, and the tag trigger should be narrowed (see section 3). `versionCode 20260301` / `versionName "2026.03.1"` must be bumped by hand for every release; deriving them from the tag in CI removes a foot gun.

## 5. Test coverage

What exists (8 classes, about 70 tests, all pure JVM, all green by inspection):

- `IntroSkipperApiTest`: `parseIntroSkipperResponse` shapes and second to ms conversion.
- `JellyfinApiTest`: `buildAuthHeader`, `parseItemResponse`, `parseNextUpDetailResponse`, `parseSearchByPathResponse`.
- `JellyfinUrlParserTest`: `extractItemId`.
- `MediaStreamParsingTest`: audio/subtitle index mapping, `parseUrlParam`, `findDefaultStreamIndex`.
- `PlaybackReportingTest`: JSON body builders, `isWatched`.
- `ReverseSubstitutionTest`: `reverseSubstitution`, `extractSearchName`.
- `SettingsExportTest`: `buildExportJson` exclusions.
- `TickConversionTest`: tick math.

What the risky code is: `Play.doSubstitution` (the feature the whole app exists for), the poll loop decision logic (`:1187-1263`), the episode transition state machine (`onActivityResult` `:1425-1643`, `handleEpisodeCompleted`), and the error paths in the network layer. None of these are tested and, as written, none can be: they are private instance methods on an `Activity` that read `PreferenceManager` directly.

Highest value missing tests, in order:

1. **Forward substitution** as a pure function `substitute(path, rules, useNewPlayer) -> (uri, foundFlag, password)`: prefix vs contains, comma lists, `$` and `\` in replacement, credentials with reserved characters, NFS rewrite, Windows backslashes. Requires extracting it out of `Play` (see section 6). Then a **round trip** property: `reverse(forward(p)) == p` for every rule the UI can hold; this test would have caught the comma split mismatch.
2. **Poll decision logic** as a pure function of `(state, currentPositionMs, durationMs, skipPoints, prefs) -> actions`: intro skip only after baseline, disarm on seek, credit stop vs 30 s stop precedence, no double trigger, reset on file change.
3. **`parseIntroSkipperResponse` with hostile bodies**: HTML, array, `null` End, missing Start. Currently throws.
4. **`parseUrlParam` with URL encoded and repeated keys**, and `extractItemId` for `main.m3u8` and `/Items/{id}/Download` to pin the intended behavior.
5. **`authenticate` response parsing** (no test exists; the parse is inline at `JellyfinApi.java:787-794`): missing `AccessToken`, missing `User`.
6. **Import round trip** for `SettingsActivity.setPreferenceOnImport` including Gson turning numbers into `Double` (`:274-275`) and `null` values.
7. **Wire level tests with OkHttp `MockWebServer`** for one GET and one POST: header shape, base URL trailing slash handling (duplicated 12 times), 401 handling, non JSON 200 body. `mockwebserver` is a test only dependency and makes the endpoint audit's findings regression proof.
8. Later: a Robolectric test of `Play` lifecycle flags (`onRestart` early return, stale result ignored).

Also: `build.yml` must run `./gradlew test` or none of the above matters.

## 6. Architecture and maintainability

**Overall shape.** Two large files (`Play.java` 1,670 lines, `JellyfinApi.java` 1,228 lines), one settings activity, one countdown activity. For a single purpose bridge that is not unreasonable, and the owner's KISS instruction (`handoff.md:74`) is a legitimate constraint. The problems below are the ones that cost real bugs, not style.

- **Static API with per call callbacks.** `JellyfinApi` is a utility class with `static volatile` OkHttp and Handler singletons (`:41-67`) and seven callback interfaces (`Callback`, `DetailedCallback`, `SimpleCallback`, `AuthCallback`, `NextUpCallback`, `NextUpDetailCallback`, `SearchByPathCallback`). Every public method takes `(serverUrl, apiKey, ...)` and strips the trailing slash itself (12 copies of `serverUrl.endsWith("/") ? ... : serverUrl`). A small instance `JellyfinClient(baseUrl, token, deviceId, version)` created once in `Play.onStart` and `SettingsActivity` removes every repeated parameter, makes the auth header one function, makes it mockable, and lets the callback types collapse to one generic `Result<T>` with `onSuccess/onError`.
- **Near identical request builders.** `reportPlaybackStart/Progress/Stopped` (`:826-885`) differ only in path and body; `getItem` and `getItemDetailed` (`:653-736`) are byte for byte the same except the callback signature; `getNextUp` and `getNextUpWithDetails` duplicate again. Three helpers (`get(path, parser, cb)`, `post(path, body, cb)`, `buildUrl(path, query)`) replace roughly 400 lines.
- **`Play.java` mixes six concerns:** intent parsing, preference reading, path substitution, Zidoo intent building, Zidoo REST control (`:52-115`), the playback poll state machine (`:1015-1276`), and the episode transition orchestration (`:1282-1377`, `:1425-1643`). The `getItemDetailed` success handler is copy pasted three times (`:865-925`, `:1083-1115`, `:1472-1528`) including the MediaStreams extraction that re parses `rawBody` when `parseItemResponse` already had the tree in hand (`:875-884`). Suggested split, still KISS: `PathMapper` (pure, testable, both directions), `ZidooPlayer` (intent + REST + poll, emits `PlayStatus`), `PlaybackSession` (per episode state: item id, session id, skip points, streams, armed flags; one object replaced wholesale on episode change instead of 14 field resets in three places), and `Play` as the thin activity. That also fixes the threading issue: the session object is swapped atomically.
- **Hardcoded identity strings.** `Client="JellyfinToZidoo", Device="Zidoo", DeviceId="jellyfintozidoo", Version="1.0.0"` appears at `JellyfinApi.java:765` and `:816-817`. `Version` disagrees with `versionName "2026.03.1"` (`app/build.gradle:14`); `BuildConfig.VERSION_NAME` is free. `DeviceId` is a constant, so every Zidoo running this app presents as the same device to the server: two boxes in one house under one user share a session slot, and "remote control" and "now playing" in the Jellyfin dashboard collapse them into one. Derive from `Settings.Secure.ANDROID_ID` or a UUID generated once into SecureStorage; put `Device` from `Build.MODEL`.
- **Configuration handling.** Preferences are read ad hoc by string key in 20+ places, with the ten slot suffix array duplicated in `Play.java:419`, `:1384` and `SettingsActivity.java:237`. A `Settings` holder read once per playback (`serverUrl`, `token`, `userId`, `rules[]`, toggles) is the obvious fix and makes the prefs keys one enum. Keep the ten slot XML if the owner likes it; just read it in one place.
- **`SettingsActivity` static fragment.** `settingsFragment`, `settingsRootKey`, `scrollToPreference` are `static` (`:41-43`) and every helper is `static` reaching into them. This is a leak of the fragment (and its activity) after the screen closes and breaks on configuration change. Instance fields plus `getSupportFragmentManager().findFragmentById` fix it in place.
- **Positive notes.** The parsing helpers are cleanly separated and package private for testing; `IntroSkipperResult` and `NextUpDetailResult` are sensible value objects; `SecureStorage` is a correct double checked singleton; `UpNextActivity` is small, cancels its timer in `onDestroy`, and the Glide sizing is right for the Z9X's memory. The Zidoo integration (intent extras, `return_result`, the REST seek with the `positon` typo preserved) matches the documented Zidoo contract. The lifecycle flag comments (`:233-235`, `:602-604`) show the author understood the ordering problem even if the flags are the wrong tool.

## 7. Prioritized recommendations

Effort: S under 2 hours, M half a day to a day, L multi day.

### P0 (fix now)

| # | Action | Effort | Files |
|-|-|-|-|
| P0-1 | Sign the release APK: add `signingConfigs.release` reading keystore path, alias and passwords from env; store them as GitHub secrets; decode the keystore in the workflow; delete and re cut v2026.03.1 (or cut v2026.09.1) so the release page has an installable asset. Verify with `apksigner verify` in the workflow. | S | `app/build.gradle`, `.github/workflows/release.yml` |
| P0-2 | Stop persisting the Jellyfin password in plaintext: add `android:persistent="false"` to `jellyfin_password` and stop calling `setText` with the real value on load (use the summary only), or remove the value from default prefs on startup for existing installs. | S | `app/src/main/res/xml/root_preferences.xml`, `SettingsActivity.java:128-152`, `:369-383` |
| P0-3 | Remove the SMB password from logcat (`:1047`, `:1465`); mask credentials with a helper that quotes the password (`Pattern.quote`) before `replaceFirst` (`:262`); restore an `intentToString` mask for `api_key`/`ApiKey` query values. | S | `Play.java` |
| P0-4 | Wrap `parseIntroSkipperResponse` calls in try/catch (or make the parser return the empty result on any exception) so a non JSON body cannot crash playback. | S | `JellyfinApi.java:169-193`, `Play.java:902`, `:1126`, `:1501` |
| P0-5 | Run the tests in CI: add `./gradlew test` (and `lint`) to `build.yml`; pin `junit:4.13.2`. | S | `.github/workflows/build.yml`, `app/build.gradle:46` |
| P0-6 | Scrub `handoff.md` (contains a plaintext SMB password and API key), rotate that API key on the server, and rewrite `CLAUDE.md:17-20` to stop describing committed fixes as uncommitted. | S | `handoff.md`, `CLAUDE.md` (local files, never committed) |

### P1 (next release)

| # | Action | Effort | Files |
|-|-|-|-|
| P1-1 | Fix the duplicate poller window: capture the executor instance in the scheduled lambda and bail if `progressPoller != captured`; make per episode state one object swapped atomically (or at minimum mark the shared fields `volatile`). | M | `Play.java:1015-1276`, `:1397-1402` |
| P1-2 | Make forward and reverse substitution inverses: same comma split, `startsWith` on both sides, `Matcher.quoteReplacement` for the replacement, URL encode SMB user and password. Extract to a pure `PathMapper` class and test the round trip. | M | `Play.java:416-463`, `:1383-1395`, `JellyfinApi.java:475-503`, new `PathMapper.java`, new test |
| P1-3 | Binge lookup: filter `/Items` by full path (check the OpenAPI spec for the `path` parameter on the owner's server version) instead of `searchTerm` + `Limit=10`; on lookup failure stop reporting progress against the previous item. | S | `JellyfinApi.java:1126-1180`, `Play.java:1066-1143` |
| P1-4 | Fix export: either drop passwords from the export file and make the strings true, or keep them and change `strings.xml:45` and README to say so and warn that the file is unencrypted. Owner's call; my recommendation is exclude by default with an "include passwords" checkbox. | S | `SettingsActivity.java:446-496`, `strings.xml:45`, `README.md` |
| P1-5 | Wire or remove `auto_play`; decide on the 30 s unconditional stop (`:1249-1263`) and document whatever stays in README. If it stays, gate it on `auto_play`. | S | `Play.java`, `root_preferences.xml:65-69`, `README.md` |
| P1-6 | Per device `DeviceId` and real `Version` from `BuildConfig.VERSION_NAME`; one `buildAuthHeader` used by every request. | S | `JellyfinApi.java:399-401`, `:765`, `:815-818` |
| P1-7 | Fix `SettingsActivity` static fragment leak; move helpers to instance methods. | S | `SettingsActivity.java:41-43`, `:107-246` |
| P1-8 | Dependency refresh: AGP 8.x latest 8 series, Gradle 8.14, compileSdk 35, appcompat/material/preference/constraintlayout current, gson 2.13, security-crypto 1.1.0, remove volley and the unused androidTest deps, Java 17. Build and run on the device once. | M | `build.gradle`, `app/build.gradle`, `gradle-wrapper.properties`, `settings.gradle` |
| P1-9 | Report `IsPaused` from Zidoo's status field; send progress only when position changed or state changed. | S | `Play.java:1150-1185` |
| P1-10 | Narrow the release trigger to `v20*` or delete the inherited `v1.x`..`v3.0` local tags; derive versionCode/versionName from the tag. | S | `.github/workflows/release.yml`, `app/build.gradle:13-14` |

### P2 (nice to have)

| # | Action | Effort | Files |
|-|-|-|-|
| P2-1 | Collapse `JellyfinApi` to an instance client with three request helpers and one result callback; delete the unused `getItem`, `getNextUp`, `testConnection`, `Callback`, `NextUpCallback`. | M | `JellyfinApi.java`, call sites in `Play.java`, `SettingsActivity.java` |
| P2-2 | Split `Play.java` into `PlaybackSession`, `ZidooPlayer`, `PathMapper` and a thin activity; replace the four lifecycle booleans with one state enum; dedupe the three `getItemDetailed` handlers. | L | `Play.java` plus new classes |
| P2-3 | Plex reference material: move the `PLEX_REMOVED` blocks and the four placeholder classes to `docs/` or a reference branch (owner's decision per `handoff.md:70`). | S | `Play.java`, `Plex*.java`, `strings.xml`, `root_preferences.xml` |
| P2-4 | Import/export through the Storage Access Framework so targetSdk can move past 28; then raise targetSdk and drop the lint suppression, `requestLegacyExternalStorage` and the storage permissions. | M | `SettingsActivity.java`, `AndroidManifest.xml`, `app/build.gradle` |
| P2-5 | `allowBackup="false"` or a backup rules file excluding prefs; host check on incoming intent URLs against the configured server; remove the `accessToken` extra from `UpNextActivity`. | S | `AndroidManifest.xml`, `Play.java:817-825`, `:1366`, `UpNextActivity.java:63` |
| P2-6 | MockWebServer tests for header shape and error bodies; Robolectric test of the `Play` lifecycle guards. | M | `app/build.gradle`, new tests |
| P2-7 | Small retry with backoff on `reportPlaybackStopped` and `markAsWatched`; log response body excerpt on HTTP errors; surface 401 from the segments call. | S | `JellyfinApi.java:966-989`, `:1193-1227` |
| P2-8 | Housekeeping: delete `.playwright-mcp/`, remove the `icon-preview.html` line from `CLAUDE.md`, replace the em dash in the Toast at `SettingsActivity.java:335`, add Dependabot for Gradle and Actions, `concurrency` group in CI, `debug` default to `false` once the setup flow is stable. | S | various |

### Honest bottom line

The app does what it says on the box and the parsing code is better than most hobby Android projects: null checked, package private, tested. The findings that matter are concentrated in four places: the release is not installable (P0-1), the password protection is not real (P0-2, S2, S3), the playback loop is racy and can attribute progress to the wrong episode (P1-1, P1-3), and the two halves of path substitution disagree (P1-2). Those are a few days of work, most of it small. The larger refactors in P2 are worth doing only if the owner intends to keep adding features (pause reporting, multi server, trailers); for a single purpose bridge that is now feature complete, P0 and P1 are enough to make it solid.
