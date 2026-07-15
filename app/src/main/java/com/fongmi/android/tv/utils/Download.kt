package com.fongmi.android.tv.utils

import com.fongmi.android.tv.App
import com.github.catvod.net.OkHttp
import com.github.catvod.utils.Logger
import com.github.catvod.utils.Path
import com.google.common.net.HttpHeaders
import java.io.*
import okhttp3.Response

@Deprecated("Use DownloadManagerImpl or OkHttp directly")
class Download(
    val url: String?,
    private val file: File?,
    private val fallbackUrl: String? = null,
    private var callback: Callback? = null
) {

    interface Callback {
        fun progress(progress: Int)
        fun error(msg: String?)
        fun success(file: File?)
    }

    companion object {
        private const val MAX_RETRY_COUNT = 3

        @JvmStatic
        fun create(url: String?, file: File?): Download {
            return create(url, file, null)
        }

        @JvmStatic
        fun create(url: String?, file: File?, callback: Callback?): Download {
            return create(url, file, null, callback)
        }

        @JvmStatic
        fun create(url: String?, file: File?, fallbackUrl: String?, callback: Callback?): Download {
            return Download(url, file, fallbackUrl, callback)
        }

        @JvmStatic
        fun generateDownloadFileName(title: String, url: String): String {
            var fileName = url.substringAfterLast("/").substringBefore("?")
            if (fileName.isEmpty()) {
                fileName = "${title}_${System.currentTimeMillis()}"
            }
            // 移除特殊字符
            fileName = fileName.replace(Regex("[?*:<>|\"]"), "_")
            if (!fileName.contains(".")) {
                fileName += ".mp4"
            }
            // 如果文件名太长，截断并保留扩展名
            if (fileName.length > 100) {
                val ext = fileName.substringAfterLast(".")
                val baseName = fileName.substringBeforeLast(".")
                fileName = baseName.take(90) + "." + ext
            }
            return fileName
        }
    }

    fun start() {
        if (url.isNullOrEmpty()) {
            callback?.let { App.post { it.error("下载URL为空") } }
            return
        }
        if (url.startsWith("file")) return
        if (file == null) {
            callback?.let { App.post { it.error("保存文件路径为空") } }
            return
        }
        if (callback == null) {
            doInBackgroundWithFallback()
        } else {
            App.execute { doInBackgroundWithFallback() }
        }
    }

    private fun doInBackgroundWithFallback() {
        val mainSuccess = doInBackground(url, "主URL")
        if (mainSuccess) return
        if (fallbackUrl != null && fallbackUrl != url) {
            Logger.d("Download: 主URL下载失败，回退到备用URL: $fallbackUrl")
            doInBackground(fallbackUrl, "备用URL")
        }
    }

    private fun doInBackground(downloadUrl: String?, source: String): Boolean {
        var lastException: Exception? = null
        for (attempt in 1..MAX_RETRY_COUNT) {
            try {
                callback?.let { App.post { it.progress(0) } }
                val success = downloadWithUrl(downloadUrl, source, attempt)
                if (success) return true
            } catch (e: Exception) {
                lastException = e
                Logger.w("Download: 下载失败 (来源: $source, 尝试 $attempt/$MAX_RETRY_COUNT): ${e.message}")
                if (attempt < MAX_RETRY_COUNT) {
                    try {
                        val retryDelay = 500L * attempt
                        Thread.sleep(retryDelay)
                        Logger.d("Download: 等待 ${retryDelay}ms 后重试...")
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
            }
        }
        if (callback != null && lastException != null) {
            val errorMsg = lastException.message
            App.post { callback?.error(errorMsg ?: "下载失败") }
        }
        return false
    }

    @Throws(Exception::class)
    private fun downloadWithUrl(downloadUrl: String?, source: String, attempt: Int): Boolean {
        if (downloadUrl.isNullOrEmpty()) throw Exception("下载URL为空")
        if (file == null) throw Exception("保存文件路径为空")

        var res: Response? = null
        var inputStream: InputStream? = null
        try {
            res = OkHttp.newCall(downloadUrl, downloadUrl).execute()
            if (!res.isSuccessful) {
                throw Exception("下载失败: HTTP ${res.code} ${res.message ?: "未知错误"}")
            }
            if (res.body == null) throw Exception("下载失败: 响应体为空")
            inputStream = res.body!!.byteStream() ?: throw Exception("下载失败: 无法获取输入流")
            Path.create(file)

            val contentLengthStr = res.header(HttpHeaders.CONTENT_LENGTH)
            var expectedLength = -1L
            if (!contentLengthStr.isNullOrEmpty()) {
                try {
                    expectedLength = contentLengthStr.toLong()
                    if (expectedLength < 0) expectedLength = -1
                } catch (_: NumberFormatException) {
                    Logger.w("Download: 无法解析Content-Length: $contentLengthStr")
                }
            }

            download(inputStream, expectedLength)
            if (expectedLength > 0 && !verifyDownloadedFile(file, expectedLength)) {
                throw Exception("下载的文件可能已损坏，请重试")
            }

            Logger.d("Download: 下载成功 (来源: $source, 尝试 $attempt/$MAX_RETRY_COUNT)")
            callback?.let { App.post { it.success(file) } }
            return true
        } catch (e: Exception) {
            if (file.exists()) {
                try { file.delete() } catch (ex: Exception) { Logger.w("Download delete", ex) }
            }
            throw e
        } finally {
            try { inputStream?.close() } catch (ex: Exception) { Logger.w("Download close", ex) }
            try { res?.close() } catch (ex: Exception) { Logger.w("Download close", ex) }
        }
    }

    @Throws(Exception::class)
    private fun download(is_: InputStream?, length: Long) {
        if (is_ == null) throw Exception("输入流为空，无法下载")
        BufferedInputStream(is_).use { input ->
            FileOutputStream(file).use { os ->
                val buffer = ByteArray(4096)
                var readBytes: Int
                var totalBytes = 0L
                while (input.read(buffer).also { readBytes = it } != -1) {
                    totalBytes += readBytes
                    os.write(buffer, 0, readBytes)
                    if (length > 0 && callback != null) {
                        val progress = ((totalBytes * 100.0 / length).toInt()).coerceAtMost(100)
                        App.post { callback?.progress(progress) }
                    } else if (callback != null) {
                        App.post { callback?.progress(-1) }
                    }
                }
                if (length <= 0 && callback != null) {
                    App.post { callback?.progress(100) }
                }
            }
        }
    }

    private fun verifyDownloadedFile(file: File?, expectedLength: Long): Boolean {
        try {
            if (file == null || !file.exists() || file.length() == 0L) {
                Logger.e("File verification failed: file does not exist or is empty")
                return false
            }
            if (expectedLength > 0 && file.length() != expectedLength) {
                Logger.e("File size mismatch: expected $expectedLength, actual ${file.length()}")
                return false
            }
            // 视频文件只做基本存在性和大小校验，不再检查 APK/ZIP 头部
            Logger.d("File verification passed: ${file.name} (${file.length()} bytes)")
            return true
        } catch (e: Exception) {
            Logger.e("File verification failed: ${e.message}")
            Logger.e("Error", e)
            return false
        }
    }

    fun cancel() {
        OkHttp.cancel(url)
        if (fallbackUrl != null) OkHttp.cancel(fallbackUrl)
        Path.clear(file)
        callback = null
    }
}
