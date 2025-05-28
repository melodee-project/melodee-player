package com.melodee.autoplayer.service

import com.melodee.autoplayer.domain.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.util.Log

class PlaylistManager {
    private val _currentPlaylist = MutableStateFlow<List<Song>>(emptyList())
    val currentPlaylist: StateFlow<List<Song>> = _currentPlaylist.asStateFlow()
    
    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()
    
    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()
    
    fun setPlaylist(songs: List<Song>, startIndex: Int = 0) {
        _currentPlaylist.value = songs
        _currentIndex.value = if (songs.isNotEmpty() && startIndex < songs.size) startIndex else -1
        _currentSong.value = if (_currentIndex.value >= 0) songs[_currentIndex.value] else null
    }
    
    fun addSongToPlaylist(song: Song) {
        val currentList = _currentPlaylist.value.toMutableList()
        currentList.add(song)
        _currentPlaylist.value = currentList
    }
    
    fun playSong(song: Song): Boolean {
        val index = _currentPlaylist.value.indexOf(song)
        return if (index >= 0) {
            _currentIndex.value = index
            _currentSong.value = song
            Log.d("PlaylistManager", "Playing song: ${song.name} at index $index")
            true
        } else {
            // Song not in current playlist, add it and play
            val currentList = _currentPlaylist.value.toMutableList()
            currentList.add(song)
            _currentPlaylist.value = currentList
            _currentIndex.value = currentList.size - 1
            _currentSong.value = song
            Log.d("PlaylistManager", "Added and playing song: ${song.name} at index ${currentList.size - 1}")
            true
        }
    }
    
    fun getNextSong(): Song? {
        val playlist = _currentPlaylist.value
        val currentIdx = _currentIndex.value
        
        return when {
            playlist.isEmpty() -> null
            currentIdx < 0 -> playlist.firstOrNull()
            currentIdx >= playlist.size - 1 -> playlist.firstOrNull() // Loop to beginning
            else -> playlist[currentIdx + 1]
        }
    }
    
    fun getPreviousSong(): Song? {
        val playlist = _currentPlaylist.value
        val currentIdx = _currentIndex.value
        
        return when {
            playlist.isEmpty() -> null
            currentIdx <= 0 -> playlist.lastOrNull() // Loop to end
            else -> playlist[currentIdx - 1]
        }
    }
    
    fun skipToNext(): Song? {
        val nextSong = getNextSong()
        return if (nextSong != null) {
            playSong(nextSong)
            nextSong
        } else null
    }
    
    fun skipToPrevious(): Song? {
        val previousSong = getPreviousSong()
        return if (previousSong != null) {
            playSong(previousSong)
            previousSong
        } else null
    }
    
    fun hasNext(): Boolean = getNextSong() != null
    
    fun hasPrevious(): Boolean = getPreviousSong() != null
    
    fun getCurrentSong(): Song? = _currentSong.value
    
    fun getCurrentIndex(): Int = _currentIndex.value
    
    fun getPlaylistSize(): Int = _currentPlaylist.value.size
    
    fun clear() {
        _currentPlaylist.value = emptyList()
        _currentIndex.value = -1
        _currentSong.value = null
    }
} 