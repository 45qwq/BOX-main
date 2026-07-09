package com.fongmi.android.tv.utils

import android.os.StatFs
import com.fongmi.android.tv.App
import com.fongmi.android.tv.impl.Callback
import com.github.catvod.utils.Logger
import com.github.catvod.utils.Path

class CacheCleaner private constructor() {

    private var cacheThreshold: Long = DEFAULT_CACHE_THRESHOLD

    companion object {
        private const val DEFAULT_CACHE_THRESHOLD = 200 * 1024 * 1024L
        private const val MIN_FREE_SPACE = 500 * 1024 * 1024L

        @JvmStatic
        val instance: CacheCleaner by lazy { CacheCleaner() }

        @JvmStatic
        fun get(): CacheCleaner = instance
    }

    fun setCacheThreshold(threshold: Long) {
        this.cacheThreshold = threshold
    }

    fun checkAndClean() {
        App.execute {
            try {
                val cacheSize = FileUtil.getDirectorySize(Path.cache())
                val freeSpace = getAvailableStorageSpace()
                if (cacheSize > cacheThreshold || freeSpace < MIN_FREE_SPACE) {
                    cleanCache()
                }
            } catch (e: Exception) {
                Logger.e("Error", e)
            }
        }
    }

    private fun cleanCache() {
        FileUtil.clearCache(object : Callback() {
            override fun success() { }
        })
    }

    private fun getAvailableStorageSpace(): Long {
        return try {
            val stat = StatFs(Path.cache().path)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) {
            Logger.e("Error", e)
            0
        }
    }
}
