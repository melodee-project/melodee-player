package com.melodee.autoplayer.network

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Basic tests to verify test infrastructure works
 */
class BasicTest {

    @Test
    fun `basic truth assertion works`() {
        val result: Int = 2 + 2
        assertThat(result).isEqualTo(4)
    }


    @Test
    fun `string operations work`() {
        val testString = "test-operation"
        assertThat(testString).contains("operation")
        assertThat(testString.length).isGreaterThan(0)
    }
}