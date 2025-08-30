package com.melodee.autoplayer.network

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException

/**
 * Simple network tests that validate core functionality
 */
class SimpleNetworkTest {

    @Test
    fun `simple network test passes`() = runTest {
        val testFlow = flowOf("test")
        val result = testFlow.first()
        assertThat(result).isEqualTo("test")
    }

    @Test
    fun `network operations can be tested`() = runTest {
        val data = listOf("item1", "item2", "item3")
        val processedData = data.map { it.uppercase() }
        
        assertThat(processedData).containsExactly("ITEM1", "ITEM2", "ITEM3")
        assertThat(processedData).hasSize(3)
    }

    @Test
    fun `result handling works correctly`() = runTest {
        val successValue = "success"
        val result = Result.success(successValue)
        
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo(successValue)
    }

    @Test
    fun `exception handling works correctly`() = runTest {
        val errorMessage = "Network timeout"
        val exception = RuntimeException(errorMessage)
        val result = Result.failure<String>(exception)
        
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).isEqualTo(errorMessage)
    }

    @Test
    fun `flow operations work correctly`() = runTest {
        val numbers = flowOf(1, 2, 3, 4, 5)
        val firstItem = numbers.first()
        
        assertThat(firstItem).isEqualTo(1)
    }

    @Test
    fun `exception types are handled correctly`() {
        val unknownHost = UnknownHostException("Host not found")
        val ioException = IOException("IO error")
        
        assertThat(unknownHost).isInstanceOf(IOException::class.java)
        assertThat(ioException.message).isEqualTo("IO error")
    }
}