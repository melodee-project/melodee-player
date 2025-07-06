package com.melodee.autoplayer.service

import com.melodee.autoplayer.domain.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.util.Log

class QueueManager {
    
    enum class RepeatMode {
        NONE,    // No repeat
        ONE,     // Repeat current song
        ALL      // Repeat entire queue
    }
    
    private val _originalQueue = MutableStateFlow<List<Song>>(emptyList())
    private val _currentQueue = MutableStateFlow<List<Song>>(emptyList())
    val currentQueue: StateFlow<List<Song>> = _currentQueue.asStateFlow()
    
    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()
    
    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()
    
    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()
    
    private val _repeatMode = MutableStateFlow(RepeatMode.NONE)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()
    
    private val _playHistory = MutableStateFlow<List<Song>>(emptyList())
    val playHistory: StateFlow<List<Song>> = _playHistory.asStateFlow()
    
    private var shuffledIndices: List<Int> = emptyList()
    private var currentShuffleIndex = -1
    
    fun setQueue(songs: List<Song>, startIndex: Int = 0) {
        Log.d("QueueManager", "Setting queue with ${songs.size} songs, startIndex: $startIndex")
        
        _originalQueue.value = songs
        _currentQueue.value = songs
        
        if (songs.isNotEmpty() && startIndex < songs.size) {
            _currentIndex.value = startIndex
            _currentSong.value = songs[startIndex]
            
            // If shuffle is enabled, create new shuffle order starting with the selected song
            if (_isShuffleEnabled.value) {
                createShuffleOrder(startIndex)
            }
        } else {
            _currentIndex.value = -1
            _currentSong.value = null
        }
        
        Log.d("QueueManager", "Queue set: currentIndex=${_currentIndex.value}, currentSong=${_currentSong.value?.title}")
    }
    
    fun addToQueue(song: Song) {
        val currentList = _currentQueue.value.toMutableList()
        
        // Check if song is already in queue
        val existingIndex = currentList.indexOfFirst { it.id == song.id }
        if (existingIndex >= 0) {
            // Song already in queue, just play it
            _currentIndex.value = existingIndex
            _currentSong.value = song
            Log.d("QueueManager", "Song already in queue at index $existingIndex")
        } else {
            // Add song to queue
            currentList.add(song)
            _currentQueue.value = currentList
            _originalQueue.value = currentList
            
            // Set as current song
            _currentIndex.value = currentList.size - 1
            _currentSong.value = song
            
            // Update shuffle order if needed
            if (_isShuffleEnabled.value) {
                createShuffleOrder(_currentIndex.value)
            }
            
            Log.d("QueueManager", "Added song to queue: ${song.title}")
        }
        
        // Add to play history
        addToHistory(song)
    }
    
    fun removeFromQueue(index: Int) {
        val currentList = _currentQueue.value.toMutableList()
        if (index >= 0 && index < currentList.size) {
            currentList.removeAt(index)
            _currentQueue.value = currentList
            _originalQueue.value = currentList
            
            // Adjust current index if necessary
            when {
                index < _currentIndex.value -> _currentIndex.value = _currentIndex.value - 1
                index == _currentIndex.value -> {
                    // Current song was removed, play next or stop
                    if (currentList.isNotEmpty()) {
                        val newIndex = if (_currentIndex.value >= currentList.size) 0 else _currentIndex.value
                        _currentIndex.value = newIndex
                        _currentSong.value = currentList[newIndex]
                    } else {
                        _currentIndex.value = -1
                        _currentSong.value = null
                    }
                }
            }
            
            // Update shuffle order if needed
            if (_isShuffleEnabled.value && currentList.isNotEmpty()) {
                createShuffleOrder(_currentIndex.value)
            }
            
            Log.d("QueueManager", "Removed song from queue at index $index")
        }
    }
    
    fun moveInQueue(fromIndex: Int, toIndex: Int) {
        val currentList = _currentQueue.value.toMutableList()
        if (fromIndex >= 0 && fromIndex < currentList.size && 
            toIndex >= 0 && toIndex < currentList.size && 
            fromIndex != toIndex) {
            
            val song = currentList.removeAt(fromIndex)
            currentList.add(toIndex, song)
            _currentQueue.value = currentList
            _originalQueue.value = currentList
            
            // Adjust current index
            when {
                fromIndex == _currentIndex.value -> _currentIndex.value = toIndex
                fromIndex < _currentIndex.value && toIndex >= _currentIndex.value -> _currentIndex.value = _currentIndex.value - 1
                fromIndex > _currentIndex.value && toIndex <= _currentIndex.value -> _currentIndex.value = _currentIndex.value + 1
            }
            
            // Update shuffle order if needed
            if (_isShuffleEnabled.value) {
                createShuffleOrder(_currentIndex.value)
            }
            
            Log.d("QueueManager", "Moved song from index $fromIndex to $toIndex")
        }
    }
    
    fun getNextSong(): Song? {
        val queue = _currentQueue.value
        if (queue.isEmpty()) return null
        
        return when (_repeatMode.value) {
            RepeatMode.ONE -> {
                // Repeat current song
                _currentSong.value
            }
            RepeatMode.ALL -> {
                // Get next song in queue, loop to beginning if at end
                if (_isShuffleEnabled.value) {
                    getNextShuffledSong()
                } else {
                    val nextIndex = (_currentIndex.value + 1) % queue.size
                    queue[nextIndex]
                }
            }
            RepeatMode.NONE -> {
                // Get next song, stop if at end
                if (_isShuffleEnabled.value) {
                    getNextShuffledSong()
                } else {
                    val nextIndex = _currentIndex.value + 1
                    if (nextIndex < queue.size) queue[nextIndex] else null
                }
            }
        }
    }
    
    fun getPreviousSong(): Song? {
        val queue = _currentQueue.value
        if (queue.isEmpty()) return null
        
        return when (_repeatMode.value) {
            RepeatMode.ONE -> {
                // Repeat current song
                _currentSong.value
            }
            RepeatMode.ALL -> {
                // Get previous song in queue, loop to end if at beginning
                if (_isShuffleEnabled.value) {
                    getPreviousShuffledSong()
                } else {
                    val prevIndex = if (_currentIndex.value <= 0) queue.size - 1 else _currentIndex.value - 1
                    queue[prevIndex]
                }
            }
            RepeatMode.NONE -> {
                // Get previous song, stop if at beginning
                if (_isShuffleEnabled.value) {
                    getPreviousShuffledSong()
                } else {
                    val prevIndex = _currentIndex.value - 1
                    if (prevIndex >= 0) queue[prevIndex] else null
                }
            }
        }
    }
    
    fun skipToNext(): Song? {
        val nextSong = getNextSong()
        if (nextSong != null) {
            playSong(nextSong)
        }
        return nextSong
    }
    
    fun skipToPrevious(): Song? {
        val previousSong = getPreviousSong()
        if (previousSong != null) {
            playSong(previousSong)
        }
        return previousSong
    }
    
    fun playSong(song: Song): Boolean {
        val queue = _currentQueue.value
        val index = queue.indexOfFirst { it.id == song.id }
        
        return if (index >= 0) {
            _currentIndex.value = index
            _currentSong.value = song
            
            // Update shuffle position if needed
            if (_isShuffleEnabled.value) {
                currentShuffleIndex = shuffledIndices.indexOf(index)
            }
            
            addToHistory(song)
            Log.d("QueueManager", "Playing song: ${song.title} at index $index")
            true
        } else {
            Log.w("QueueManager", "Song not found in queue: ${song.title}")
            false
        }
    }
    
    fun setShuffle(enabled: Boolean) {
        if (_isShuffleEnabled.value != enabled) {
            _isShuffleEnabled.value = enabled
            
            if (enabled) {
                createShuffleOrder(_currentIndex.value)
            }
            
            Log.d("QueueManager", "Shuffle ${if (enabled) "enabled" else "disabled"}")
        }
    }
    
    fun toggleShuffle() {
        setShuffle(!_isShuffleEnabled.value)
    }
    
    fun setRepeatMode(mode: RepeatMode) {
        _repeatMode.value = mode
        Log.d("QueueManager", "Repeat mode set to: $mode")
    }
    
    fun toggleRepeat() {
        val newMode = when (_repeatMode.value) {
            RepeatMode.NONE -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.NONE
        }
        setRepeatMode(newMode)
    }
    
    private fun createShuffleOrder(currentIndex: Int) {
        val queue = _currentQueue.value
        if (queue.isEmpty()) {
            shuffledIndices = emptyList()
            currentShuffleIndex = -1
            return
        }
        
        // Create list of all indices except current
        val indices = (0 until queue.size).filter { it != currentIndex }.toMutableList()
        indices.shuffle()
        
        // Put current song first in shuffle order
        shuffledIndices = if (currentIndex >= 0) {
            listOf(currentIndex) + indices
        } else {
            indices
        }
        
        currentShuffleIndex = 0
        Log.d("QueueManager", "Created shuffle order: $shuffledIndices")
    }
    
    private fun getNextShuffledSong(): Song? {
        val queue = _currentQueue.value
        if (shuffledIndices.isEmpty() || queue.isEmpty()) return null
        
        return when (_repeatMode.value) {
            RepeatMode.NONE -> {
                val nextShuffleIndex = currentShuffleIndex + 1
                if (nextShuffleIndex < shuffledIndices.size) {
                    currentShuffleIndex = nextShuffleIndex
                    val songIndex = shuffledIndices[currentShuffleIndex]
                    queue[songIndex]
                } else {
                    null
                }
            }
            RepeatMode.ALL -> {
                currentShuffleIndex = (currentShuffleIndex + 1) % shuffledIndices.size
                val songIndex = shuffledIndices[currentShuffleIndex]
                queue[songIndex]
            }
            RepeatMode.ONE -> {
                _currentSong.value
            }
        }
    }
    
    private fun getPreviousShuffledSong(): Song? {
        val queue = _currentQueue.value
        if (shuffledIndices.isEmpty() || queue.isEmpty()) return null
        
        return when (_repeatMode.value) {
            RepeatMode.NONE -> {
                val prevShuffleIndex = currentShuffleIndex - 1
                if (prevShuffleIndex >= 0) {
                    currentShuffleIndex = prevShuffleIndex
                    val songIndex = shuffledIndices[currentShuffleIndex]
                    queue[songIndex]
                } else {
                    null
                }
            }
            RepeatMode.ALL -> {
                currentShuffleIndex = if (currentShuffleIndex <= 0) {
                    shuffledIndices.size - 1
                } else {
                    currentShuffleIndex - 1
                }
                val songIndex = shuffledIndices[currentShuffleIndex]
                queue[songIndex]
            }
            RepeatMode.ONE -> {
                _currentSong.value
            }
        }
    }
    
    private fun addToHistory(song: Song) {
        val history = _playHistory.value.toMutableList()
        
        // Remove song if it already exists in history
        history.removeAll { it.id == song.id }
        
        // Add to beginning of history
        history.add(0, song)
        
        // Keep only last 50 songs in history
        if (history.size > 50) {
            history.removeAt(history.size - 1)
        }
        
        _playHistory.value = history
    }
    
    fun playAtIndex(index: Int) {
        val queue = _currentQueue.value
        if (index >= 0 && index < queue.size) {
            _currentIndex.value = index
            _currentSong.value = queue[index]
            
            // Update shuffle position if needed
            if (_isShuffleEnabled.value && shuffledIndices.isNotEmpty()) {
                currentShuffleIndex = shuffledIndices.indexOf(index)
                if (currentShuffleIndex == -1) {
                    // If the index is not in shuffle order, recreate shuffle starting from this index
                    createShuffleOrder(index)
                }
            }
            
            // Add to play history
            addToHistory(queue[index])
            
            Log.d("QueueManager", "Playing song at index $index: ${queue[index].title}")
        } else {
            Log.w("QueueManager", "Invalid index $index for queue of size ${queue.size}")
        }
    }
    
    fun clearQueue() {
        _originalQueue.value = emptyList()
        _currentQueue.value = emptyList()
        _currentIndex.value = -1
        _currentSong.value = null
        shuffledIndices = emptyList()
        currentShuffleIndex = -1
        Log.d("QueueManager", "Queue cleared")
    }
    
    fun clearHistory() {
        _playHistory.value = emptyList()
        Log.d("QueueManager", "History cleared")
    }
    
    // Convenience methods for external access
    fun isShuffleEnabled(): Boolean = _isShuffleEnabled.value
    fun getRepeatMode(): RepeatMode = _repeatMode.value
    fun getCurrentSong(): Song? = _currentSong.value
    fun getCurrentIndex(): Int = _currentIndex.value
    fun getQueueSize(): Int = _currentQueue.value.size
    fun hasNext(): Boolean = getNextSong() != null
    fun hasPrevious(): Boolean = getPreviousSong() != null
} 