package com.fongmi.android.tv.player

import com.fongmi.android.tv.bean.Episode
import com.fongmi.android.tv.bean.Flag
import com.fongmi.android.tv.player.extractor.Force
import com.fongmi.android.tv.player.extractor.JianPian
import com.fongmi.android.tv.player.extractor.Push
import com.fongmi.android.tv.player.extractor.Strm
import com.fongmi.android.tv.player.extractor.Video
import com.fongmi.android.tv.player.extractor.Youtube
import com.fongmi.android.tv.utils.UrlUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit
import kotlin.jvm.JvmStatic
import kotlin.jvm.Throws

object Source {

    private val extractors = listOf(
        Force(),
        JianPian(),
        Push(),
        Strm(),
        Video(),
        Youtube()
    )

    /** 挂起函数：用于协程上下文，带 30 秒超时 */
    suspend fun parse(flags: List<Flag>) {
        // 收集所有需要解析的 YouTube 解析器
        val parsers = mutableListOf<Youtube.Parser>()

        for (flag in flags) {
            val iterator = flag.getEpisodes().iterator()
            while (iterator.hasNext()) {
                val url = iterator.next().getUrl()
                if (Youtube.Parser.match(url)) {
                    parsers.add(Youtube.Parser.get(url))
                    iterator.remove()
                }
            }
        }

        if (parsers.isEmpty()) return

        val scope = CoroutineScope(SupervisorJob())
        val deferred = parsers.map { parser ->
            scope.async { parser.call() }
        }
        val results = withTimeoutOrNull(30_000L) {
            deferred.awaitAll()
        } ?: throw RuntimeException("YouTube 解析超时")

        // 将解析结果追加到对应 Flag 的 episodes 中
        // 保持原有顺序：按 Flag 顺序依次将结果追加
        var parserIdx = 0
        for (flag in flags) {
            val episodes = flag.getEpisodes()
            // 只对原有 URL 为 YouTube 的位置追加结果
            // 这里简单处理：遍历所有 parser 结果并追加
            while (parserIdx < results.size) {
                val parsedEpisodes = results[parserIdx]
                if (parsedEpisodes.isNotEmpty()) {
                    episodes.addAll(parsedEpisodes)
                }
                parserIdx++
                // 一个 Flag 只对应一个 Parser（按原逻辑）
                break
            }
        }
    }

    /** 阻塞式桥接：供 Java 调用（SiteViewModel 等） */
    @JvmStatic
    fun parseBlocking(flags: List<Flag>) {
        kotlinx.coroutines.runBlocking { parse(flags) }
    }

    /** Java 兼容单例获取 */
    @JvmStatic
    fun get(): Source = this

    @JvmStatic
    fun fetch(url: String): String {
        val extractor = getExtractor(url)
        return if (extractor != null) extractor.fetch(url) else url
    }

    @JvmStatic
    fun fetch(result: com.fongmi.android.tv.bean.Result): String {
        val url = result.getUrl().v()
        val extractor = getExtractor(url)
        if (extractor != null) result.setParse(0)
        if (extractor is Video) result.setParse(1)
        return if (extractor == null) url else extractor.fetch(url)
    }

    @JvmStatic
    fun stop() {
        for (extractor in extractors) extractor.stop()
    }

    @JvmStatic
    fun exit() {
        for (extractor in extractors) extractor.exit()
    }

    private fun getExtractor(url: String): Extractor? {
        val host = UrlUtil.host(url)
        val scheme = UrlUtil.scheme(url)
        return extractors.firstOrNull { it.match(scheme, host) }
    }

    interface Extractor {
        fun match(scheme: String, host: String): Boolean
        @Throws(Exception::class)
        fun fetch(url: String): String
        fun stop()
        fun exit()
    }
}