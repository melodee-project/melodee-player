package com.melodee.autoplayer.domain.usecase

import com.melodee.autoplayer.data.repository.MusicRepository
import com.melodee.autoplayer.domain.model.PaginatedResponse
import com.melodee.autoplayer.domain.model.Song
import kotlinx.coroutines.flow.Flow

class SearchSongsUseCase(private val repository: MusicRepository) {
    suspend operator fun invoke(query: String, page: Int = 1): Flow<PaginatedResponse<Song>> {
        return repository.searchSongs(query, page)
    }
} 