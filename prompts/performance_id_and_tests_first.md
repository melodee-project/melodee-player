# Task: Android Performance Review + Regression Test Suite (No Code Changes)

## Overview
Users report poor performance on **spotty cellular** and **slow Wi-Fi**. Your job:
1) **Analyze** the project and identify performance improvement opportunities.
2) **Do not change production code**; provide proposals only.
3) **Create/expand tests** that lock in current behavior so later perf changes don’t break shipped features.

## Tech Context (from repo)
- UI/Arch: Jetpack Compose (Material 3), MVVM/Clean Architecture, Navigation Compose
- Playback: Media3/ExoPlayer, MediaSession/MediaBrowserService (Android Auto)
- Networking: Retrofit + OkHttp (+ Gson)
- Images: Coil
- Concurrency: Kotlin Coroutines/Flows
- Tests available/expected: JUnit, AndroidX Test, Espresso, Compose UI testing
- Toolchain: Kotlin 1.9.20, Gradle 8.12, AGP 8.10.0; minSdk 21, targetSdk 35
- Build/run: `cd src && ./gradlew build` / `./gradlew installDebug`

## Ground Rules
- No edits to production sources, manifests, or build types.
- You may add **test-only** dependencies, benchmark modules, and CI config.
- Tests must be fast, deterministic, and isolated (no real network/FS/DB).

---

## Deliverables

### A) Performance Analysis Report (`/docs/performance_review.md`)
For each finding, include:
- **Symptom** (e.g., startup latency, jank on scroll, slow offline behavior)
- **Evidence** (traces, logs, profiler screenshots; paths to artifacts)
- **Likely cause(s)** (main-thread I/O, redundant network calls, over-recomposition, bitmap scaling, missing caching, unbounded coroutines, heavy Room/queries if present)
- **Proposed fix(es)** (minimal, measurable)
- **Impact × Effort** (High/Med/Low)
- **Risk & Test Plan** (which tests protect behavior)

Use (debug/dev only):
- Perfetto / Android Studio Profiler, StrictMode (to surface main-thread I/O), LeakCanary (optional)
- OkHttp EventListener logs; Retrofit call graph review
- Gradle Build Analyzer (`Analyze > Build`) for classpath/kapt/ksp issues
- APK/Bundle size reports (R8), image/bitmap loading audits

### B) Test Map (`/docs/test_map.md`)
List SUTs and **must-not-regress** scenarios:
- Networking resilience: timeouts, retries/backoff (with jitter), cancellation, idempotency, offline cache behavior
- Data layer: repository behavior & paging; sorting and boundary sizes
- Concurrency: dispatcher usage, structured concurrency, cancellation propagation
- UI: critical flows (startup → auth → list → play), state correctness (not perf)
- Background work: WorkManager enqueues, constraints, backoff
- Parsing/formatting: JSON edge cases, locales/time zones, large payloads

Provide a table:
| Area | SUT/Class | Scenarios | Notes |
|------|-----------|-----------|-------|

### C) Tests (new/updated)
**Unit / Robolectric**
- Frameworks: JUnit + (MockK/Mockito) + Truth/AssertJ.
- Coroutines: `kotlinx-coroutines-test` (`runTest`, test dispatcher/scope), `Turbine` for Flows.
- Networking: **OkHttp MockWebServer** with a custom `Dispatcher` to simulate:
  - slow/throttled responses, timeouts, 429/5xx, dropped/partial connections.
- Verify retry/backoff behavior, error surfaces, and **no infinite/retry storms**.
- Ensure cancellation tokens are respected and no main-thread blocking on I/O.

**UI (Instrumented)**
- Espresso / **Compose UI Test** for critical flows with mocked network (DI/service locator swap to a client pointed at MockWebServer).
- Use IdlingResource / test clocks—avoid arbitrary sleeps.
- Validate state and navigation invariants under degraded network.

**Benchmarks (separate module, test-only)**
- Add a `:benchmark` module using **Macrobenchmark**:
  - **Startup** (cold/hot), **Scroll** smoothness on main list(s), **Navigation** latency.
- Generate a **Baseline Profile** file (collect only; don’t wire into release yet).

**Coverage & CI**
- Enable JaCoCo (unit + instrumented) and publish reports.
- Thresholds: **≥80% line**, **≥70% branch** on touched modules (adjust only if impossible).
- CI: run unit tests, then instrumented tests on a stable emulator (API = minSdk+3).

---

## What To Check (Quick Checklist)
- **Networking**: missing HTTP cache/ETags, duplicate or chatty calls, large JSON on main thread, retrying non-idempotent requests, no timeout budgets/backoff/jitter.
- **Compose/UI**: heavy work in composables, unstable params causing recompositions, missing Paging 3 for long lists, unbounded image sizes (Coil policies).
- **Concurrency**: main-thread blocking, unbounded coroutines, missing `withContext(Dispatchers.IO)`, hot flows with backpressure issues.
- **Startup**: eager init, reflection-heavy DI/graph init, unnecessary work before first frame.
- **Build**: slow KAPT/KSP setups, unused deps.

---

## Commands (adjust if needed)
- Unit tests: `./gradlew testDebugUnitTest`
- Instrumented tests: `./gradlew connectedDebugAndroidTest`
- Coverage (example): `./gradlew jacocoTestReport`
- Benchmarks: `./gradlew :benchmark:connectedCheck`

## Quality Gates
- All tests pass locally and in CI.
- No flakiness (run suite 3×).
- Tests are fast (suite ≤ 5 min; typical unit test ≤ 200 ms).
- Clear, maintainable assertions; minimal mocking; no dead code in tests.

> Output everything as described and stop before making production code changes. Recommend changes; don’t apply them.

