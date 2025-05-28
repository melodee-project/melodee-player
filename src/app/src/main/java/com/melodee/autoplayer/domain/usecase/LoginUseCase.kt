package com.melodee.autoplayer.domain.usecase

import android.content.Context
import com.melodee.autoplayer.data.repository.MusicRepository
import com.melodee.autoplayer.domain.model.AuthResponse
import kotlinx.coroutines.flow.Flow

class LoginUseCase(private val context: Context) {
    operator fun invoke(email: String, password: String, baseUrl: String): Flow<AuthResponse> {
        val repository = MusicRepository(baseUrl, context)
        return repository.login(email, password)
    }
} 