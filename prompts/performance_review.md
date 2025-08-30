# Performance Review (First Pass)

**App stack**: Jetpack Compose (Material 3), MVVM/Clean Architecture, Media3 (ExoPlayer + Session), Retrofit/OkHttp(+Gson), Coil, Coroutines/Flows.  
**Reported issue**: Users see poor performance on spotty cellular and slow Wi‑Fi.

## High‑impact opportunities (ranked)
1. **HTTP resilience + caching**
   - Set explicit budgets: connect/read/write timeouts; per‑call overall deadline.
   - Add **ETag/If‑None‑Match** support and OkHttp **Cache** (disk) for list/content endpoints.
   - Implement **retry with capped exponential backoff + jitter** for idempotent GETs only.
   - **Deduplicate in‑flight requests** (coalesce by key) to avoid dogpiling.
2. **ExoPlayer data caching**
   - Enable `CacheDataSource` with an LRU SimpleCache to reduce re‑buffers on flaky links.
   - Tune `LoadControl` and buffer sizes for cellular vs Wi‑Fi; prefer smaller `bufferForPlaybackMs` on cellular.
3. **Compose recomposition pressure**
   - Stabilize parameters using `immutable` types/`@Stable` or `remember`; use `derivedStateOf` for computed values.
   - Provide `key` for `LazyColumn` rows; avoid heavy lambdas in composables.
   - Use Paging 3 for long lists to avoid full list materialization.
4. **Image loading policy (Coil)**
   - Downsample & resize at source; set memory keys; avoid original‑size bitmaps in lists.
   - Provide placeholders and `crossfade(false)` in long lists to reduce jank.
5. **Concurrency hygiene**
   - Ensure IO‑bound work uses `Dispatchers.IO`; cancel child jobs on scope cancellation (structured concurrency).
   - Guard against unbounded retries; verify cancellation paths in ViewModels and repositories.
6. **Startup & baseline profile**
   - Add **Macrobenchmark** + **Baseline Profiles** to improve cold/hot startup and scroll smoothness.
   - Defer non‑critical initialization via `App Startup` or lazy injection.
7. **Build & size**
   - Remove unused deps; turn on R8 full mode; keep rules only where needed; verify Kotlin/Compose compiler metrics.

## Measurement plan
- **Perfetto** / Android Studio profiler sessions for: startup, list scroll, playback start.
- Enable **StrictMode** (debug): detect main‑thread network/disk.
- **OkHttp EventListener** (debug) to log slow calls, retries, and pooling stats.
- **Macrobenchmarks**: Startup + List scroll (repeat 5×; median & p95).

## Risks & guardrails
- Retry logic must not break **non‑idempotent** calls.
- Cache validation must respect auth and per‑user content.
- Don’t prefetch aggressively on cellular; obey metered network constraints.

See `/docs/test_map.md` for concrete tests that lock current behavior while enabling perf work.
