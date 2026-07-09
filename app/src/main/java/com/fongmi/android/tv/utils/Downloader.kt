package com.fongmi.android.tv.utils

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import com.github.catvod.utils.Logger
import androidx.core.content.ContextCompat
import com.fongmi.android.tv.App
import com.fongmi.android.tv.R
import com.fongmi.android.tv.bean.Download
import com.fongmi.android.tv.bean.Result
import com.fongmi.android.tv.service.DownloadService
import com.fongmi.android.tv.service.DownloadWorker
import com.github.catvod.utils.Path
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.net.URI
import java.util.*

class Downloader private constructor() {

    private var result: Result? = null
    private var activity: Activity? = null
    private var title: String? = null
    private var image: String? = null

    companion object {
        @JvmStatic
        val instance: Downloader by lazy { Downloader() }

        @JvmStatic
        fun get(): Downloader = instance
    }

    fun title(title: String): Downloader {
        this.title = title
        return this
    }

    fun image(image: String): Downloader {
        this.image = image
        return this
    }

    fun result(result: Result): Downloader {
        this.result = result
        return this
    }

    fun start(activity: Activity) {
        this.activity = activity
        if (result?.hasMsg() == true) {
            Notify.show(result?.msg)
        } else {
            download()
        }
    }

    fun start(activity: Activity, url: String?, headers: Map<String, String>?) {
        this.activity = activity
        if (url.isNullOrEmpty()) {
            Notify.show(R.string.error_play_url)
            return
        }
        val realUrl = extractRealUrl(url)
        if (realUrl == null || isBlockedUrl(realUrl)) {
            Notify.show(R.string.download_blocked)
            return
        }
        if (checkFileOverwrite(activity, realUrl) { downloadDirect(realUrl, headers) }) {
            return
        }
        downloadDirect(realUrl, headers)
    }

    private fun checkFileOverwrite(activity: Activity, url: String, onProceed: Runnable): Boolean {
        val fileName = Download.generateDownloadFileName(title ?: "video", url)
        val targetFile = File(Path.download(), fileName)
        if (targetFile.exists()) {
            val fileSize = formatFileSize(targetFile.length())
            MaterialAlertDialogBuilder(activity)
                .setTitle("文件已存在")
                .setMessage("$fileName\n\n大小: $fileSize\n\n是否重新下载？（将覆盖原文件）")
                .setCancelable(true)
                .setNegativeButton("取消", null)
                .setPositiveButton("重新下载") { _, _ ->
                    targetFile.delete()
                    onProceed.run()
                }
                .show()
            return true
        }
        return false
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        return if (bytes < 1024 * 1024) String.format("%.1f KB", bytes / 1024.0)
        else String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }

    private fun extractRealUrl(url: String?): String? {
        if (url.isNullOrEmpty()) return url
        return try {
            val host = URI.create(url).host?.lowercase()
            if (host == null) return url
            val isProxy = host == "127.0.0.1" || host == "0.0.0.0" || host == "localhost" || host.startsWith("127.")
            if (!isProxy) return url
            val qIdx = url.indexOf('?')
            if (qIdx < 0) return null
            val query = url.substring(qIdx + 1)
            val params = query.split("&")
            for (param in params) {
                if (param.startsWith("url=")) {
                    val encoded = param.substring(4)
                    val decoded = Uri.decode(encoded)
                    if (decoded != null && decoded.isNotEmpty() &&
                        (decoded.startsWith("http://") || decoded.startsWith("https://"))) {
                        Logger.i("Downloader: 从代理URL提取真实地址: $decoded")
                        return decoded
                    }
                }
            }
            Logger.w("Downloader: 代理URL中未找到有效真实地址: $url")
            null
        } catch (e: Exception) {
            Logger.w("Downloader: 提取真实URL异常", e)
            url
        }
    }

    private fun isBlockedUrl(url: String?): Boolean {
        if (url == null) return true
        return try {
            val host = URI.create(url).host?.lowercase()
            if (host == null) return false
            host == "127.0.0.1" || host == "0.0.0.0" || host == "localhost" || host.startsWith("127.")
        } catch (_: Exception) {
            false
        }
    }

    private fun downloadDirect(url: String?, headers: Map<String, String>?) {
        try {
            val downloadId = UUID.randomUUID().toString()
            val headersStr = convertHeadersToString(headers)
            if (url.isNullOrEmpty()) {
                Notify.show(R.string.error_play_url)
                return
            }
            val isM3U8 = url.lowercase().contains(".m3u8") ||
                    url.lowercase().contains("m3u8") ||
                    url.lowercase().contains("playlist")
            if (isM3U8) {
                Logger.i("Downloader: 检测到M3U8流媒体: $url")
            }
            val download = Download(downloadId, image, title, url, headersStr)
            App.execute {
                try {
                    download.save()
                } catch (_: Exception) {
                    App.post { Notify.show("下载失败: 数据库错误") }
                    return@execute
                }
                App.post {
                    try {
                        if (DownloadService.isWorkManagerEnabled()) {
                            DownloadWorker.enqueue(App.get(), download)
                        } else {
                            DownloadService.startDownload(download)
                        }
                        Notify.show(R.string.download_start)
                    } catch (_: Exception) {
                        Notify.show("下载失败: 服务启动错误")
                    }
                }
            }
        } catch (e: Exception) {
            Notify.show("下载失败: ${e.message}")
        }
    }

    private fun download() {
        try {
            val downloadId = UUID.randomUUID().toString()
            val url = result?.getRealUrl()
            val headers = convertHeadersToString(result?.getHeaders())
            if (url.isNullOrEmpty()) {
                Notify.show(R.string.error_play_url)
                return
            }
            val realUrl = extractRealUrl(url)
            if (realUrl == null || isBlockedUrl(realUrl)) {
                Notify.show(R.string.download_blocked)
                return
            }
            val isM3U8 = realUrl.lowercase().contains(".m3u8") ||
                    realUrl.lowercase().contains("m3u8") ||
                    realUrl.lowercase().contains("playlist")
            if (isM3U8) {
                Logger.i("Downloader: 检测到M3U8流媒体: $realUrl")
            }
            val download = Download(downloadId, image, title, realUrl, headers)
            App.execute {
                try {
                    download.save()
                } catch (_: Exception) {
                    App.post { Notify.show("下载失败: 数据库错误") }
                    return@execute
                }
                App.post {
                    try {
                        if (DownloadService.isWorkManagerEnabled()) {
                            DownloadWorker.enqueue(App.get(), download)
                        } else {
                            DownloadService.startDownload(download)
                        }
                        Notify.show(R.string.download_start)
                    } catch (_: Exception) {
                        Notify.show("下载失败: 服务启动错误")
                    }
                }
            }
        } catch (e: Exception) {
            Notify.show("下载失败: ${e.message}")
        }
    }

    private fun convertHeadersToString(headers: Map<String, String>?): String {
        if (headers.isNullOrEmpty()) return ""
        return headers.entries.joinToString("\n") { "${it.key}: ${it.value}" }
    }
}
