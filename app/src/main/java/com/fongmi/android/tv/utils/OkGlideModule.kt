package com.fongmi.android.tv.utils

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.annotation.NonNull
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.Excludes
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.integration.avif.AvifByteBufferBitmapDecoder
import com.bumptech.glide.integration.avif.AvifGlideModule
import com.bumptech.glide.integration.avif.AvifStreamBitmapDecoder
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.load.engine.cache.LruResourceCache
import com.bumptech.glide.load.engine.cache.MemorySizeCalculator
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.module.AppGlideModule
import com.github.catvod.net.OkHttp
import java.io.InputStream
import java.nio.ByteBuffer

@GlideModule
@Excludes(AvifGlideModule::class)
class OkGlideModule : AppGlideModule() {

    companion object {
        // 磁盘缓存上限 150MB（默认 250MB，列表缩略图多时膨胀快）
        private const val DISK_CACHE_SIZE = 150L * 1024 * 1024
    }

    override fun applyOptions(@NonNull context: Context, @NonNull builder: GlideBuilder) {
        builder.setLogLevel(Log.ERROR)
        // 按设备内存档次限制 Glide 内存缓存，缓解列表图多时的 GC 抖动
        val calculator = MemorySizeCalculator.Builder(context)
            .setMemoryCacheScreens(2f)
            .build()
        builder.setMemoryCache(LruResourceCache(calculator.memoryCacheSize.toLong()))
        // 限制磁盘缓存上限，避免无上限膨胀占用存储空间
        builder.setDiskCache(InternalCacheDiskCacheFactory(context, DISK_CACHE_SIZE))
    }

    override fun registerComponents(@NonNull context: Context, @NonNull glide: Glide, registry: Registry) {
        val byteBufferBitmapDecoder = AvifByteBufferBitmapDecoder(glide.bitmapPool)
        val streamBitmapDecoder = AvifStreamBitmapDecoder(registry.imageHeaderParsers, byteBufferBitmapDecoder, glide.arrayPool)
        registry.replace(GlideUrl::class.java, InputStream::class.java, OkHttpUrlLoader.Factory(OkHttp.client()))
        registry.append(ByteBuffer::class.java, Bitmap::class.java, byteBufferBitmapDecoder)
        registry.append(InputStream::class.java, Bitmap::class.java, streamBitmapDecoder)
    }
}
