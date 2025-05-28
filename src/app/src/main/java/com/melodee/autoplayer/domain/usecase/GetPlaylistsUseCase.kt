package com.melodee.autoplayer.domain.usecase

import com.melodee.autoplayer.data.repository.MusicRepository
import com.melodee.autoplayer.domain.model.PaginatedResponse
import com.melodee.autoplayer.domain.model.Playlist
import kotlinx.coroutines.flow.Flow

class GetPlaylistsUseCase(private val repository: MusicRepository) {
    operator fun invoke(page: Int): Flow<PaginatedResponse<Playlist>> {
        return repository.getPlaylists(page)
    }
} 