# Melodee Android Auto Player

An Android Auto music player application that allows users to stream music from a custom API. The application is built using modern Android development practices and supports Android Auto integration.

## Features

- Android Auto integration with voice commands
- Modern UI using Jetpack Compose
- MVVM architecture with Clean Architecture principles
- Efficient networking with Retrofit and OkHttp
- Media playback using Media3
- Support for playlists and song search
- User authentication
- Infinite scrolling for playlists and songs
- Pull-to-refresh functionality
- Now playing bar with playback controls

## Architecture

The application follows MVVM architecture with Clean Architecture principles:

- **Data Layer**
  - API interfaces
  - Repository implementation
  - Data models

- **Domain Layer**
  - Use cases
  - Domain models

- **Presentation Layer**
  - ViewModels
  - UI components
  - Navigation

## Setup

1. Clone the repository
2. Open the project in Android Studio
3. Configure the API base URL in the login screen
4. Build and run the application

## Requirements

- Android Studio Arctic Fox or later
- Android SDK 21 or higher
- Kotlin 1.9.0 or higher
- Gradle 8.1.0 or higher

## Dependencies

- Jetpack Compose
- Media3
- Retrofit
- OkHttp
- Coil
- Coroutines
- ViewModel
- Navigation Compose

## Usage

1. Launch the application
2. Enter your credentials and API server URL
3. Browse playlists or search for songs
4. Select a playlist or song to start playback
5. Use the now playing bar to control playback
6. Connect to Android Auto to use the application in your car

## Android Auto Integration

The application supports Android Auto with the following features:

- Media playback controls
- Voice commands
- Metadata display
- Playlist browsing
- Search functionality

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## License

This project is licensed under the MIT License - see the LICENSE file for details. 