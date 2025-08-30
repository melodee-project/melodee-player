package com.melodee.autoplayer.repository

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Ignore
import org.junit.Test

/**
 * Contract tests that define expected behavior for your PlaylistRepository.
 * Implement the Adapter inside this test to delegate to your real repository.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistRepositoryContractTest {

    // Minimal domain model used by the contract.
    data class Playlist(val id: String, val name: String)

    interface PlaylistRepository {
        fun getPlaylistsPaged(pageSize: Int): Flow<List<Playlist>>
        suspend fun refresh(): Unit
    }

    /** TODO: Wire this to your real repository (delegate). */
    private val sut: PlaylistRepository = object : PlaylistRepository {
        private val data = MutableStateFlow<List<Playlist>>(emptyList())
        override fun getPlaylistsPaged(pageSize: Int): Flow<List<Playlist>> = data
        override suspend fun refresh() { /* TODO delegate */ }
    }

    @Test @Ignore("Wire sut to real repository then enable")
    fun paging_respectsPageSize_andEmitsIncreasingPages() = runTest(UnconfinedTestDispatcher()) {
        val first = sut.getPlaylistsPaged(pageSize = 20).first()
        assertThat(first.size).isAtMost(20)
    }

    @Test @Ignore("Wire sut to real repository then enable")
    fun refresh_inflightRequestsAreDeduped() = runTest {
        // Recommend: coalesce duplicate requests by key in repository layer.
        // This test should assert that parallel refresh calls do not produce duplicated network calls.
        assertThat(true).isTrue() // Replace with interaction assertions once wired.
    }
}
