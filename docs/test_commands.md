# Test Execution Commands

This document provides the commands to run all the tests created for the performance review and regression testing suite.

## Setup

Navigate to the project source directory:
```bash
cd src
```

## Unit Tests

### Run all unit tests
```bash
./gradlew testDebugUnitTest
```

### Run specific test suites
```bash
# Network resilience tests
./gradlew :app:testDebugUnitTest --tests "*NetworkResilienceTest"

# Request deduplication tests  
./gradlew :app:testDebugUnitTest --tests "*RequestDeduplicatorTest"

# Existing retrofit tests
./gradlew :app:testDebugUnitTest --tests "*RetrofitResilienceTest"
```

## Integration Tests

### Run all instrumented tests
```bash
./gradlew connectedDebugAndroidTest
```

### Run UI tests specifically
```bash
./gradlew :app:connectedDebugAndroidTest --tests "*HomeScreenInstrumentedTest"
```

## Benchmark Tests

### Run startup benchmarks
```bash
./gradlew :benchmark:connectedCheck --tests "*StartupBenchmark"
```

### Run scroll performance benchmarks
```bash
./gradlew :benchmark:connectedCheck --tests "*ScrollPerformanceBenchmark"
```

### Run navigation benchmarks
```bash
./gradlew :benchmark:connectedCheck --tests "*NavigationBenchmark"
```

### Run all benchmarks
```bash
./gradlew :benchmark:connectedCheck
```

## Coverage Reports

### Generate JaCoCo coverage report
```bash
./gradlew jacocoTestReport
```

Coverage reports will be available at:
- HTML: `app/build/reports/jacoco/jacocoTestReport/html/index.html`
- XML: `app/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml`

## CI/CD Pipeline Commands

### Complete test suite for CI
```bash
# Run tests 3x to check for flakiness
for i in {1..3}; do
  echo "=== Test Run $i ==="
  ./gradlew clean testDebugUnitTest connectedDebugAndroidTest
done
```

### Performance regression testing
```bash
# Run benchmarks and store baseline
./gradlew :benchmark:connectedCheck
```

## Quality Gates Validation

### Check coverage thresholds
```bash
# Generate coverage report
./gradlew jacocoTestReport

# Check thresholds (≥80% line, ≥70% branch coverage)
# This will need to be configured in the build or CI script
```

### Validate test performance
```bash
# Time unit test execution (should be ≤ 200ms per test average)
time ./gradlew testDebugUnitTest

# Time full test suite (should be ≤ 5 minutes total)
time ./gradlew testDebugUnitTest connectedDebugAndroidTest
```

## Debugging Test Failures

### Verbose test output
```bash
./gradlew testDebugUnitTest --info
```

### Run specific failing test with stack trace
```bash
./gradlew testDebugUnitTest --tests "com.melodee.autoplayer.network.NetworkResilienceTest.http_timeout_errors_are_handled_gracefully" --stacktrace
```

### Generate test reports
Test reports are automatically generated at:
- Unit tests: `app/build/reports/tests/testDebugUnitTest/index.html`
- Instrumented tests: `app/build/reports/androidTests/connected/index.html`
- Benchmarks: `benchmark/build/reports/benchmark/`

## Performance Monitoring Setup

### Enable performance monitoring in development
1. Run benchmarks regularly during development
2. Monitor memory usage with LeakCanary (if configured)
3. Use Android Studio Profiler for detailed analysis

### Baseline Profile Generation (Future)
```bash
# Generate baseline profiles (when ready for release optimization)
./gradlew :benchmark:generateBaselineProfile
```

## Notes

- All tests are designed to be deterministic and avoid flakiness
- MockWebServer tests simulate various network conditions
- Benchmark tests require a physical device or emulator for accurate results
- Coverage reports exclude generated code and test files
- Tests validate both performance and correctness of the identified issues

## Troubleshooting

### Common Issues

1. **Tests timeout**: Increase timeout values in test configuration
2. **Network tests fail**: Check MockWebServer port availability  
3. **UI tests fail**: Ensure test device has sufficient resources
4. **Coverage low**: Add tests for uncovered code paths identified in reports

### Device Requirements for Benchmarks
- Physical device recommended for accurate performance measurement
- API level 23+ (minSdk + 3 for stability)
- Sufficient storage for test APKs and data