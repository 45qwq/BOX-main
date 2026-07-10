package com.fongmi.android.tv.utils

import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * 全局线程池统一管理
 * IO    - 网络请求、文件读写等 IO 密集型任务，池大小 = CPU*2 ~ CPU*4
 * COMPUTE - 解析、编解码等 CPU 密集型任务，池大小 = CPU
 * SCHEDULE - 定时/周期性任务
 */
object ThreadPools {

    private const val QUEUE_CAPACITY = 128

    private val CPU_CORES = Runtime.getRuntime().availableProcessors()

    /** IO 密集型线程池：网络请求、文件读写、数据库操作 */
    private val IO: ThreadPoolExecutor

    /** CPU 密集型线程池：解析、计算、编解码 */
    private val COMPUTE: ThreadPoolExecutor

    /** 定时任务线程池：周期性检查、延迟任务 */
    private val SCHEDULE: ScheduledExecutorService

    init {
        IO = ThreadPoolExecutor(
            Math.max(4, CPU_CORES * 2),
            Math.max(8, CPU_CORES * 4),
            60L, TimeUnit.SECONDS,
            LinkedBlockingQueue(QUEUE_CAPACITY),
            ThreadPoolExecutor.CallerRunsPolicy()
        )
        IO.allowCoreThreadTimeOut(true)

        COMPUTE = ThreadPoolExecutor(
            Math.max(2, CPU_CORES),
            Math.max(2, CPU_CORES),
            60L, TimeUnit.SECONDS,
            LinkedBlockingQueue(QUEUE_CAPACITY),
            ThreadPoolExecutor.CallerRunsPolicy()
        )
        COMPUTE.allowCoreThreadTimeOut(true)

        SCHEDULE = Executors.newScheduledThreadPool(1)
    }

    @JvmStatic
    fun io(): ThreadPoolExecutor = IO

    @JvmStatic
    fun compute(): ThreadPoolExecutor = COMPUTE

    @JvmStatic
    fun schedule(): ScheduledExecutorService = SCHEDULE

    /** 创建可暂停的线程池，用于搜索等多任务可取消场景 */
    @JvmStatic
    fun newPauseExecutor(poolSize: Int): PauseExecutor {
        return PauseExecutor(poolSize)
    }

    /** 优雅关闭所有池 */
    @JvmStatic
    fun shutdown() {
        shutdown(IO)
        shutdown(COMPUTE)
        SCHEDULE.shutdownNow()
    }

    private fun shutdown(pool: ExecutorService) {
        pool.shutdown()
        try {
            if (!pool.awaitTermination(3, TimeUnit.SECONDS)) pool.shutdownNow()
        } catch (e: InterruptedException) {
            pool.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }
}
