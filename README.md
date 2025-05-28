# Melodee Android Auto Player

A native Android music player application that allows users to stream music from Melodee servers. The application is built using modern Android development practices with Jetpack Compose and supports Android Auto integration for in-car entertainment.

This app is very heavily vibe coded.

## Features

- 🚗 **Android Auto Integration** - Full support with voice commands and car-optimized UI
- 🎵 **Modern UI** - Built with Jetpack Compose and Material Design 3
- 🏗️ **Clean Architecture** - MVVM pattern with Clean Architecture principles
- 🌐 **Efficient Networking** - Retrofit with OkHttp for reliable API communication
- 🎧 **Media3 Playback** - Advanced media playback with ExoPlayer
- 📱 **Playlist Management** - Browse and manage playlists with infinite scrolling
- 🔍 **Search Functionality** - Search for songs and playlists
- 🔐 **User Authentication** - Secure login with configurable server endpoints
- 🔄 **Pull-to-Refresh** - Refresh content with intuitive gestures
- 🎮 **Playback Controls** - Persistent now playing bar with full media controls

## Project Structure

```
src/
├── build.gradle.kts              # Root project configuration (Kotlin DSL)
├── settings.gradle.kts           # Project settings (Kotlin DSL)
├── app/
│   ├── build.gradle.kts          # App module configuration (Kotlin DSL)
│   └── src/main/java/com/melodee/autoplayer/
│       ├── data/                 # Data layer (API, repositories)
│       ├── domain/               # Domain layer (models, use cases)
│       ├── presentation/         # Presentation layer (ViewModels, UI)
│       ├── service/              # Background services (media, scrobbling)
│       ├── ui/                   # UI components and themes
│       ├── util/                 # Utility classes
│       └── MelodeeApplication.kt # Application class
├── gradlew                       # Gradle wrapper (Unix)
├── gradlew.bat                   # Gradle wrapper (Windows)
└── gradle/                       # Gradle wrapper configuration
```

## Architecture

The application follows **Clean Architecture** with **MVVM** pattern:

### 📊 **Data Layer**
- API interfaces and implementations
- Repository pattern for data access
- Network models and DTOs
- Local caching strategies

### 🎯 **Domain Layer**
- Business logic and use cases
- Domain models and entities
- Repository interfaces
- Business rules validation

### 🖼️ **Presentation Layer**
- Jetpack Compose UI components
- ViewModels with state management
- Navigation with Navigation Compose
- UI state handling

### 🔧 **Service Layer**
- Media playback service
- Android Auto integration
- Background processing
- Scrobbling and analytics

## Setup

### Prerequisites
- **Android Studio** Hedgehog (2023.1.1) or later
- **JDK 17** or higher
- **Android SDK** with API level 35
- **Git** for version control

### Installation
1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd melodee-player
   ```

2. **Open in Android Studio**
   - Open Android Studio
   - Select "Open an existing project"
   - Navigate to the cloned directory and select the `src` folder

3. **Configure API endpoint**
   - Launch the application
   - Enter your Melodee server URL in the login screen
   - Provide your credentials

4. **Build and run**
   ```bash
   cd src
   ./gradlew build
   ./gradlew installDebug
   ```

## Requirements

### Development Environment
- **Android Studio**: Hedgehog (2023.1.1) or later
- **JDK**: 17 or higher
- **Android SDK**: API 21-35
- **Kotlin**: 1.9.20
- **Gradle**: 8.12
- **Android Gradle Plugin**: 8.10.0

### Device Requirements
- **Minimum SDK**: API 21 (Android 5.0)
- **Target SDK**: API 35 (Android 15)
- **Architecture**: ARM64, ARM, x86_64

## Dependencies

### Core Android
- **AndroidX Core KTX**: 1.15.0
- **AppCompat**: 1.7.0
- **Material Design**: 1.12.0

### Jetpack Compose
- **Compose BOM**: 2024.12.01
- **Activity Compose**: 1.9.3
- **Navigation Compose**: 2.8.5
- **Material 3**: Latest
- **Compiler**: 1.5.4

### Architecture & Lifecycle
- **Lifecycle Runtime**: 2.8.7
- **ViewModel**: 2.8.7
- **ViewModel Compose**: 2.8.7
- **Runtime Compose**: 2.8.7

### Media & Playback
- **Media3 ExoPlayer**: 1.2.0
- **Media3 UI**: 1.2.0
- **Media3 Session**: 1.2.0
- **AndroidX Media**: 1.7.0 (Android Auto support)

### Networking
- **Retrofit**: 2.11.0
- **Gson Converter**: 2.11.0
- **OkHttp**: 4.12.0
- **Logging Interceptor**: 4.12.0

### Image Loading
- **Coil**: 2.7.0
- **Coil Compose**: 2.7.0
- **Coil GIF**: 2.7.0

### Concurrency
- **Kotlin Coroutines**: 1.9.0

### Testing
- **JUnit**: 4.13.2
- **AndroidX Test**: 1.2.1
- **Espresso**: 3.6.1
- **Compose Testing**: Latest

## Build System

This project uses **Kotlin DSL** for Gradle build scripts, providing:
- Type-safe build configuration
- Better IDE support and autocomplete
- Compile-time error checking
- Consistent syntax with Kotlin codebase

### Build Commands
```bash
# Clean build
./gradlew clean

# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Run tests
./gradlew test

# Install on device
./gradlew installDebug
```

## Usage

### Getting Started
1. **Launch** the application
2. **Configure** your Melodee server URL
3. **Login** with your credentials
4. **Browse** available playlists and songs
5. **Select** content to start playback
6. **Control** playback using the now playing bar

### Android Auto
1. **Connect** your device to Android Auto
2. **Launch** the Melodee app from the car interface
3. **Use voice commands** for hands-free control
4. **Browse** playlists using car-safe UI
5. **Control playback** with steering wheel controls

### Voice Commands (Android Auto)
- "Play [song/artist/playlist name]"
- "Pause music"
- "Skip to next song"
- "Go back to previous song"
- "Shuffle my music"

## Android Auto Integration

### Features
- 🎵 **Media Playback Controls** - Play, pause, skip, previous
- 🗣️ **Voice Commands** - Full voice control integration
- 📊 **Metadata Display** - Song title, artist, album art
- 📋 **Playlist Browsing** - Car-optimized playlist navigation
- 🔍 **Search Functionality** - Voice and text search
- 🎛️ **Steering Wheel Controls** - Hardware button support

### Technical Implementation
- Media3 MediaSession for playback control
- MediaBrowserService for content browsing
- Custom MediaSessionCallback for Android Auto commands
- Optimized UI for automotive displays

## Contributing

### Development Workflow
1. **Fork** the repository
2. **Create** a feature branch (`git checkout -b feature/amazing-feature`)
3. **Commit** your changes (`git commit -m 'Add amazing feature'`)
4. **Push** to the branch (`git push origin feature/amazing-feature`)
5. **Open** a Pull Request

### Code Style
- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use [ktlint](https://ktlint.github.io/) for code formatting
- Write meaningful commit messages
- Include tests for new features

### Pull Request Guidelines
- Ensure all tests pass
- Update documentation if needed
- Follow the existing code style
- Include screenshots for UI changes

## License

This project is licensed under the **MIT License** - see the [LICENSE](LICENSE) file for details.

## Support

For support and questions:
- Create an issue in the repository
- Check existing documentation
- Review the code comments for implementation details 
