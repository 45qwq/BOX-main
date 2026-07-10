package com.fongmi.android.tv.utils

import com.github.catvod.utils.Logger
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URI
import java.nio.channels.FileChannel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * FluxDown 风格 HLS 下载器
 *
 * 功能：M3U8 解析 → TS 并发下载（2线程）→ AES-128 解密 → 合并 TS
 * 使用 OkHttp，正确处理 Header（Referer/UA/Cookie）
 * 正确处理 Master/Media M3U8
 * AES-128 Key 拉取失败时详细日志
 */
class FluxDownDownloader(
    private val m3u8Url: String,
    private val headers: Map<String, String>,
    private val outputDir: File,
    private val outputFileName: String,
    private val callback: Callback
) {

    interface Callback {
        fun onStart()
        fun onProgress(progress: Int, downloadedSegments: Int, totalSegments: Int, speed: Long)
        fun onMerging()
        fun onSuccess(file: File)
        fun onError(error: String)
    }

    companion object {
        private const val MAX_CONCURRENT = 2       // 每任务2个线程
        private const val CONNECT_TIMEOUT = 30L     // 秒
        private const val READ_TIMEOUT = 60L        // 秒
        private const val MAX_RETRIES = 2           // 每个片段最多重试
    }

    // 每个任务独立 taskId，防止多个任务共享 outputDir 时临时文件冲突
    private val taskId = "task_${System.nanoTime()}_${(Math.random() * 100000).toInt()}"
    private var cancelled = false
    private val executor = Executors.newFixedThreadPool(MAX_CONCURRENT)
    private val activeCalls = ConcurrentHashMap.newKeySet<Call>()

    private val okHttpClient: OkHttpClient = DownloadHttpClient.get()

    private var totalSegments = 0
    private val downloadedCount = AtomicInteger(0)
    private val failedCount = AtomicInteger(0)
    private val totalBytes = AtomicLong(0)
    private var startTime = 0L

    // ==================== 数据结构 ====================

    private data class M3U8Segment(
        val url: String,
        val duration: Double,
        val sequence: Int
    )

    private data class AESKeyInfo(
        val uri: String,
        val ivHex: String?          // 原始 IV 十六进制字符串（含 0x 前缀）
    )

    private data class MediaPlaylist(
        val segments: List<M3U8Segment>,
        val keyInfo: AESKeyInfo?,
        val targetDuration: Int,
        val mediaSequence: Int
    )

    // ==================== 公共入口 ====================

    fun start() {
        startTime = System.currentTimeMillis()
        callback.onStart()

        try {
            // 1. 解析播放列表（处理 Master → Media 重定向）
            val mediaUrl = resolvePlaylist()
            if (cancelled) return

            // 2. 解析媒体播放列表
            val playlist = parseMediaPlaylist(mediaUrl)
            if (cancelled) return

            totalSegments = playlist.segments.size
            if (totalSegments == 0) {
                callback.onError("播放列表中没有 TS 片段")
                return
            }
            Logger.i("FluxDownDownloader: 共 $totalSegments 个 TS 片段")

            // 3. 获取 AES-128 密钥
            var aesKey: ByteArray? = null
            if (playlist.keyInfo != null) {
                aesKey = fetchAESKey(playlist.keyInfo)
                if (aesKey == null) {
                    callback.onError("无法获取 AES-128 密钥: ${playlist.keyInfo.uri}")
                    return
                }
                Logger.i("FluxDownDownloader: AES-128 密钥获取成功 (${aesKey.size} bytes)")
            }
            if (cancelled) return

            // 4. 并发下载所有 TS 片段
            downloadSegments(playlist.segments, playlist.keyInfo, aesKey)
            if (cancelled) return

            // 如果失败片段过多，报错
            if (failedCount.get() > 0) {
                val failRatio = failedCount.get().toFloat() / totalSegments
                if (failRatio > 0.2f) {
                    callback.onError("下载失败: ${failedCount.get()}/$totalSegments 片段失败")
                    return
                }
                Logger.w("FluxDownDownloader: ${failedCount.get()} 个片段下载失败（<= 20%），继续合并")
            }

            // 5. 合并 TS 文件
            callback.onMerging()
            mergeTSFiles(totalSegments)

        } catch (e: Exception) {
            if (!cancelled) {
                Logger.e("FluxDownDownloader: 下载异常", e)
                callback.onError("下载失败: ${e.message ?: "未知错误"}")
            }
        } finally {
            executor.shutdownNow()
            // 如果未正常完成（取消或异常），清理临时片段文件
            if (cancelled || failedCount.get() > 0) {
                cleanupTempFiles()
            }
            cleanup()
        }
    }

    fun cancel() {
        cancelled = true
        for (call in activeCalls) {
            call.cancel()
        }
        activeCalls.clear()
        executor.shutdownNow()
        // 取消时清理已下载的临时片段文件
        cleanupTempFiles()
    }

    // ==================== M3U8 解析 ====================

    /**
     * 解析播放列表 URL
     * 如果是 Master Playlist，取最高带宽的 Media Playlist URL
     */
    private fun resolvePlaylist(): String {
        val responseBody = httpGetString(m3u8Url)
            ?: throw RuntimeException("无法获取播放列表: $m3u8Url")
        Logger.i("FluxDownDownloader: 播放列表获取成功, 大小=${responseBody.length} bytes")

        // 判断是否为 Master Playlist
        if (responseBody.contains("#EXT-X-STREAM-INF")) {
            Logger.i("FluxDownDownloader: 检测到 Master Playlist，解析 Media Playlist URL")
            return resolveMasterPlaylist(responseBody)
        }

        // 直接返回 Media 原始 URL（无需下载两次）
        return m3u8Url
    }

    /**
     * 解析 Master Playlist，返回最高带宽的 Media Playlist URL
     */
    private fun resolveMasterPlaylist(masterContent: String): String {
        var bestUrl: String? = null
        var bestBandwidth = -1L
        var nextIsVariant = false

        for (line in masterContent.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            if (trimmed.startsWith("#EXT-X-STREAM-INF:")) {
                nextIsVariant = true
                // 解析带宽
                val bandwidthMatch = Regex("BANDWIDTH=(\\d+)").find(trimmed)
                if (bandwidthMatch != null) {
                    val bw = bandwidthMatch.groupValues[1].toLongOrNull() ?: 0L
                    if (bw > bestBandwidth) {
                        bestBandwidth = bw
                    }
                }
            } else if (nextIsVariant && !trimmed.startsWith("#")) {
                nextIsVariant = false
                val variantUrl = resolveUrl(m3u8Url, trimmed)
                // 记录第一个变体，后面有更高带宽的会替换
                if (bestUrl == null || bestBandwidth > 0) {
                    bestUrl = variantUrl
                }
            }
        }

        if (bestUrl == null) {
            throw RuntimeException("Master Playlist 中未找到 Media Playlist URL")
        }

        Logger.i("FluxDownDownloader: 选中 Media Playlist (带宽=${bestBandwidth}): $bestUrl")
        return bestUrl
    }

    /**
     * 解析 Media Playlist，返回片段列表和密钥信息
     */
    private fun parseMediaPlaylist(url: String): MediaPlaylist {
        val content = httpGetString(url)
            ?: throw RuntimeException("无法获取 Media Playlist: $url")

        val segments = mutableListOf<M3U8Segment>()
        var currentDuration = 0.0
        var currentSequence = 0
        var targetDuration = 10
        var mediaSequence = 0
        var keyInfo: AESKeyInfo? = null

        for (line in content.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            when {
                trimmed.startsWith("#EXT-X-TARGETDURATION:") -> {
                    val match = Regex(":(\\d+)").find(trimmed)
                    if (match != null) {
                        targetDuration = match.groupValues[1].toIntOrNull() ?: 10
                    }
                }
                trimmed.startsWith("#EXT-X-MEDIA-SEQUENCE:") -> {
                    val match = Regex(":(\\d+)").find(trimmed)
                    if (match != null) {
                        mediaSequence = match.groupValues[1].toIntOrNull() ?: 0
                        currentSequence = mediaSequence
                    }
                }
                trimmed.startsWith("#EXTINF:") -> {
                    val match = Regex(":(\\d*\\.?\\d+)").find(trimmed)
                    if (match != null) {
                        currentDuration = match.groupValues[1].toDoubleOrNull() ?: 0.0
                    }
                }
                trimmed.startsWith("#EXT-X-KEY:") -> {
                    keyInfo = parseKeyTag(trimmed)
                }
                !trimmed.startsWith("#") -> {
                    // 这是一个片段 URL
                    val segmentUrl = resolveUrl(url, trimmed)
                    segments.add(M3U8Segment(segmentUrl, currentDuration, currentSequence))
                    currentSequence++
                    currentDuration = 0.0
                }
            }
        }

        Logger.i("FluxDownDownloader: Media Playlist 解析完成: ${segments.size} 个片段" +
                ", targetDuration=$targetDuration, mediaSequence=$mediaSequence" +
                (if (keyInfo != null) ", AES-128 加密" else ""))

        return MediaPlaylist(segments, keyInfo, targetDuration, mediaSequence)
    }

    /**
     * 解析 #EXT-X-KEY 标签
     * 示例: #EXT-X-KEY:METHOD=AES-128,URI="key.key",IV=0x1234...
     */
    private fun parseKeyTag(tag: String): AESKeyInfo? {
        if (!tag.contains("METHOD=AES-128")) return null

        val uriMatch = Regex("URI=\"([^\"]+)\"").find(tag)
        val uri = uriMatch?.groupValues?.get(1) ?: return null

        val ivMatch = Regex("IV=(0x[0-9A-Fa-f]+)").find(tag)
        val ivHex = ivMatch?.groupValues?.get(1)

        Logger.i("FluxDownDownloader: AES-128 密钥信息: URI=$uri, IV=${ivHex ?: "使用序列号"}")
        return AESKeyInfo(uri, ivHex)
    }

    // ==================== AES-128 密钥获取 ====================

    /**
     * 获取 AES-128 密钥
     */
    private fun fetchAESKey(keyInfo: AESKeyInfo): ByteArray? {
        val keyUrl = resolveUrl(m3u8Url, keyInfo.uri)
        Logger.i("FluxDownDownloader: 获取 AES-128 密钥: $keyUrl")

        val body = httpGetBytes(keyUrl)
        if (body == null) {
            Logger.e("FluxDownDownloader: AES-128 密钥获取失败: $keyUrl (检查 Header/Referer/UA/Cookie)")
            return null
        }

        if (body.size != 16) {
            Logger.e("FluxDownDownloader: AES-128 密钥长度异常: 期望 16 bytes, 实际 ${body.size} bytes")
            // 仍返回，让解密阶段报错
        }

        return body
    }

    /**
     * 计算片段 IV
     * 如果 M3U8 中指定了 IV 则使用，否则使用序列号的大端表示
     */
    private fun computeSegmentIV(keyInfo: AESKeyInfo?, sequence: Int): ByteArray {
        if (keyInfo?.ivHex != null) {
            val hex = keyInfo.ivHex.removePrefix("0x").removePrefix("0X")
            val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            // 补齐到 16 字节
            if (bytes.size == 16) return bytes
            if (bytes.size < 16) {
                val padded = ByteArray(16)
                System.arraycopy(bytes, 0, padded, 16 - bytes.size, bytes.size)
                return padded
            }
            // 超过 16 字节只取后 16 字节
            return bytes.copyOfRange(bytes.size - 16, bytes.size)
        }

        // 使用序列号的大端 16 字节表示
        val iv = ByteArray(16)
        var s = sequence.toLong()
        for (i in 15 downTo 8) {
            iv[i] = (s and 0xFF).toByte()
            s = s shr 8
        }
        return iv
    }

    // ==================== TS 片段下载 ====================

    /**
     * 并发下载所有 TS 片段，每任务 2 个线程
     */
    private fun downloadSegments(segments: List<M3U8Segment>, keyInfo: AESKeyInfo?, aesKey: ByteArray?) {
        val latch = CountDownLatch(segments.size)

        for ((index, segment) in segments.withIndex()) {
            if (cancelled) break

            val tempFile = File(outputDir, "${taskId}_seg_${"%06d".format(index)}.tmp")
            executor.submit {
                try {
                    if (!cancelled) {
                        val success = downloadSingleSegment(segment, index, keyInfo, aesKey, tempFile)
                        if (success) {
                            downloadedCount.incrementAndGet()
                            totalBytes.addAndGet(tempFile.length())
                        } else {
                            failedCount.incrementAndGet()
                        }

                        // 更新进度
                        val done = downloadedCount.get()
                        val elapsed = System.currentTimeMillis() - startTime
                        val speed = if (elapsed > 0) (totalBytes.get() * 1000 / elapsed) else 0L
                        val progress = (done * 100f / totalSegments).toInt()
                        callback.onProgress(progress, done, totalSegments, speed)
                    }
                } catch (e: Exception) {
                    if (!cancelled) {
                        Logger.e("FluxDownDownloader: 片段下载异常 index=$index", e)
                        failedCount.incrementAndGet()
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        // 等待所有下载完成或取消
        latch.await(30, TimeUnit.MINUTES)
        Logger.i("FluxDownDownloader: 下载完成: ${downloadedCount.get()}/$totalSegments 成功, ${failedCount.get()} 失败")
    }

    /**
     * 下载单个 TS 片段，支持重试
     */
    private fun downloadSingleSegment(
        segment: M3U8Segment,
        index: Int,
        keyInfo: AESKeyInfo?,
        aesKey: ByteArray?,
        tempFile: File
    ): Boolean {
        var lastError: String? = null

        for (attempt in 1..MAX_RETRIES) {
            if (cancelled) return false

            try {
                val request = buildRequest(segment.url)
                val call = okHttpClient.newCall(request)
                activeCalls.add(call)

                call.execute().use { response ->
                    activeCalls.remove(call)

                    if (!response.isSuccessful) {
                        lastError = "HTTP ${response.code} ${response.message}"
                        Logger.w("FluxDownDownloader: 片段 $index 下载失败 (尝试 $attempt/$MAX_RETRIES): $lastError, URL: ${segment.url}")
                        // 如果被限流，等待重试
                        if (response.code == 429 || response.code == 503) {
                            Thread.sleep(2000L * attempt)
                        }
                        return@use false
                    }

                    val body = response.body
                    if (body == null) {
                        lastError = "响应体为空"
                        Logger.w("FluxDownDownloader: 片段 $index 响应体为空 (尝试 $attempt/$MAX_RETRIES)")
                        return@use false
                    }

                    // 写入临时文件
                    val data = body.bytes()
                    if (data.isEmpty()) {
                        lastError = "数据为空"
                        Logger.w("FluxDownDownloader: 片段 $index 数据为空 (尝试 $attempt/$MAX_RETRIES)")
                        return@use false
                    }

                    // 如果有 AES-128 加密，解密
                    val decryptedData = if (aesKey != null && keyInfo != null) {
                        decryptAES128(data, aesKey, computeSegmentIV(keyInfo, segment.sequence))
                    } else {
                        data
                    }

                    if (decryptedData == null) {
                        lastError = "AES-128 解密失败"
                        Logger.e("FluxDownDownloader: 片段 $index AES-128 解密失败 (尝试 $attempt/$MAX_RETRIES)")
                        return@use false
                    }

                    // 写入文件
                    tempFile.parentFile?.mkdirs()
                    FileOutputStream(tempFile).use { fos ->
                        fos.write(decryptedData)
                        fos.flush()
                    }

                    return true
                }
            } catch (e: Exception) {
                lastError = e.message ?: e.javaClass.simpleName
                Logger.w("FluxDownDownloader: 片段 $index 下载异常 (尝试 $attempt/$MAX_RETRIES): $lastError")
                if (attempt < MAX_RETRIES) {
                    Thread.sleep(1000L * attempt)
                }
            }
        }

        Logger.e("FluxDownDownloader: 片段 $index 下载失败 (已重试 $MAX_RETRIES 次): $lastError, URL: ${segment.url}")
        return false
    }

    // ==================== AES-128 解密 ====================

    /**
     * AES-128/CBC/PKCS7Padding 解密
     */
    private fun decryptAES128(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray? {
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
            val keySpec = SecretKeySpec(key, "AES")
            val ivSpec = IvParameterSpec(iv)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            cipher.doFinal(data)
        } catch (e: Exception) {
            Logger.e("FluxDownDownloader: AES-128 解密失败: ${e.message}")
            null
        }
    }

    // ==================== TS 合并 ====================

    /**
     * 将所有临时 TS 片段按顺序合并为一个文件
     * 使用 FileChannel.transferTo 零拷贝合并，大幅提升大文件合并速度
     */
    private fun mergeTSFiles(segmentCount: Int) {
        val outputFile = File(outputDir, outputFileName)
        Logger.i("FluxDownDownloader: 开始合并 TS 文件: ${outputFile.absolutePath}")

        // 确保父目录存在
        outputFile.parentFile?.mkdirs()

        // 先删除已存在的输出文件
        if (outputFile.exists()) {
            outputFile.delete()
        }

        var totalMerged = 0L

        try {
            FileOutputStream(outputFile).use { fos ->
                val outChannel: FileChannel = fos.channel

                for (i in 0 until segmentCount) {
                    if (cancelled) {
                        outputFile.delete()
                        return
                    }

                    val tempFile = File(outputDir, "${taskId}_seg_${"%06d".format(i)}.tmp")
                    if (!tempFile.exists() || tempFile.length() == 0L) {
                        Logger.w("FluxDownDownloader: 合并跳过缺失片段 $i")
                        continue
                    }

                    FileInputStream(tempFile).use { fis ->
                        val inChannel: FileChannel = fis.channel
                        var transferred: Long
                        var position: Long = 0L
                        val size: Long = tempFile.length()
                        while (position < size) {
                            transferred = inChannel.transferTo(position, size - position, outChannel)
                            if (transferred <= 0) break
                            position += transferred
                            totalMerged += transferred
                        }
                    }
                }
                outChannel.force(false)
            }

            if (cancelled) {
                outputFile.delete()
                return
            }

            // 验证
            if (outputFile.exists() && outputFile.length() > 0) {
                Logger.i("FluxDownDownloader: 合并完成: ${outputFile.absolutePath}, 大小=${outputFile.length()} bytes")
                // 回调异常（如 Notify 在主线程之外的崩溃）不应导致已合并文件被删
                try {
                    callback.onSuccess(outputFile)
                } catch (e: Exception) {
                    Logger.e("FluxDownDownloader: onSuccess 回调异常, 但文件已合并成功", e)
                    // 文件已合并成功，通知上层任务完成
                }
            } else {
                Logger.e("FluxDownDownloader: 合并失败: 输出文件为空")
                callback.onError("合并后的文件为空")
            }

        } catch (e: Exception) {
            Logger.e("FluxDownDownloader: 合并异常", e)
            outputFile.delete()
            callback.onError("合并失败: ${e.message ?: "未知错误"}")
        } finally {
            // 清理临时文件
            for (i in 0 until segmentCount) {
                val tempFile = File(outputDir, "${taskId}_seg_${"%06d".format(i)}.tmp")
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            }
        }
    }

    // ==================== HTTP 工具 ====================

    /**
     * 构建带有原始 Header 的 OkHttp Request
     */
    private fun buildRequest(url: String): Request {
        val builder = Request.Builder().url(url)

        // 传递所有自定义 Header（Referer, UA, Cookie 等）
        for ((key, value) in headers) {
            if (!key.equals("Content-Length", ignoreCase = true) &&
                !key.equals("Content-Type", ignoreCase = true) &&
                !key.equals("Host", ignoreCase = true)) {
                builder.header(key, value)
            }
        }

        return builder.build()
    }

    /**
     * HTTP GET 返回字符串
     */
    private fun httpGetString(url: String): String? {
        try {
            val request = buildRequest(url)
            val call = okHttpClient.newCall(request)
            activeCalls.add(call)

            val response = call.execute()
            activeCalls.remove(call)

            if (!response.isSuccessful) {
                Logger.w("FluxDownDownloader: HTTP GET 失败: ${response.code} ${response.message} URL: $url")
                response.close()
                return null
            }

            val body = response.body?.string()
            response.close()
            return body
        } catch (e: Exception) {
            if (!cancelled) {
                Logger.e("FluxDownDownloader: HTTP GET 异常: $url, ${e.message}")
            }
            return null
        }
    }

    /**
     * HTTP GET 返回字节数组
     */
    private fun httpGetBytes(url: String): ByteArray? {
        try {
            val request = buildRequest(url)
            val call = okHttpClient.newCall(request)
            activeCalls.add(call)

            val response = call.execute()
            activeCalls.remove(call)

            if (!response.isSuccessful) {
                Logger.w("FluxDownDownloader: HTTP GET bytes 失败: ${response.code} ${response.message} URL: $url")
                response.close()
                return null
            }

            val body = response.body?.bytes()
            response.close()
            return body
        } catch (e: Exception) {
            if (!cancelled) {
                Logger.e("FluxDownDownloader: HTTP GET bytes 异常: $url, ${e.message}")
            }
            return null
        }
    }

    // ==================== URL 工具 ====================

    /**
     * 解析相对 URL 为绝对 URL
     */
    private fun resolveUrl(baseUrl: String, relativeUrl: String): String {
        if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) {
            return relativeUrl
        }

        return try {
            val base = URI(baseUrl)
            val resolved = base.resolve(relativeUrl)
            resolved.toString()
        } catch (e: Exception) {
            // 容错：手动拼接
            val base = baseUrl.substringBeforeLast("/")
            "$base/$relativeUrl"
        }
    }

    // ==================== 清理 ====================

    private fun cleanup() {
        activeCalls.clear()
        executor.shutdownNow()
    }

    /**
     * 删除本次下载产生的所有临时片段文件
     * 在取消、下载失败或异常时调用，防止 .tmp 文件残留
     */
    private fun cleanupTempFiles() {
        for (i in 0 until totalSegments) {
            val tempFile = File(outputDir, "${taskId}_seg_${"%06d".format(i)}.tmp")
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }
}