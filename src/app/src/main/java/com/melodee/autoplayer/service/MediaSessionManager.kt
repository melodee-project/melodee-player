package com.melodee.autoplayer.service

import android.content.Context
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.melodee.autoplayer.domain.model.Song

class MediaSessionManager(private val context: Context) {
    
    private var mediaSession: MediaSessionCompat? = null
    
    @Suppress("DEPRECATION")
    fun createMediaSession(): MediaSessionCompat {
        return MediaSessionCompat(context, "MelodeeAutoPlayer").also {
            mediaSession = it
            it.setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            it.isActive = true
        }
    }
    
    fun updateMetadata(song: Song?) {
        val metadata = MediaMetadataCompat.Builder().apply {
            song?.let {
                putString(MediaMetadataCompat.METADATA_KEY_TITLE, it.title)
                putString(MediaMetadataCompat.METADATA_KEY_ARTIST, it.artist.name)
                putString(MediaMetadataCompat.METADATA_KEY_ALBUM, it.album.name)
                putLong(MediaMetadataCompat.METADATA_KEY_DURATION, it.durationMs.toLong())
                putString(MediaMetadataCompat.METADATA_KEY_ART_URI, it.imageUrl)
            }
        }.build()
        
        mediaSession?.setMetadata(metadata)
    }
    
    fun updatePlaybackState(isPlaying: Boolean, position: Long) {
        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(state, position, 1.0f)
            .build()
            
        mediaSession?.setPlaybackState(playbackState)
    }
    
    fun setCallback(callback: MediaSessionCompat.Callback) {
        mediaSession?.setCallback(callback)
    }
    
    fun getSessionToken() = mediaSession?.sessionToken
    
    fun release() {
        mediaSession?.release()
        mediaSession = null
    }
} 
