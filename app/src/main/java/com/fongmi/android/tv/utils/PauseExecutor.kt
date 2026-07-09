package com.fongmi.android.tv.utils

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock

class PauseExecutor(corePoolSize: Int) : ThreadPoolExecutor(
    corePoolSize, corePoolSize,
    0, TimeUnit.MILLISECONDS,
    LinkedBlockingQueue()
) {

    private val pauseLock = ReentrantLock()
    private val condition: Condition = pauseLock.newCondition()
    @Volatile
    private var isPaused = false

    override fun beforeExecute(t: Thread, r: Runnable) {
        super.beforeExecute(t, r)
        pauseLock.lock()
        try {
            while (isPaused) condition.await()
        } catch (ie: InterruptedException) {
            t.interrupt()
        } finally {
            pauseLock.unlock()
        }
    }

    fun pause() {
        pauseLock.lock()
        try {
            isPaused = true
        } finally {
            pauseLock.unlock()
        }
    }

    fun resume() {
        pauseLock.lock()
        try {
            isPaused = false
            condition.signalAll()
        } finally {
            pauseLock.unlock()
        }
    }
}
