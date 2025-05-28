- Create an Android Auto application using Kotlin and Media3 that will play music from an API. Create this new application in the "src" folder.
- Add support for voice commands.
- Use Retrofit and OkHttp for efficient networking.
- Use MVVM with Clean Architecture principles.
- Implement proper metadata handling for Android Auto.
- Implement proper audio focus handling.
- Ensure that the application handles different Android Auto display sizes.
- Use Jetpack compose for a modern look and feel for the application with #2b1452 as the primary scheme color.
- Application view flow is Login -> HomePage -> Playlist.
- On the login view create input boxes for the user to provide their email, password and the server URL. Use this server URL as the API base url in the application.
- The user is required to authenticate and will receive a token. This token will be set as the Authorization header as the Bearer token on all API requests. This request returns the AuthResponse model.
- Show the users avatar image and username in the header. 
- The homepage is to show a search input and display all playlists as returned by the API.
- The request to get all playlists returns the PaginatedResponse model with Playlist as the generic data type.
- While information is being retrieved from the API show a loading indicator, like a spinner image.
- When the user performs a search a list of matching songs will be returned. Allow the user to click on any of the songs and start playback of that song. This request returns the PaginatedResponse model with the Song model as generic data type.
- When the user clicks on a playlist, fetch the songs for the playlist from the API and start playing the first song of the playlist. This request returns the PaginatedResponse model with the Song model as the generic data type. Use infite scrolling for retreiving songs.
- When displaying information about the playlist show the playlist name, description, image, number of songs and duration. Add pull-to-refresh capability to perform an API request to get the songs as the order may have changed. Include an icon in the header to refresh. While refreshing display a loading indicator.
- When displaying information about the song playing display these items: Artist name, Album name, show a progress bar of the playback position of the song compared to the duration of the song.
- When playing a song include the ability for the user to click next and back to move through songs in the playlist. When the user is on the first song if they click back play the last song. When the user is on the last song and they click back play the first song.
- Ensure that the application builds without errors.

Below are the models for reponses from the API:

data class PaginatedResponse<T>(
    val meta: PaginationMeta,
    val data: List<T>
)

data class PaginationMeta(
    val totalCount: Int,
    val pageSize: Int,
    val currentPage: Int,
    val totalPages: Int,
    val hasPrevious: Boolean,
    val hasNext: Boolean
)

data class Playlist(
    val id: UUID,
    val name: String,
    val description: String? = null,
    val imageUrl: String,
    val thumbnailUrl: String,
    val durationMs: Double,
    val durationFormatted: String,
    val songCount: Int,
    val initialSong: Song
)

data class Song(
    val id: UUID,
    val streamUrl: String,    
    val name: String,
    val artist: Artist,
    val album: Album,
    var thumbnailUrl: String,
    val imageUrl: String,    
    val durationMs: Double,
    val formattedDuration: String,
    val thumbnailUrl: String,
    val userStarred: Boolean,
    val userRating: Double
)

data class Artist(
    val id: UUID,
    val name: String,
    var thumbnailUrl: String,    
    val imageUrl: String,
    val userStarred: Boolean,
    val userRating: Double
)

data class Album(
    val id: UUID,
    val name: String,
    var thumbnailUrl: String,
    val imageUrl: String,
    val releaseYear: Int,
    val userStarred: Boolean,
    val userRating: Double
)

data class AuthResponse(
    val token: String,
    val user: User
)

data class User(
    val id: UUID,
    val email: String,
    var avatarThumbnailUrl: String,
    var avatarUrl: String,
    val username: String
)




