# Coding Agent Instructions

These instructions apply to coding agents working in this repository.

## Project Shape

- The Git repository root is `melodee-player/`, but the Android Gradle project
  root is `melodee-player/src/`.
- Run Gradle from `src/`, not from the repository root.
- The app module is `src/app`; the macrobenchmark module is `src/benchmark`.
- The app targets Melodee API v1 and Android SDK 36, with minSdk 23.
- The local Android SDK path used in this workspace is
  `/home/steven/Android/Sdk`.

## Change Discipline

- Keep edits scoped to the requested behavior and existing architecture.
- Do not revert user changes or unrelated workspace changes.
- Prefer the existing Kotlin, Compose, Retrofit, Media3, coroutine, and
  SharedPreferences patterns before introducing new frameworks.
- Treat authentication, Android Auto, media playback, API models, and Gradle
  configuration as high-risk areas. Add or update tests when touching them.
- Do not add local app-data SQLite/Room persistence unless explicitly requested.
  Media3 already uses its own SQLite-backed cache metadata internally.

## Changelog

- Update `CHANGELOG.md` for every notable change before finishing work.
- Add new entries under `## [Unreleased]` by default. If the user says the
  current version has not been released yet, or the task is explicitly part of
  an in-progress release section such as `## [1.8.0]`, update that active
  version section instead.
- Follow the existing Keep a Changelog categories: `Added`, `Changed`,
  `Deprecated`, `Removed`, `Fixed`, and `Security`.
- Include user-visible behavior changes, API compatibility changes,
  authentication/session changes, Android Auto changes, dependency/toolchain
  changes, security hardening, and important test coverage additions.
- Do not add changelog entries for purely mechanical formatting changes unless
  they materially affect users, maintainers, CI, or release behavior.
- Keep entries concise and factual. Mention the affected subsystem when useful.
- If a task intentionally skips a changelog update, state why in the final
  response.

## README And Docs

- Keep `README.md` accurate when setup, build commands, API behavior,
  authentication behavior, Android Auto behavior, or project layout changes.
- Some files under `docs/` are historical review/planning artifacts. Do not
  treat them as more authoritative than current source, Gradle files,
  `README.md`, and `CHANGELOG.md`.
- If docs contradict source code, verify against source before editing.

## Authentication And API

- Preserve refresh tokens when API responses omit rotated refresh-token values.
- Android Auto must be able to restore existing stored authentication without
  requiring the user to open the handheld UI again.
- Invalid/rejected refresh tokens may clear authentication; transient refresh
  failures should keep stored credentials available for retry.
- API contract changes should be covered by tests under
  `src/app/src/test/java/com/melodee/autoplayer/api` or related model tests.
- The current OpenAPI reference used during this work was
  `/home/steven/Downloads/v1.json`; verify with the user before assuming that
  path is still current in future sessions.

## Android Auto And Playback

- `MusicService` is exported for Android Auto discovery; caller validation must
  remain strict before returning browse content.
- Do not trust Android Auto callers by substring package-name checks. Use exact
  trusted packages and package/UID ownership checks.
- UI playback state should be driven by service/manager flows where possible,
  not by UI-owned polling loops.
- When changing playback state, verify handheld UI and Android Auto behavior:
  current song, play/pause, queue, metadata, notification controls, and auth
  restoration.

## Validation

- Always run:

  ```bash
  git diff --check
  ```

- For Markdown-only changes, `git diff --check` is usually enough.
- For test-only changes, run the targeted test class and preferably the full
  unit suite:

  ```bash
  cd src
  ANDROID_HOME=/home/steven/Android/Sdk ./gradlew testDebugUnitTest --stacktrace
  ```

- For production Android code, build logic, shared models, authentication,
  Android Auto, networking, or playback changes, run the full build:

  ```bash
  cd src
  ANDROID_HOME=/home/steven/Android/Sdk ./gradlew build --stacktrace
  ```

- Connected tests and benchmarks require a device or emulator:

  ```bash
  cd src
  ANDROID_HOME=/home/steven/Android/Sdk ./gradlew connectedDebugAndroidTest
  ANDROID_HOME=/home/steven/Android/Sdk ./gradlew :benchmark:connectedCheck
  ```

- If a validation command cannot be run, explain why and report what was run
  instead.

## Final Response Expectations

- Summarize what changed, what was verified, and any residual risk.
- Mention skipped validations explicitly.
- Keep file references precise for important source or documentation changes.
