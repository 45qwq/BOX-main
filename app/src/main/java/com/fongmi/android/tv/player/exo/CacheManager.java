package com.fongmi.android.tv.player.exo;

import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;

import com.fongmi.android.tv.App;
import com.github.catvod.utils.Path;

import java.io.File;

public class CacheManager {

    private static final long DEFAULT_MAX_CACHE_SIZE = 1024L * 1024L * 1024L; // 1GB
    private static final long MIN_CACHE_SIZE = 512L * 1024L * 1024L; // 512MB
    private static final long MAX_CACHE_SIZE = 2048L * 1024L * 1024L; // 2GB

    private volatile SimpleCache cache;
    private long maxCacheSize;

    private static class Loader {
        static volatile CacheManager INSTANCE = new CacheManager();
    }

    public static CacheManager get() {
        return Loader.INSTANCE;
    }

    private CacheManager() {
        this.maxCacheSize = DEFAULT_MAX_CACHE_SIZE;
    }

    public Cache getCache() {
        if (cache == null) {
            synchronized (this) {
                if (cache == null) {
                    create();
                }
            }
        }
        return cache;
    }

    public long getMaxCacheSize() {
        return maxCacheSize;
    }

    public void setMaxCacheSize(long maxCacheSize) {
        long size = Math.max(MIN_CACHE_SIZE, Math.min(maxCacheSize, MAX_CACHE_SIZE));
        synchronized (this) {
            this.maxCacheSize = size;
            if (cache != null) {
                cache.release();
                cache = null;
            }
        }
    }

    public long getCacheSize() {
        SimpleCache c = cache;
        if (c != null) {
            return c.getCacheSpace();
        }
        return getDirectorySize(Path.exo());
    }

    public void clearCache() {
        synchronized (this) {
            if (cache != null) {
                cache.release();
                cache = null;
            }
            Path.clear(Path.exo());
        }
    }

    private void create() {
        cache = new SimpleCache(
                Path.exo(),
                new LeastRecentlyUsedCacheEvictor(maxCacheSize),
                new StandaloneDatabaseProvider(App.get())
        );
    }

    private static long getDirectorySize(File dir) {
        long size = 0;
        if (dir == null || !dir.exists()) return 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                size += file.isDirectory() ? getDirectorySize(file) : file.length();
            }
        }
        return size;
    }
}