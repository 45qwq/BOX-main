package com.fongmi.android.tv.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.github.catvod.utils.Logger
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.fongmi.android.tv.App
import com.fongmi.android.tv.R
import com.fongmi.android.tv.bean.Download
import com.fongmi.android.tv.event.RefreshEvent
import com.fongmi.android.tv.utils.HttpDownloader
import com.fongmi.android.tv.utils.FluxDownDownloader
import com.fongmi.android.tv.utils.DownloadHttpClient
import com.fongmi.android.tv.utils.Notify
import com.github.catvod.utils.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * WorkManager 下载 Worker
 *
 * 功能：OkHttp 流式下载 + setForeground 通知进度 + 失败自动重试 + 完成写数据库
 * 替换 DownloadService 中的 DownloadTask
 */
class DownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val KEY_DOWNLOAD_JSON = "download_json"
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "download_worker_channel"
        private const val MAX_RETRIES = 3
        private const val WORK_PREFIX = "download_"

        /** 入队下载任务 */
        @JvmStatic
        fun enqueue(context: Context, download: Download) {
            val inputData = workDataOf(KEY_DOWNLOAD_JSON to download.toString())
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(inputData)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
                .addTag(WORK_PREFIX + download.id)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    WORK_PREFIX + download.id,
                    ExistingWorkPolicy.REPLACE,
                    request
                )
            Logger.i("DownloadWorker: WorkManager 入队: ${download.vodName}")
        }

        /** 取消下载任务 */
        @JvmStatic
        fun cancel(context: Context, downloadId: String) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_PREFIX + downloadId)
            Logger.i("DownloadWorker: WorkManager 取消: $downloadId")
        }

        /** 取消所有下载任务 */
        @JvmStatic
        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag(WORK_PREFIX)
        }
    }

    private val notificationManager: NotificationManager by lazy {
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override suspend fun doWork(): Result {
        val downloadJson = inputData.getString(KEY_DOWNLOAD_JSON)
            ?: return Result.failure()
        val download = Download.objectFrom(downloadJson)
        if (download.id.isEmpty()) {
            return Result.failure()
        }

        Logger.i("DownloadWorker: Worker 开始下载: ${download.vodName}")

        // 创建通知渠道并设置前台通知
        createNotificationChannel()
        val foregroundInfo = ForegroundInfo(
            NOTIFICATION_ID,
            buildNotification("准备下载: ${download.vodName}", 0)
        )
        setForegroundAsync(foregroundInfo)

        val latch = CountDownLatch(1)

        return withContext(Dispatchers.IO) {
            try {
                val downloadDir = getDownloadDir()
                if (!downloadDir.exists()) downloadDir.mkdirs()

                val fileName = generateFileName(download.vodName ?: "", download.url ?: "")
                val outputFile = File(downloadDir, fileName)

                val isM3U8 = isM3U8Url(download.url ?: "")
                val hasM3U8Header = download.header?.contains("M3U8-Content") ?: false

                if (isM3U8 || hasM3U8Header) {
                    downloadWithM3U8(download, outputFile, latch)
                } else {
                    downloadWithDirect(download, outputFile, latch)
                }

                latch.await(60, TimeUnit.MINUTES)

                if (isStopped) {
                    Result.failure()
                } else {
                    val status = download.status ?: "pending"
                    when (status) {
                        "completed" -> Result.success()
                        else -> {
                            if (runAttemptCount < MAX_RETRIES) {
                                Logger.i("DownloadWorker: Worker 重试: ${runAttemptCount + 1}/$MAX_RETRIES")
                                Result.retry()
                            } else {
                                Result.failure()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (!isStopped) {
                    Logger.e("DownloadWorker: 下载异常", e)
                    updateDownloadStatus(download, "failed")
                    Notify.show("下载失败: ${e.message ?: "未知错误"}")
                }
                if (runAttemptCount < MAX_RETRIES) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            NOTIFICATION_ID,
            buildNotification("下载中...", 0)
        )
    }

    private fun downloadWithM3U8(download: Download, outputFile: File, latch: CountDownLatch) {
        val headers = parseHeaders(download.header)

        val fluxDown = FluxDownDownloader(
            download.url ?: "",
            headers,
            outputFile.parentFile ?: Path.download(),
            outputFile.name,
            object : FluxDownDownloader.Callback {
                override fun onStart() {
                    updateDownloadStatus(download, "downloading")
                }

                override fun onProgress(
                    progress: Int,
                    downloadedSegments: Int,
                    totalSegments: Int,
                    speed: Long
                ) {
                    download.progress = progress
                    download.speed = speed
                    download.segmentInfo = "$downloadedSegments/$totalSegments"
                    App.execute { download.save() }
                    notificationManager.notify(
                        NOTIFICATION_ID,
                        buildNotification(
                            "${download.vodName} ${formatSpeed(speed)}/s",
                            progress
                        )
                    )
                    RefreshEvent.download()
                }

                override fun onMerging() {
                    updateDownloadStatus(download, "merging")
                }

                override fun onSuccess(file: File) {
                    if (!file.exists() || file.length() == 0L) {
                        updateDownloadStatus(download, "failed")
                        Notify.show("下载失败: 合并后的文件不存在或为空")
                        latch.countDown()
                        return
                    }
                    download.filePath = file.absolutePath
                    download.progress = 100
                    App.execute {
                        download.status = "completed"
                        download.save()
                    }
                    notificationManager.notify(
                        NOTIFICATION_ID,
                        buildNotification("${download.vodName} 下载完成", 100)
                    )
                    RefreshEvent.download()
                    Notify.show("下载完成: ${download.vodName}")
                    latch.countDown()
                }

                override fun onError(error: String) {
                    Logger.e("DownloadWorker: M3U8 下载失败: $error")
                    updateDownloadStatus(download, "failed")
                    Notify.show("下载失败: $error")
                    latch.countDown()
                }
            }
        )

        fluxDown.start()
    }

    private fun downloadWithDirect(download: Download, outputFile: File, latch: CountDownLatch) {
        val headers = parseHeaders(download.header)
        var lastGoodSpeed = 0L

        val http = HttpDownloader()
        http.download(
            download.url ?: "",
            outputFile.parentFile ?: Path.download(),
            outputFile.name,
            headers,
            object : HttpDownloader.Callback {
                override fun onStart(totalSize: Long) {
                    updateDownloadStatus(download, "downloading")
                }

                override fun onProgress(
                    progress: Int,
                    speed: Long,
                    downloaded: Long,
                    total: Long
                ) {
                    val actualSpeed = if (speed > 0) {
                        lastGoodSpeed = speed
                        speed
                    } else if (lastGoodSpeed > 0 && progress in 0 until 100) {
                        lastGoodSpeed
                    } else {
                        0
                    }
                    if (progress >= 0) download.progress = progress
                    download.speed = actualSpeed
                    download.status = "downloading"
                    App.execute { download.save() }
                    notificationManager.notify(
                        NOTIFICATION_ID,
                        buildNotification(
                            "${download.vodName} ${formatSpeed(actualSpeed)}/s",
                            if (progress >= 0) progress else 0
                        )
                    )
                    RefreshEvent.download()
                }

                override fun onSuccess(file: File?) {
                    if (file == null || !file.exists() || file.length() == 0L) {
                        updateDownloadStatus(download, "failed")
                        Notify.show("下载失败: 文件不存在或为空")
                        latch.countDown()
                        return
                    }
                    download.filePath = file.absolutePath
                    download.progress = 100
                    App.execute {
                        download.status = "completed"
                        download.save()
                    }
                    notificationManager.notify(
                        NOTIFICATION_ID,
                        buildNotification("${download.vodName} 下载完成", 100)
                    )
                    RefreshEvent.download()
                    Notify.show("下载完成: ${download.vodName}")
                    latch.countDown()
                }

                override fun onError(error: String?) {
                    Logger.e("DownloadWorker: 直链下载失败: $error")
                    updateDownloadStatus(download, "failed")
                    Notify.show("下载失败: $error")
                    latch.countDown()
                }
            }
        )
    }

    private fun updateDownloadStatus(download: Download, status: String) {
        download.status = status
        App.execute { download.save() }
        notificationManager.notify(
            NOTIFICATION_ID,
            buildNotification(
                when (status) {
                    "downloading" -> "${download.vodName} - 下载中"
                    "merging" -> "${download.vodName} - 合并中"
                    "completed" -> "${download.vodName} - 下载完成"
                    "failed" -> "${download.vodName} - 下载失败"
                    else -> "${download.vodName}"
                },
                download.progress
            )
        )
        RefreshEvent.download()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "视频下载(Worker)",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "WorkManager 下载进度通知"
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, progress: Int): Notification {
        val intent = Intent()
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_action_download)
            .setContentTitle(title)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (progress > 0) {
            builder.setProgress(100, progress, false)
                .setContentText("$progress%")
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun getDownloadDir(): File {
        val customPath = com.fongmi.android.tv.Setting.getDownloadPath()
        if (customPath != null && customPath.isNotEmpty()) {
            val dir = File(customPath)
            if (!dir.exists()) dir.mkdirs()
            return dir
        }
        return Path.download()
    }

    private fun generateFileName(vodName: String, url: String): String {
        val cleanName = vodName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        var extension = ".mp4"
        if (url.lowercase().contains(".m3u8")) {
            extension = ".mp4"
        } else if (url.contains(".")) {
            var urlExt = url.substring(url.lastIndexOf("."))
            if (urlExt.contains("?")) urlExt = urlExt.substring(0, urlExt.indexOf("?"))
            if (urlExt.matches(Regex("\\.(mp4|mkv|avi|mov|wmv|flv|webm|ts)"))) {
                extension = urlExt
            }
        }
        return cleanName + extension
    }

    private fun isM3U8Url(url: String): Boolean {
        if (url.isEmpty()) return false
        val lower = url.lowercase()
        if (lower.contains(".m3u8") || lower.contains("m3u8") ||
            lower.contains("hls") || lower.contains("playlist")
        ) {
            return true
        }
        return isM3U8ByContentType(url)
    }

    private val m3u8DetectClient: okhttp3.OkHttpClient = DownloadHttpClient.head()

    private fun isM3U8ByContentType(url: String): Boolean {
        return try {
            val request = okhttp3.Request.Builder().url(url).head().build()
            val response = m3u8DetectClient.newCall(request).execute()
            val contentType = response.header("Content-Type")
            val code = response.code
            response.close()
            Logger.i("DownloadWorker: HEAD检测: url=$url HTTP=$code Content-Type=$contentType")
            if (contentType != null) {
                val lower = contentType.lowercase()
                lower.contains("mpegurl") || lower.contains("m3u8") || lower.contains("vnd.apple")
            } else false
        } catch (e: Exception) {
            false
        }
    }

    private fun formatSpeed(speed: Long): String {
        if (speed < 1024) return "${speed}B"
        if (speed < 1024 * 1024) return String.format("%.1fKB", speed / 1024.0)
        return String.format("%.1fMB", speed / 1024.0 / 1024.0)
    }

    private fun parseHeaders(headerStr: String?): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        if (headerStr != null && headerStr.isNotEmpty()) {
            for (line in headerStr.split("\n")) {
                val idx = line.indexOf(":")
                if (idx > 0) {
                    headers[line.substring(0, idx).trim()] = line.substring(idx + 1).trim()
                }
            }
        }
        return headers
    }
}