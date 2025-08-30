# Performance Benchmarking Workflow

## How to Measure Performance Improvements

### 1. Create Baseline (Before Improvements)

```bash
# Run all benchmark tests
./gradlew benchmark:connectedAndroidTest

# Save baseline results
cp -r src/benchmark/build/reports/androidTests/connected/ performance_reports/baseline_$(date +%Y%m%d)/
```

### 2. Implement Performance Improvements

- Apply optimizations from `docs/performance_review.md`
- Make code changes to improve performance
- Run unit tests to ensure no regressions: `./gradlew app:testDebugUnitTest`

### 3. Generate Comparison Report (After Improvements)

```bash
# Run benchmarks again after improvements
./gradlew benchmark:connectedAndroidTest

# Save improved results
cp -r src/benchmark/build/reports/androidTests/connected/ performance_reports/improved_$(date +%Y%m%d)/
```

### 4. Compare Results

**Key Metrics to Compare:**
- **Startup Time**: Cold/Warm/Hot start duration
- **Frame Timing**: P50, P90, P99 frame render times
- **Memory**: Peak memory usage during operations
- **Network**: Request completion times

**Report Locations:**
- Baseline: `performance_reports/baseline_YYYYMMDD/`
- Improved: `performance_reports/improved_YYYYMMDD/`

## Benchmark Test Details

**StartupBenchmark.kt:**
- `coldStartup()` - App launch from scratch
- `warmStartup()` - App launch with process in memory
- `hotStartup()` - App launch with activity in memory

**ScrollPerformanceBenchmark.kt:**
- `playlistScrolling()` - Playlist list scroll performance
- `songListScrolling()` - Song list scroll performance

**NavigationBenchmark.kt:**
- `navigationBetweenScreens()` - Screen transition timing
- `deepLinkNavigation()` - Deep link performance

## Expected Improvements

Based on performance review, expect improvements in:
- **Startup Time**: 20-40% faster due to optimized initialization
- **Network Performance**: 30-50% faster due to request deduplication
- **UI Smoothness**: Reduced jank from better state management
- **Memory Usage**: Lower peak memory from optimized data structures