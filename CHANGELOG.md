# Changelog

This file records notable project changes. It follows the
[Keep a Changelog](https://keepachangelog.com/en/1.0.0/) format and uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.8.0] - 2026-06-17

### Added

- Added encrypted SharedPreferences storage for authentication tokens, including
  migration of existing access-token, refresh-token, and refresh-token-expiry
  values out of the regular settings file.
- Added refresh-token expiry persistence so refreshed authentication state can
  be saved alongside rotated access and refresh tokens.
- Added an OkHttp `Authenticator`-based token refresh flow that retries 401
  responses with a refreshed access token and distinguishes invalid credentials
  from transient network or server failures.
- Added a separate unauthenticated Retrofit/OkHttp client for refresh-token
  requests to avoid recursive authorization handling.
- Added service-side playlist and search queue APIs so bound UI ViewModels can
  hand queues directly to `MusicService` without large Parcelable intent
  payloads.
- Added playlist page-size tracking, in-flight next-page fetch reuse, and
  proactive page prefetching when playback nears the end of the current queue.
- Added MediaBrowser caller validation for the exported Android Auto media
  service before exposing browse content.
- Added debug-symbol keep rules for native libraries used by AndroidX graphics,
  benchmark, DataStore, and tracing dependencies.
- Added focused authentication persistence tests for omitted refresh-token
  responses, refresh-token expiry retention, transient refresh failures, invalid
  refresh failures, and Android Auto service-side authentication restoration.
- Added OpenAPI v1 contract tests covering authentication, refresh-token
  requests, playlist and favorite route placeholders, scrobble payloads and
  error responses, nullable refresh-token fields, and artist genre arrays.
- Added repository instructions for coding agents requiring changelog updates
  for notable code, behavior, dependency, security, and documentation changes.
- Added project-specific coding-agent guidance for nested Gradle usage,
  validation, authentication, API contracts, Android Auto, playback, and
  documentation expectations.

### Changed

- Updated the Android project version from `1.7.3` to `1.8.0`.
- Raised the Android platform requirements from min SDK 21 / target SDK 35 to
  min SDK 23 / target SDK 36.
- Updated the build toolchain to Gradle 9.5.1, Android Gradle Plugin 9.2.1, and
  JDK 21 in CI.
- Updated Kotlin plugin usage for Kotlin 2.3.21 and Compose compiler plugin
  integration.
- Updated major Android and Kotlin dependencies, including Compose BOM
  2026.05.01, Media3 1.10.1, Retrofit 3.0.0, OkHttp 5.3.2, Coroutines 1.11.0,
  Lifecycle 2.10.0, AndroidX media 1.8.0, Firebase BOM 34.13.0, and current
  test libraries.
- Reduced HTTP logging in app and image-loading clients, redacted authorization
  headers, and disabled verbose request logging for non-debuggable builds.
- Changed authentication failure handling so only non-recoverable refresh
  failures clear stored credentials, while transient refresh failures leave
  credentials available for later retries.
- Changed single-song, playlist, search-result, and album playback setup to use
  service methods when the playback service is already bound.
- Changed playlist playback intents to send playlist references and start
  metadata instead of serializing full song lists through intent extras.
- Aligned the Retrofit v1 API contract with the current OpenAPI spec, including
  refresh-token authentication, playlist pagination query names, album-song
  browsing, scrobble requests, and v1 error payload parsing.
- Updated README, documentation, review notes, and prompt artifacts for the
  SDK 36, JDK 21, Gradle 9.5.1, and dependency-version changes.
- Updated the benchmark module to compile and target SDK 36 with the same JDK
  21 toolchain configuration as the app module.
- Updated `HomeViewModel` and `PlaylistViewModel` to collect playback state,
  current song, playback context, duration, and position from service-backed
  flows instead of running UI-owned polling loops.
- Updated `MusicService` and `MusicPlaybackManager` to expose and maintain
  flow-backed playback progress for bound UI consumers.
- Updated the README to document current setup, API v1 authentication behavior,
  Android Auto behavior, testing commands, and repository layout.
- Cleaned Android Studio commit-inspection warnings in playback, playlist,
  home, service, and model serialization code by removing dead helpers,
  simplifying redundant conditions, and keeping only intentional Android
  lifecycle suppressions.

### Fixed

- Fixed token value leakage in logs by logging token presence instead of token
  prefixes.
- Fixed refresh-token rotation persistence by saving new refresh tokens and
  refresh-token expiry values from network refresh callbacks.
- Fixed Android Auto playlist continuation to avoid starting duplicate next-page
  fetches when playback reaches a queue boundary during an active prefetch.
- Fixed queue navigation state updates by separating next/previous queue
  movement from immediate playback commands in the consolidated playback
  manager.
- Fixed logout flow to route through `AuthenticationManager` when available so
  stored settings, network authentication state, and UI authentication state stay
  synchronized.
- Fixed nullable refresh-token response handling so login and token refresh
  persistence tolerate v1 responses that omit rotated refresh-token values.

### Security

- Moved sensitive authentication tokens from regular SharedPreferences to
  encrypted SharedPreferences when the platform keystore-backed implementation
  is available.
- Reduced sensitive network logging by redacting authorization headers and
  disabling OkHttp body logging.
- Kept the media browser service exported for Android Auto discovery while
  adding caller checks before returning a browser root.
- Tightened Android Auto media browser caller classification by replacing
  package-name substring trust with exact trusted package matching.
