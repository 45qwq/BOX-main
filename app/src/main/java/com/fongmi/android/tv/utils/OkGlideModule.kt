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
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.module.AppGlideModule
import com.github.catvod.net.OkHttp
import java.io.InputStream
import java.nio.ByteBuffer

@GlideModule
@Excludes(AvifGlideModule::class)
class OkGlideModule : AppGlideModule() {

    override fun applyOptions(@NonNull context: Context, @NonNull builder: GlideBuilder) {
        builder.setLogLevel(Log.ERROR)
    }

    override fun registerComponents(@NonNull context: Context, @NonNull glide: Glide, registry: Registry) {
        val byteBufferBitmapDecoder = AvifByteBufferBitmapDecoder(glide.bitmapPool)
        val streamBitmapDecoder = AvifStreamBitmapDecoder(registry.imageHeaderParsers, byteBufferBitmapDecoder, glide.arrayPool)
        registry.replace(GlideUrl::class.java, InputStream::class.java, OkHttpUrlLoader.Factory(OkHttp.client()))
        registry.append(ByteBuffer::class.java, Bitmap::class.java, byteBufferBitmapDecoder)
        registry.append(InputStream::class.java, Bitmap::class.java, streamBitmapDecoder)
    }
}
