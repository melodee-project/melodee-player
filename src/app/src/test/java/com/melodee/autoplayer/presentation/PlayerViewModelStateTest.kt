package com.melodee.autoplayer.presentation

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Ignore
import org.junit.Test

/**
 * Contract tests that lock PlayerViewModel external behavior under slow networks.
 * Implement the Adapter below and delegate to your real ViewModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelStateTest {

    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        data class Error(val message: String) : UiState()
        data class Playing(val trackId: String) : UiState()
    }

    interface PlayerViewModelAdapter {
        val state: StateFlow<UiState>
        suspend fun play(trackId: String)
        fun cancel() // should cancel ongoing work
    }

    /** TODO: Wire to your real ViewModel. */
    private val vm: PlayerViewModelAdapter = object : PlayerViewModelAdapter {
        private val _state = MutableStateFlow<UiState>(UiState.Idle)
        override val state = _state
        override suspend fun play(trackId: String) { _state.value = UiState.Playing(trackId) }
        override fun cancel() { _state.value = UiState.Idle }
    }

    @Test @Ignore("Wire vm to real ViewModel then enable")
    fun slowNetwork_showsLoading_thenPlaying_orError() = runTest(UnconfinedTestDispatcher()) {
        vm.state.test {
            assertThat(awaitItem()).isInstanceOf(UiState.Idle::class.java)
            // trigger a play action on a slow network (simulate in repository with delay)
            // then assert states: Loading -> Playing or Loading -> Error
        }
    }

    @Test @Ignore("Wire vm to real ViewModel then enable")
    fun cancel_whileLoading_transitionsToIdle() = runTest(UnconfinedTestDispatcher()) {
        vm.cancel()
        assertThat(vm.state.value).isInstanceOf(UiState.Idle::class.java)
    }
}
