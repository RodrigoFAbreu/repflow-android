package com.repflow.app.application.backup

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
class PreRestoreCopyLockTest {
    private val lock = PreRestoreCopyLock()

    /** Two instances would compile and pass any test that builds its own, and protect nothing. */
    @Test
    fun `the class is a singleton, so every caller in the process shares it`() {
        assertTrue(PreRestoreCopyLock::class.java.isAnnotationPresent(Singleton::class.java))
    }

    @Test
    fun `a second withLock does not start until the first returns`() =
        runTest(UnconfinedTestDispatcher()) {
            val firstMayFinish = CompletableDeferred<Unit>()
            val events = mutableListOf<String>()

            val first =
                launch {
                    lock.withLock {
                        events += "first started"
                        firstMayFinish.await()
                        events += "first finished"
                    }
                }
            val second = launch { lock.withLock { events += "second started" } }
            assertEquals(listOf("first started"), events)

            firstMayFinish.complete(Unit)
            first.join()
            second.join()

            assertEquals(listOf("first started", "first finished", "second started"), events)
        }

    @Test
    fun `waiters run in the order they asked`() =
        runTest(UnconfinedTestDispatcher()) {
            val holderMayFinish = CompletableDeferred<Unit>()
            val order = mutableListOf<Int>()
            val holder = launch { lock.withLock { holderMayFinish.await() } }
            val waiters = (1..3).map { number -> launch { lock.withLock { order += number } } }

            holderMayFinish.complete(Unit)
            holder.join()
            waiters.forEach { it.join() }

            assertEquals(listOf(1, 2, 3), order)
        }

    @Test
    fun `a block that throws releases the lock`() =
        runTest(UnconfinedTestDispatcher()) {
            val thrown =
                runCatching { lock.withLock<Unit> { error("the block failed") } }.exceptionOrNull()

            assertEquals("the block failed", thrown?.message)
            assertEquals("next", lock.withLock { "next" })
        }

    @Test
    fun `a cancelled block releases the lock`() =
        runTest(UnconfinedTestDispatcher()) {
            val never = CompletableDeferred<Unit>()
            val holder = launch(start = CoroutineStart.UNDISPATCHED) { lock.withLock { never.await() } }

            holder.cancelAndJoin()

            assertEquals("next", lock.withLock { "next" })
        }
}
