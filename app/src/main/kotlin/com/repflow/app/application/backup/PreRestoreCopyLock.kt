package com.repflow.app.application.backup

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One owner for the whole safety-copy sequence. A confirmed restore outlives its screen, so without
 * this a restore could overlap the next one, or an erase, over the same files: the copy would end up
 * holding the wrong data, or bring back what the user just erased. Locking each file operation would
 * fix neither.
 *
 * Whoever comes second waits (first come, first served) and then reads what the first left. It is not
 * re-entrant and does not need to be: nothing inside it takes it again, and it is never taken inside a
 * Room transaction, so it cannot deadlock with one. A restore takes it first and its transaction second.
 *
 * `@Singleton` is what makes this work: two instances would compile and pass any test that builds its
 * own, which is why `PreRestoreCopyLockTest` asserts the annotation.
 */
@Singleton
class PreRestoreCopyLock
    @Inject
    constructor() {
        private val mutex = Mutex()

        suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
    }
