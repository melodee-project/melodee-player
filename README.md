<div align="center">
  <img src="graphics/logo.png" alt="Melodee logo" height="120" />

  # Melodee Android Auto Player
  Native Android and Android Auto client for the Melodee self-hosted music server, built with Kotlin, Jetpack Compose, and Media3.

  [![Android CI](https://github.com/melodee-project/melodee-player/actions/workflows/android.yml/badge.svg)](https://github.com/melodee-project/melodee-player/actions/workflows/android.yml)
  [![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
  ![minSdk](https://img.shields.io/badge/minSdk-23-blue)
  ![targetSdk](https://img.shields.io/badge/targetSdk-36-blue)

  [Features](#features) | [Quick Start](#quick-start) | [Configuration](#configuration) | [Android Auto](#android-auto) | [Architecture](#architecture--stack) | [Testing](#testing)
</div>

## Overview

Melodee Player is a Kotlin/Compose Android app for browsing, searching, and streaming music from a Melodee API v1 server. Version 1.8.0 targets Android SDK 36, supports Android 6.0+ devices (minSdk 23), and includes a `MediaBrowserServiceCompat`/`MediaSessionCompat` playback service for Android Auto.

The Gradle project is intentionally nested under `src/`. Open `src/` in Android Studio or run Gradle from that directory.

## Features

- Android Auto browsing, search, voice-triggered playback, media buttons, metadata, and notification controls.
- Compose handheld UI with login, home/search, playlist, now-playing, settings, theme, artist, and album views.
- Media3/ExoPlayer playback with queue control, shuffle/repeat, seeking, foreground playback notification, audio focus handling, prefetching, and on-disk media caching.
- Melodee API v1 integration for authentication, refresh-token renewal, current user profile, user playlists, playlist songs, search, artist browsing, album songs, favorites, and scrobbling.
- Authentication persistence with encrypted SharedPreferences when available, migration from previous plaintext token keys, refresh-token retention, and Android Auto service-side restoration.
- OkHttp/Retrofit networking with bearer-token injection, automatic 401 refresh, retry/backoff for recoverable failures, HTTP caching, authorization-header redaction, and request deduplication.
- Local unit and Robolectric tests, a macrobenchmark module, JaCoCo coverage wiring, and GitHub Actions CI for build/test/coverage.

## Requirements

- Android Studio that supports Android Gradle Plugin 9.2.1.
- JDK 21. The Gradle daemon toolchain is configured for Java 21.
- Android SDK Platform 36 and Build Tools installed through Android Studio.
- A device or emulator running API 23 or newer.
- A running Melodee API v1 server. This repository does not include the server source.

## Quick Start

1. Clone the repository.

   ```bash
   git clone https://github.com/melodee-project/melodee-player.git
   cd melodee-player/src
   ```

2. Open the project in Android Studio.

   Use **File > Open** and select `melodee-player/src`, not the repository root.

3. Create or select an Android run configuration.

   Use an **Android App** configuration with module `app`, deploy target set to your emulator or connected device, and launch activity set to the default launcher activity.

4. Build from the command line when needed.

   ```bash
   ./gradlew build
   ./gradlew installDebug
   ```

5. Launch Melodee and sign in with your server URL, email or username, and password.

## Configuration

### Server URL

Enter the base URL for your Melodee server in the login screen, for example:

```text
https://music.example.com/
```

The app normalizes the base URL and then calls versioned API routes such as `/api/v1/auth/authenticate` and `/api/v1/auth/refresh-token`.

Remote cleartext HTTP is disabled. For local development, cleartext traffic is allowed only for `localhost`, `127.0.0.1`, `10.0.2.2`, and `10.0.3.2`.

When testing from the standard Android emulator against a server running on this computer, use:

```text
http://10.0.2.2:<port>/
```

### Authentication

Access and refresh tokens are stored locally. On devices where AndroidX Security encrypted preferences are available, token values are stored in encrypted SharedPreferences. If encrypted preferences cannot be created, the app falls back to regular app-private SharedPreferences.

The refresh token is preserved when the server returns a new access token without rotating the refresh token. Android Auto uses the same stored authentication state as the handheld app, so plugging into a car should not require another login unless the server rejects or expires the refresh token.

### Firebase

The app module includes Firebase Crashlytics and Analytics dependencies and a checked-in `google-services.json`. Replace that file if you build under a different Firebase project.

## Android Auto

The app declares an exported media browser service for Android Auto discovery. `MusicService.onGetRoot()` validates the caller before returning browse content.

Current Android Auto capabilities include:

- Playlist and current-queue browsing through the media browser tree.
- Search and voice-triggered playback through media search intents.
- Play, pause, previous, next, seek, shuffle, repeat, and favorite actions where supported by the active media surface.
- Playback metadata, artwork, media buttons, and foreground notification controls.
- Authentication restoration from local storage when Android Auto starts the service before the handheld UI is opened.

For desktop testing, run the app on an emulator or connected device from Android Studio, sign in once through the handheld UI, then use Android Auto Desktop Head Unit or an Android Auto-capable test environment to connect to the same installed app.

## Architecture & Stack

### Project Layout

- `src/app` - Android application module.
- `src/benchmark` - Macrobenchmark module targeting `:app`.
- `docs/` - Design notes, review artifacts, API migration notes, and Android Auto planning docs.
- `graphics/` - Branding assets used by repository documentation.
- `prompts/` - Development prompt artifacts.
- `CHANGELOG.md` - Release notes for this repository.

### App Stack

- UI: Jetpack Compose, Material 3, Navigation Compose.
- Playback: Media3/ExoPlayer, `MediaBrowserServiceCompat`, `MediaSessionCompat`, Android media notifications.
- Networking: Retrofit 3, OkHttp 5, Gson, retry/backoff, HTTP cache, request deduplication.
- Auth/data: `SettingsManager`, encrypted SharedPreferences, refresh-token persistence, URL normalization.
- Images: Coil with memory and disk caching.
- Background work: Kotlin coroutines and Flow.
- Tests: JUnit 4, MockK, Truth, Robolectric, MockWebServer, AndroidX Test, Macrobenchmark, JaCoCo.

## Testing

Run these commands from `src/`.

```bash
# Local unit and Robolectric tests
./gradlew testDebugUnitTest

# Full local build, lint, unit tests, app APKs, and benchmark module build
./gradlew build

# Connected app instrumentation tests, when a device/emulator is available
./gradlew connectedDebugAndroidTest

# Connected macrobenchmarks, when a device/emulator is available
./gradlew :benchmark:connectedCheck

# JaCoCo coverage report
./gradlew jacocoTestReport
```

If the Android SDK is not discovered automatically, set `ANDROID_HOME` before running Gradle:

```bash
ANDROID_HOME=/path/to/Android/Sdk ./gradlew testDebugUnitTest
```

GitHub Actions installs Android SDK 36 and currently runs `./gradlew build --stacktrace`, `./gradlew :app:testDebugUnitTest --stacktrace`, and `./gradlew :app:jacocoTestReport --stacktrace`. Connected tests and benchmarks are present in the workflow but disabled until CI has an emulator or device runner.

## Documentation

- Current release notes: `CHANGELOG.md`
- API v1 migration and reviews: `docs/API-CHANGES.md`, `docs/API-CHANGES-REVIEW.md`, `docs/API-CHANGES-FINAL-REVIEW.md`
- Android Auto design and planning notes: `docs/ANDROID_AUTO_TECHNICAL_SPEC.md`, `docs/ANDROID_AUTO_ENHANCEMENT_CHECKLIST.md`
- Performance and review notes: `docs/performance_review.md`, `docs/PERFORMANCE_ANALYSIS.md`, `docs/REVIEW-SUMMARY.md`
- Test planning notes: `docs/test_map.md`, `docs/test_commands.md`

Some files under `docs/` are historical review or planning artifacts. Prefer this README, `CHANGELOG.md`, and the Gradle build files as the current source of truth for setup and supported commands.

## Contributing

1. Create a feature branch.
2. Keep changes focused and add or update tests when behavior changes.
3. Run `./gradlew testDebugUnitTest` for focused changes and `./gradlew build` before opening a PR.
4. Include screenshots or screen recordings for visible UI changes.
5. Describe any Android Auto, authentication, or API compatibility impact in the PR.

## License

Distributed under the MIT License. See `LICENSE` for details.
