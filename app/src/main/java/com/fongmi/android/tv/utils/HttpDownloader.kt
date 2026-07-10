package com.fongmi.android.tv.utils

import android.os.Handler
import android.os.Looper
import com.github.catvod.utils.Logger
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request

class HttpDownloader {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val client: OkHttpClient = DownloadHttpClient.get()

    @Volatile
    private var cancelled = false
    var outputFile: File? = null
        private set

    interface Callback {
        fun onStart(totalSize: Long)
        fun onProgress(progress: Int, speed: Long, downloaded: Long, total: Long)
        fun onSuccess(outputFile: File?)
        fun onError(error: String?)
    }

    fun download(url: String, saveDir: File, fileName: String, headers: Map<String, String>?, callback: Callback?) {
        executor.submit {
            try {
                Logger.d("Http下载开始: $url")
                if (!saveDir.exists()) saveDir.mkdirs()
                outputFile = File(saveDir, fileName)
                okHttpDownload(url, outputFile!!, headers, callback)
            } catch (e: Exception) {
                Logger.e("Http下载失败: ${e.message}")
                callback?.let { mainHandler.post { it.onError("下载失败: ${e.message}") } }
            }
        }
    }

    private fun okHttpDownload(url: String, outputFile: File, headers: Map<String, String>?, callback: Callback?) {
        val hb = Headers.Builder()
        if (headers != null) {
            for ((key, value) in headers) {
                hb.add(key, value)
            }
        }
        val hasUA = headers != null && (headers["User-Agent"] != null || headers["user-agent"] != null)
        if (!hasUA) {
            hb.add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        }
        okHttpDownloadRetry(url, outputFile, hb.build(), callback, 1)
    }

    private fun okHttpDownloadRetry(url: String, outputFile: File, headers: Headers, callback: Callback?, attempt: Int) {
        try {
            if (attempt > 1) {
                val delay = 1000L * attempt
                Logger.d("Http重试($attempt/3) 等待 ${delay}ms")
                Thread.sleep(delay)
            }
            if (outputFile.exists()) outputFile.delete()

            val expectedSize = getContentLength(url, headers)
            val request = Request.Builder().url(url).headers(headers).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                val body = if (response.body != null) response.body!!.string() else ""
                response.close()
                throw Exception("HTTP ${response.code} $body")
            }

            val responseLength = response.body!!.contentLength().coerceAtLeast(0)
            val totalSize = maxOf(expectedSize, responseLength)

            Logger.d("Http开始下载(尝试$attempt/3), HEAD: ${expectedSize}bytes, 响应: ${responseLength}bytes")
            callback?.let { mainHandler.post { it.onStart(totalSize) } }

            var downloaded = 0L
            var lastTime = System.currentTimeMillis()
            var lastBytes = 0L

            try {
                response.body!!.byteStream().use { is_ ->
                    FileOutputStream(outputFile).use { fos ->
                        val buffer = ByteArray(BUFFER_SIZE * 4)
                        var bytesRead: Int
                        while (is_.read(buffer).also { bytesRead = it } != -1) {
                            if (cancelled) {
                                fos.flush()
                                return
                            }
                            fos.write(buffer, 0, bytesRead)
                            downloaded += bytesRead

                            val now = System.currentTimeMillis()
                            if (now - lastTime >= PROGRESS_INTERVAL_MS) {
                                val timeDiff = now - lastTime
                                val speed = if (timeDiff > 0) (downloaded - lastBytes) * 1000 / timeDiff else 0L
                                val progress = if (totalSize > 0) (downloaded * 100 / totalSize).toInt() else -1
                                callback?.let { mainHandler.post { it.onProgress(progress, speed, downloaded, totalSize) } }
                                lastTime = now
                                lastBytes = downloaded
                            }
                        }
                        fos.flush()
                    }
                }
            } finally {
                response.close()
            }

            if (cancelled) return

            if (!verifyFileIntegrity(outputFile, downloaded, totalSize, expectedSize, callback)) {
                return
            }

            Logger.d("Http下载完成: ${outputFile.absolutePath} 大小: ${outputFile.length()}")
            callback?.let { mainHandler.post { it.onSuccess(outputFile) } }

        } catch (e: Exception) {
            Logger.e("Http下载失败(尝试$attempt/3): ${e.message}")
            if (outputFile.exists()) outputFile.delete()
            if (cancelled) return
            if (attempt < 3) {
                okHttpDownloadRetry(url, outputFile, headers, callback, attempt + 1)
            } else {
                callback?.let { mainHandler.post { it.onError("下载失败: ${e.message}") } }
            }
        }
    }

    private fun getContentLength(url: String, headers: Headers): Long {
        return try {
            val headRequest = Request.Builder().url(url).headers(headers).head().build()
            val headResponse = client.newCall(headRequest).execute()
            val length = if (headResponse.body != null) headResponse.body!!.contentLength() else -1L
            headResponse.close()
            length.coerceAtLeast(0)
        } catch (e: Exception) {
            Logger.d("HEAD请求失败，忽略: ${e.message}")
            0
        }
    }

    private fun verifyFileIntegrity(outputFile: File, downloaded: Long, totalSize: Long, expectedSize: Long, callback: Callback?): Boolean {
        if (totalSize > 0 && downloaded < totalSize) {
            Logger.e("下载不完整: 期望 ${totalSize}bytes, 实际 ${downloaded}bytes")
            if (outputFile.exists()) outputFile.delete()
            val errorMsg = "下载不完整: 期望 ${totalSize}bytes, 实际 ${downloaded}bytes"
            callback?.let { mainHandler.post { it.onError(errorMsg) } }
            return false
        }
        val fileSize = if (outputFile.exists()) outputFile.length() else 0L
        if (totalSize > 0 && fileSize < totalSize) {
            Logger.e("文件大小不匹配: 期望 ${totalSize}bytes, 磁盘 ${fileSize}bytes")
            if (outputFile.exists()) outputFile.delete()
            val errorMsg = "文件大小不匹配: 期望 ${totalSize}bytes, 磁盘 ${fileSize}bytes"
            callback?.let { mainHandler.post { it.onError(errorMsg) } }
            return false
        }
        if (fileSize > 0 && downloaded > 0 && fileSize != downloaded) {
            Logger.w("文件大小与下载量不一致: 文件=$fileSize 下载=$downloaded")
        }
        if (totalSize > 0 && fileSize > 0) {
            val diff = Math.abs(fileSize - totalSize)
            if (diff > totalSize * 0.01 && diff > 1024 * 1024) {
                Logger.w("文件大小偏差较大: 期望=$totalSize 实际=$fileSize 偏差=$diff")
            }
        }
        return true
    }

    fun cancel() {
        cancelled = true
    }

    fun shutdown() {
        cancel()
        if (!executor.isShutdown) executor.shutdownNow()
    }

    companion object {
        private const val BUFFER_SIZE = 8192
        private const val PROGRESS_INTERVAL_MS = 300L
    }
}
