<div align="center">
  <img src="graphics/logo.png" alt="Melodee logo" height="120" />

  # Melodee Android Auto Player
  Native Android + Android Auto client for the Melodee self-hosted music server, built with Jetpack Compose and Media3.

  [![Android CI](https://github.com/melodee-project/melodee-player/actions/workflows/android.yml/badge.svg)](https://github.com/melodee-project/melodee-player/actions/workflows/android.yml)
  [![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
  ![minSdk](https://img.shields.io/badge/minSdk-21-blue)
  ![targetSdk](https://img.shields.io/badge/targetSdk-35-blue)

  Features | Quick Start | Android Auto | Architecture | Testing | Documentation | Contributing
</div>

## Overview

Melodee Player is a Kotlin/Compose app (version 1.7.1, minSdk 21 / targetSdk 35) that streams music from a Melodee server to phones and Android Auto. It ships with a background MediaBrowserService for car interfaces, a Compose UI for handheld use, and a Media3-based playback stack with caching and scrobbling.

## Features

- Android Auto ready: MediaBrowserServiceCompat + MediaSession with browse/search, metadata, steering wheel controls, and voice-triggered playback.
- Rich browse/search: playlists, songs, artists, and albums with pagination, infinite scroll, artist/album drill-ins, and server-side favorites.
- Playback service: Media3/ExoPlayer with queue control, shuffle/repeat, prefetching, notification controls, and streaming cache.
- Resilient networking: login and token refresh against a Melodee API, request deduplication, retry/backoff, normalized server URLs, and scrobble reporting.
- Compose UI: Material 3 theming (light/dark/dynamic), now playing screen + mini player, playlist view, and artist/album galleries with Coil-powered artwork.
- Performance and diagnostics: macrobenchmarks, unit/UI tests, optional performance monitor hooks, and detailed logging for Android Auto interactions.

## Quick Start

1. **Requirements**
   - Android Studio Hedgehog (2023.1.1)+, JDK 17, Android SDK Platform 35
   - Device or emulator on API 21+ (Android Auto features require a device or emulator with Android Auto)
   - A running Melodee API server (use your deployment or the bundled `api-server/`—see its README for setup)
2. **Clone and open**
   ```bash
   git clone https://github.com/melodee-project/melodee-player.git
   cd melodee-player/src
   ```
   Open the `src` directory in Android Studio or keep using the CLI.
3. **Build and run**
   ```bash
   ./gradlew build          # compile and run unit tests
   ./gradlew installDebug   # deploy to a connected device/emulator
   ```
4. **Configure the backend**
   - Launch the app, enter your Melodee server base URL (e.g., `https://your-host/`), and sign in with your account.
   - The app stores auth tokens locally and will refresh them automatically during playback or search.
5. **Use it**
   - Browse playlists/artists/albums, search, start playback, and manage the queue (shuffle/repeat, add/remove, skip/seek).
   - Connect to Android Auto to browse, search, and control playback hands-free.

## Android Auto

- Browse playlists and the current queue from Android Auto using the MediaBrowser tree.
- Voice search and playback via Google Assistant; results are cached for quick follow-up commands.
- Full metadata (art, artist, album) and notification controls; steering wheel buttons map to previous/next/play-pause.
- Queue mutations (add/clear) stay in sync with the handheld UI.

## Architecture & Stack

### Project layout
- `src/app` — Compose Android app (app module)
- `src/benchmark` — Macrobenchmark suite for startup/navigation/scroll performance
- `docs/` — Architecture, reviews, Android Auto specs, performance notes
- `api-server/` — Melodee backend source for local testing (see `api-server/README.md`)
- `graphics/` — Branding assets for docs/UI
- `prompts/` — Prompt artifacts used during development

### Tech stack
- UI: Jetpack Compose, Material 3, Navigation Compose
- Playback: Media3/ExoPlayer, MediaBrowserServiceCompat, MediaSessionCompat, Media cache/prefetch
- Networking: Retrofit + OkHttp (logging, retry/backoff, caching), Gson, request deduplication, token refresh handling
- Data/auth: SharedPreferences-based SettingsManager (encrypted SecureSettingsManager available for migration), URL normalization, scrobble API integration
- Concurrency: Kotlin Coroutines + Flow
- Images: Coil (+ GIF support)
- Testing: JUnit, MockK, Truth, Robolectric, Compose UI tests, AndroidX Test, Macrobenchmark, JaCoCo coverage

## Testing

From `src/`:

```bash
# Unit tests
./gradlew testDebugUnitTest

# Instrumented/UI tests (requires emulator/device)
./gradlew connectedDebugAndroidTest

# Macrobenchmarks (requires emulator/device)
./gradlew :benchmark:connectedCheck

# Coverage report
./gradlew jacocoTestReport
```

CI runs `./gradlew build` and `./gradlew testDebugUnitTest` via GitHub Actions (`android.yml`).

## Documentation

- Android Auto implementation notes: `docs/README.md`
- Code and performance reviews: `docs/REVIEW-SUMMARY.md`, `docs/performance_review.md`, `docs/PERFORMANCE_ANALYSIS.md`
- Implementation and migration details: `docs/implementation-summary.md`, `docs/MIGRATION-SUMMARY.md`

## Contributing

1. Fork and create a feature branch (`git checkout -b feature/my-change`).
2. Make changes, add tests when possible, and run the test suite.
3. Open a PR describing the change and any user-facing impact (screenshots for UI tweaks help).

## License

Distributed under the MIT License. See `LICENSE` for details.
