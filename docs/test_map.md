# Test Map - Melodee Player

## Overview
This document outlines the Systems Under Test (SUT) and critical scenarios that must not regress during performance improvements. Tests are organized by functional area and include networking resilience, data layer behavior, concurrency patterns, UI flows, and background operations.

## Test Coverage Requirements
- **Unit Tests**: ≥80% line coverage, ≥70% branch coverage
- **Integration Tests**: Critical user flows end-to-end  
- **Performance Tests**: Startup, scroll, navigation latency benchmarks
- **Network Tests**: Various connectivity scenarios with MockWebServer

## Test Areas

| Area | SUT/Class | Scenarios | Notes |
|------|-----------|-----------|-------|
| **Networking** | `NetworkModule` | HTTP cache behavior, retry with exponential backoff, request cancellation, timeout handling | Critical for cellular performance |
| **Networking** | `MusicRepository` | Request deduplication, error mapping, Flow cancellation | Validate repository contract |
| **Networking** | `RequestDeduplicator` | Concurrent request merging, cache eviction, key generation | Prevent duplicate API calls |
| **Networking** | `ErrorHandler` | HTTP error codes (401, 429, 5xx), network timeouts, localization | User-friendly error messages |
| **Data Layer** | `MusicRepository` | Pagination boundaries, concurrent requests, offline behavior | Repository resilience testing |
| **Data Layer** | `AuthenticationManager` | Token refresh, 401 handling, logout flows | Authentication state management |
| **Concurrency** | `HomeViewModel` | StateFlow updates, coroutine cancellation, service binding lifecycle | Memory leak prevention |
| **Concurrency** | `MusicService` | Media session callbacks, playlist management, background processing | Service lifecycle correctness |
| **UI State** | `HomeViewModel` | Search state consistency, pagination state, playback state sync | UI state correctness |
| **UI State** | `HomeScreen` | Compose recomposition, infinite scroll, search result updates | Performance and correctness |
| **UI Flows** | Login → Home → Search → Play | User authentication, search execution, media playback initiation | Critical user journey |
| **UI Flows** | Background → Foreground | Service reconnection, state restoration, playback continuity | Background/foreground transitions |
| **Media Playback** | `MusicService` | Queue management, seeking, shuffle/repeat, Android Auto integration | Core playback functionality |
| **Background Work** | Service lifecycle | Service binding/unbinding, notification management, MediaSession callbacks | Background service reliability |
| **Parsing** | JSON deserialization | Large payloads, malformed data, null handling, type safety | Data integrity |
| **Performance** | Startup benchmarks | Cold start, warm start, first frame rendering | Launch performance |
| **Performance** | Scroll benchmarks | List scrolling smoothness, infinite scroll performance | UI performance |
| **Performance** | Navigation benchmarks | Screen transition latency, ViewModel initialization | Navigation performance |

## Critical Must-Not-Regress Scenarios

### Authentication & Session Management
- **Login flow**: Username/email authentication, token storage, error handling
- **Token refresh**: Automatic 401 handling, background token renewal  
- **Logout flow**: State cleanup, service disconnection, security

### Search & Data Loading
- **Search query execution**: Debounced input, API request management, result pagination
- **Artist filtering**: Autocomplete functionality, selection persistence
- **Infinite scroll**: Pagination state, boundary conditions, loading states
- **Request deduplication**: Identical request merging, cache management

### Media Playback
- **Play queue management**: Song selection, queue modification, shuffle/repeat
- **Playback controls**: Play/pause, seeking, next/previous navigation
- **Background playback**: Service lifecycle, notification controls, MediaSession integration
- **Android Auto**: MediaBrowser implementation, metadata display, remote control

### Network Resilience  
- **Connectivity changes**: WiFi ↔ cellular transitions, offline/online handling
- **Request retry**: Exponential backoff, jitter, maximum retry limits
- **Timeout handling**: Progressive timeouts, user feedback, graceful degradation
- **HTTP caching**: ETags, cache invalidation, offline content availability

### UI State Management
- **Search state persistence**: Query preservation, result caching
- **Playback state sync**: ViewModel ↔ Service synchronization
- **Screen rotation**: State preservation, UI reconstruction
- **Memory pressure**: Large list handling, image loading, cache eviction

### Error Scenarios
- **Network errors**: DNS resolution, connection timeouts, HTTP errors
- **Authentication failures**: Invalid credentials, expired tokens, permission denial
- **Media errors**: Unsupported formats, streaming failures, corrupted data
- **Service errors**: Binding failures, process death, system resource constraints

## Test Implementation Strategy

### Unit Tests (`src/test/`)
- **Framework**: JUnit 4 + MockK + Truth
- **Coroutines**: kotlinx-coroutines-test (runTest, TestDispatcher)  
- **Flows**: Turbine for Flow testing
- **Networking**: MockWebServer for HTTP simulation
- **Coverage**: JaCoCo for line/branch coverage reporting

### Integration Tests (`src/androidTest/`)  
- **Framework**: AndroidX Test + Espresso + Compose UI Test
- **Test doubles**: MockWebServer integration, service locator swaps
- **Synchronization**: IdlingResource, test clocks (avoid arbitrary delays)
- **Device testing**: API level = minSdk + 3 for CI reliability

### Benchmark Tests (`:benchmark` module)
- **Framework**: Macrobenchmark for realistic performance measurement
- **Scenarios**: Startup (cold/hot), scroll performance, navigation latency
- **Baseline profiles**: Generation for release optimization (collection only)
- **CI integration**: Performance regression detection

### Test Doubles & Mocks
- **Network layer**: MockWebServer with custom Dispatcher for various conditions
- **Service layer**: Mock MusicService for UI testing isolation  
- **Repository layer**: Fake implementations with controlled data
- **Time**: Test clocks for predictable timing behavior

## Quality Gates
- All tests pass locally and in CI (3× run for flakiness detection)
- Test suite completion ≤ 5 minutes total
- Individual unit tests ≤ 200ms execution time
- Clear assertions, minimal mocking, no dead test code
- Tests validate both happy path and error conditions

## CI/CD Integration
- **Unit tests**: Run on every PR, gate merge on failures
- **Integration tests**: Run on main branch, stable emulator configuration
- **Performance tests**: Baseline measurement, regression alerting
- **Coverage reporting**: Publish to development team, enforce thresholds