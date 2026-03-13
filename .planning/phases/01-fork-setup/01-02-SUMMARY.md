---
phase: 01-fork-setup
plan: 02
subsystem: infra
tags: [android, gradle, ci, attribution, app-icon, adb, agp8]

# Dependency graph
requires:
  - phase: 01-fork-setup/01
    provides: Renamed codebase with package com.jellyfintozidoo and Plex code commented
provides:
  - Verified debug APK building with AGP 8.2.2 / Gradle 8.5
  - GitHub Actions CI workflow for automated builds
  - README and LICENSE with PlexToZidoo/bowlingbeeg attribution
  - Custom Jellyfin-palette adaptive app icon
  - APK installed and verified on Zidoo device (192.168.0.71:5555)
affects: [02-core-bridge]

# Tech tracking
tech-stack:
  added: [AGP 8.2.2, Gradle 8.5, mavenCentral]
  patterns:
    - "AGP 8.2.2 with Gradle 8.5 requires JDK 17 (JAVA_HOME=C:/Users/anthony/jdk-17.0.18+8)"
    - "Adaptive icon: vector XML foreground with gradient stroke + solid fill"

key-files:
  created:
    - .github/workflows/build.yml
    - README.md
    - app/src/main/res/drawable-v24/ic_launcher_foreground.xml
    - app/src/main/res/values/ic_launcher_background.xml
  modified:
    - app/build.gradle
    - LICENSE
    - app/src/main/AndroidManifest.xml
    - app/src/main/res/values/colors.xml
    - gradle/wrapper/gradle-wrapper.properties
    - app/src/main/res/mipmap-*/ic_launcher.png (all densities)
    - app/src/main/res/mipmap-*/ic_launcher_round.png (all densities)

key-decisions:
  - "Upgraded AGP 4.1.3 to 8.2.2 and Gradle 6.5 to 8.5 for JDK 21 compatibility (only JDK 17+ available)"
  - "Replaced jcenter() with mavenCentral() since jcenter is deprecated/sunset"
  - "App icon uses gradient purple-to-blue outline triangle with solid blue inner triangle (Jellyfin palette)"
  - "CI workflow uses JDK 17 temurin to match AGP 8.2.2 requirements"

patterns-established:
  - "Build requires JAVA_HOME pointing to JDK 17 and ANDROID_HOME to SDK"
  - "local.properties contains sdk.dir for local builds"

requirements-completed: [FORK-03, FORK-05]

# Metrics
duration: 45min
completed: 2026-03-13
---

# Phase 1 Plan 2: Build Verification, Attribution, CI, and App Icon Summary

**AGP 8.2.2 / Gradle 8.5 build pipeline with CI workflow, PlexToZidoo attribution, custom Jellyfin-palette adaptive icon, and verified device deployment on Zidoo**

## Performance

- **Duration:** ~45 min (including checkpoint verification)
- **Started:** 2026-03-13T17:25:00Z
- **Completed:** 2026-03-13T18:14:00Z
- **Tasks:** 2
- **Files modified:** 18+

## Accomplishments
- Gradle build passes with AGP 8.2.2 and Gradle 8.5 (upgraded from 4.1.3/6.5 for JDK compatibility)
- README.md with full PlexToZidoo/bowlingbeeg fork attribution and project description
- LICENSE updated with attribution header preserving original MIT license
- GitHub Actions CI workflow at .github/workflows/build.yml (builds on push/PR to main)
- Custom adaptive app icon: gradient purple-to-blue outline triangle with solid Jellyfin blue inner triangle
- APK deployed and verified on Zidoo device at 192.168.0.71:5555 -- app launches, icon displays correctly, runs alongside PlexToZidoo

## Task Commits

Each task was committed atomically:

1. **Task 1: Build verification, attribution, CI, and app icon** - `121cc01` (feat)
2. **Task 1 follow-up: Refine icon foreground** - `b601a31` (fix)
3. **Task 2: Verify fork on Zidoo device** - checkpoint:human-verify (approved by user)

## Files Created/Modified
- `app/build.gradle` - AGP 8.2.2, namespace, mavenCentral, compileSdk/targetSdk updates
- `gradle/wrapper/gradle-wrapper.properties` - Gradle 8.5 distribution URL
- `README.md` - Fork attribution, project description, build instructions
- `LICENSE` - Attribution header for PlexToZidoo/bowlingbeeg
- `.github/workflows/build.yml` - CI workflow with JDK 17, Gradle cache, APK artifact upload
- `app/src/main/res/drawable-v24/ic_launcher_foreground.xml` - Adaptive icon foreground vector
- `app/src/main/res/values/colors.xml` - Jellyfin purple (#AA5CC3) and blue (#00A4DC) palette
- `app/src/main/res/mipmap-*/ic_launcher.png` - App icon at all Android densities
- `app/src/main/res/mipmap-*/ic_launcher_round.png` - Round icon at all densities
- `app/src/main/AndroidManifest.xml` - Adaptive icon references

## Decisions Made
- Upgraded AGP from 4.1.3 to 8.2.2 and Gradle from 6.5 to 8.5 because only JDK 17+ was available locally (JDK 8 not installed). AGP 8.2.2 is the minimum version supporting JDK 17+.
- Replaced deprecated jcenter() repository with mavenCentral() -- jcenter has been sunset and no longer receives updates.
- App icon designed as an adaptive icon with vector XML foreground: a gradient purple-to-blue outline triangle (Jellyfin branding colors) with a solid blue inner triangle, on a dark background. Refined during device verification to ensure proper centering and rounded corners.
- CI workflow uses JDK 17 temurin distribution to match the AGP 8.2.2 requirement.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] AGP upgraded from 4.1.3 to 8.2.2, Gradle from 6.5 to 8.5**
- **Found during:** Task 1 (Build verification)
- **Issue:** Plan specified JDK 8, but only JDK 17/21 available locally. AGP 4.1.3 cannot run on JDK 17+.
- **Fix:** Upgraded AGP to 8.2.2 and Gradle to 8.5, updated build.gradle for AGP 8 syntax (namespace in android block, etc.)
- **Files modified:** app/build.gradle, gradle/wrapper/gradle-wrapper.properties
- **Verification:** BUILD SUCCESSFUL with zero errors
- **Committed in:** 121cc01

**2. [Rule 3 - Blocking] Replaced jcenter() with mavenCentral()**
- **Found during:** Task 1 (Build verification)
- **Issue:** jcenter() repository is sunset and returns 403 errors for some artifacts
- **Fix:** Replaced all jcenter() references with mavenCentral()
- **Files modified:** build.gradle (project-level)
- **Verification:** All dependencies resolve successfully
- **Committed in:** 121cc01

**3. [Rule 1 - Bug] Icon foreground refined for proper rendering**
- **Found during:** Task 2 checkpoint (device verification)
- **Issue:** Initial icon foreground had rendering issues -- triangle not properly centered with unrefined corners
- **Fix:** Updated ic_launcher_foreground.xml with properly centered paths, gradient stroke, and rounded corner control points
- **Files modified:** app/src/main/res/drawable-v24/ic_launcher_foreground.xml
- **Verification:** User confirmed icon looks correct on Zidoo device
- **Committed in:** b601a31

---

**Total deviations:** 3 auto-fixed (2 blocking, 1 bug)
**Impact on plan:** AGP/Gradle upgrade was necessary for JDK compatibility. jcenter replacement was required for dependency resolution. Icon refinement improved visual quality. No scope creep.

## Issues Encountered
- local.properties file needed with sdk.dir=C:\\Users\\anthony\\Android\\Sdk for local builds (not committed to git)
- Build requires JAVA_HOME="C:/Users/anthony/jdk-17.0.18+8" environment variable

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Phase 1 is complete: clean fork builds, installs on device, has CI, proper attribution
- Ready for Phase 2: Core Bridge (Jellyfin auth, intent interception, path substitution)
- PLEX_REMOVED markers throughout codebase ready for Jellyfin API replacement
- Build pipeline established with AGP 8.2.2 / Gradle 8.5

## Self-Check: PASSED

All key files verified present (7/7). Both task commits (121cc01, b601a31) verified in git log.

---
*Phase: 01-fork-setup*
*Completed: 2026-03-13*
