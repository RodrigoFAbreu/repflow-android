package com.repflow.app.domain.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainResultTest {
    @Test
    fun `success carries the value and reports isSuccess`() {
        val result: DomainResult<Int, String> = DomainResult.Success(42)

        assertTrue(result.isSuccess)
        assertFalse(result.isFailure)
        assertEquals(42, result.getOrNull())
    }

    @Test
    fun `failure carries the error and reports isFailure`() {
        val result: DomainResult<Int, String> = DomainResult.Failure("boom")

        assertTrue(result.isFailure)
        assertFalse(result.isSuccess)
        assertNull(result.getOrNull())
    }

    @Test
    fun `map transforms only a success value`() {
        val success: DomainResult<Int, String> = DomainResult.Success(2)
        val failure: DomainResult<Int, String> = DomainResult.Failure("boom")

        assertEquals(DomainResult.Success<Int>(4), success.map { it * 2 })
        assertEquals(DomainResult.Failure<String>("boom"), failure.map { it * 2 })
    }

    @Test
    fun `mapFailure transforms only a failure error`() {
        val success: DomainResult<Int, String> = DomainResult.Success(2)
        val failure: DomainResult<Int, String> = DomainResult.Failure("boom")

        assertEquals(DomainResult.Success<Int>(2), success.mapFailure { it.length })
        assertEquals(DomainResult.Failure<Int>(4), failure.mapFailure { it.length })
    }

    @Test
    fun `fold invokes the matching branch`() {
        val success: DomainResult<Int, String> = DomainResult.Success(2)
        val failure: DomainResult<Int, String> = DomainResult.Failure("boom")

        assertEquals("value:2", success.fold(onSuccess = { "value:$it" }, onFailure = { "error:$it" }))
        assertEquals("error:boom", failure.fold(onSuccess = { "value:$it" }, onFailure = { "error:$it" }))
    }
}
