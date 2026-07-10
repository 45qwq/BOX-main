package com.fongmi.android.tv.utils

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.view.View
import android.widget.ImageView
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.signature.ObjectKey
import com.fongmi.android.tv.App
import com.fongmi.android.tv.R
import com.fongmi.android.tv.Setting
import com.github.catvod.utils.Json
import jahirfiquitiva.libs.textdrawable.TextDrawable
import com.google.common.net.HttpHeaders


object ImgUtil {

    /**
     * 基于内存大小的 LruCache，限制 8MB
     * 超过限制时自动淘汰最近最少使用的条目
     */
    private val urlCache = object : androidx.collection.LruCache<String, Any>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Any): Int {
            // 估算缓存条目占用的内存大小
            return when (value) {
                is String -> value.length * 2  // char 占 2 字节
                is com.bumptech.glide.load.model.GlideUrl -> 256  // 估算 GlideUrl 对象大小
                else -> 512  // 默认估算大小
            }
        }
    }

    private fun getSignature(url: String): ObjectKey {
        return ObjectKey(url + "_" + Setting.getQuality())
    }

    @JvmStatic
    fun load(url: String?, error: Int, target: CustomTarget<Drawable>) {
        if (TextUtils.isEmpty(url)) target.onLoadFailed(ResUtil.getDrawable(error))
        else GlideApp.with(App.get()).asDrawable().load(getUrl(url)).error(error).skipMemoryCache(false).dontAnimate().diskCacheStrategy(DiskCacheStrategy.ALL).signature(getSignature(url!!)).into(target)
    }

    @JvmStatic
    fun rect(text: String, url: String?, view: ImageView) {
        load(text, url, view, ImageView.ScaleType.CENTER, true)
    }

    @JvmStatic
    fun oval(text: String, url: String?, view: ImageView) {
        load(text, url, view, ImageView.ScaleType.CENTER, false)
    }

    @JvmStatic
    fun load(text: String, url: String?, view: ImageView, scaleType: ImageView.ScaleType, rect: Boolean) {
        view.scaleType = scaleType
        if (!TextUtils.isEmpty(url)) GlideApp.with(App.get()).asBitmap().load(getUrl(url)).placeholder(R.drawable.ic_img_loading).error(R.drawable.ic_img_error).skipMemoryCache(false).dontAnimate().diskCacheStrategy(DiskCacheStrategy.ALL).sizeMultiplier(Setting.getThumbnail()).signature(getSignature(url!!)).listener(GlideHelper.getBitmapListener(view, scaleType)).into(view)
        else if (text.isNotEmpty()) view.setImageDrawable(getTextDrawable(text.substring(0, 1), rect))
        else view.setImageResource(R.drawable.ic_img_error)
    }

    @JvmStatic
    fun loadVod(text: String, url: String?, view: ImageView) {
        view.scaleType = ImageView.ScaleType.CENTER
        if (!TextUtils.isEmpty(url)) GlideApp.with(App.get()).asBitmap().load(getUrl(url)).placeholder(R.drawable.ic_img_loading).diskCacheStrategy(DiskCacheStrategy.ALL).listener(GlideHelper.getBitmapListener(view)).into(view)
        else if (text.isNotEmpty()) view.setImageDrawable(getTextDrawable(text.substring(0, 1), true))
        else view.setImageResource(R.drawable.ic_img_error)
    }

    @JvmStatic
    fun loadLive(url: String?, view: ImageView) {
        view.visibility = if (TextUtils.isEmpty(url)) View.GONE else View.VISIBLE
        if (TextUtils.isEmpty(url)) view.setImageResource(R.drawable.ic_img_empty)
        else GlideApp.with(App.get()).asBitmap().load(getUrl(url)).error(R.drawable.ic_img_empty).skipMemoryCache(false).dontAnimate().diskCacheStrategy(DiskCacheStrategy.ALL).signature(getSignature(url!!)).into(view)
    }

    private fun getTextDrawable(text: String, rect: Boolean): Drawable {
        val builder = TextDrawable.Builder().withBorder(ResUtil.dp2px(2), ColorGenerator.get700(text))
        return if (rect) builder.buildRoundRect(text, ColorGenerator.get400(text), ResUtil.dp2px(8))
        else builder.buildRound(text, ColorGenerator.get400(text))
    }

    @JvmStatic
    fun getUrl(url: String?): Any? {
        if (url == null) return null
        val cached = urlCache[url]
        if (cached != null) return cached

        var param: String? = null
        var processedUrl = UrlUtil.convert(url)
        if (processedUrl.startsWith("data:")) {
            urlCache.put(processedUrl, processedUrl)
            return processedUrl
        }
        val builder = LazyHeaders.Builder()
        if (processedUrl.contains("@Headers=")) addHeader(builder, processedUrl.split("@Headers=")[1].split("@")[0].also { param = it })
        if (processedUrl.contains("@Cookie=")) builder.addHeader(HttpHeaders.COOKIE, processedUrl.split("@Cookie=")[1].split("@")[0].also { param = it })
        if (processedUrl.contains("@Referer=")) builder.addHeader(HttpHeaders.REFERER, processedUrl.split("@Referer=")[1].split("@")[0].also { param = it })
        if (processedUrl.contains("@User-Agent=")) builder.addHeader(HttpHeaders.USER_AGENT, processedUrl.split("@User-Agent=")[1].split("@")[0].also { param = it })
        processedUrl = if (param == null) processedUrl else processedUrl.split("@")[0]
        if (TextUtils.isEmpty(processedUrl)) return null
        val result: GlideUrl = GlideUrl(processedUrl, builder.build())
        urlCache.put(processedUrl, result)
        return result
    }

    private fun addHeader(builder: LazyHeaders.Builder, header: String) {
        val map = Json.toMap(Json.parse(header))
        for ((key, value) in map) builder.addHeader(UrlUtil.fixHeader(key), value)
    }
}
