package com.fongmi.android.tv.utils

import com.fongmi.android.tv.bean.Vod
import com.github.catvod.utils.Logger
import java.util.Collections

/**
 * 搜索结果优化器
 * 功能：
 * 1. 结果去重 - 根据 vodId 或 vodName + vodYear 去重
 * 2. 相关性排序 - 根据标题与关键词的匹配程度排序
 * 3. 精确匹配优先 - 标题以关键词开头的优先显示
 * 4. 过滤无效结果 - 过滤标题为空或无效的结果
 */
object SearchResultOptimizer {

    /**
     * 优化搜索结果
     * @param list 原始搜索结果列表
     * @param keyword 搜索关键词
     * @return 优化后的结果列表
     */
    @JvmStatic
    fun optimize(list: List<Vod>?, keyword: String): List<Vod> {
        if (list.isNullOrEmpty()) {
            return ArrayList()
        }

        // 1. 过滤无效结果
        val filtered = filterInvalid(list)

        // 2. 去重
        val deduplicated = deduplicate(filtered)

        // 3. 相关性排序
        return sortByRelevance(deduplicated, keyword)
    }

    /**
     * 过滤无效结果
     */
    private fun filterInvalid(list: List<Vod>): List<Vod> {
        val result = ArrayList<Vod>()
        for (vod in list) {
            val name = vod.vodName
            // 过滤标题为空、null 或纯空白的结果
            if (name.isNullOrBlank()) {
                continue
            }
            // 过滤标题太短的结果（小于2个字符）
            if (name.trim { it <= ' ' }.length < 2) {
                continue
            }
            result.add(vod)
        }
        return result
    }

    /**
     * 去除重复结果
     * 优先使用 vodId 去重，其次使用 vodName + vodYear 组合
     */
    private fun deduplicate(list: List<Vod>): List<Vod> {
        val result = ArrayList<Vod>()
        val seenIds = HashSet<String>()
        val seenNames = HashMap<String, Int>()

        for (vod in list) {
            val id = vod.vodId
            val name = vod.vodName
            val year = vod.vodYear

            // 优先根据 vodId 去重
            if (!id.isNullOrEmpty()) {
                if (seenIds.contains(id)) {
                    continue
                }
                seenIds.add(id)
            } else {
                // 如果没有 vodId，则根据 name + year 组合去重
                val key = (name + "_" + (year ?: "")).lowercase().trim { it <= ' ' }
                if (seenNames.containsKey(key)) {
                    // 保留已有结果，丢弃重复的
                    continue
                }
                seenNames[key] = 1
            }
            result.add(vod)
        }
        return result
    }

    /**
     * 根据相关性排序
     * 排序优先级：
     * 1. 标题完全匹配关键词（权重最高）
     * 2. 标题以关键词开头
     * 3. 标题包含关键词
     * 4. 按年份降序（越新的越靠前）
     */
    private fun sortByRelevance(list: List<Vod>, keyword: String): List<Vod> {
        if (keyword.isNullOrEmpty()) {
            return list
        }

        val searchKey = keyword.lowercase().trim { it <= ' ' }

        // 计算每个结果的匹配分数
        for (vod in list) {
            vod.searchScore = calculateScore(vod, searchKey)
        }

        // 按分数降序排序
        Collections.sort(list) { v1: Vod, v2: Vod ->
            // 首先按相关性分数排序
            val scoreDiff = v2.searchScore - v1.searchScore
            if (scoreDiff != 0) {
                return@sort scoreDiff
            }
            // 分数相同则按年份降序
            val year1 = v1.vodYear
            val year2 = v2.vodYear
            if (year1 != null && year2 != null) {
                try {
                    val y1 = year1.toInt()
                    val y2 = year2.toInt()
                    return@sort y2 - y1 // 降序
                } catch (e: NumberFormatException) {
                    Logger.w("SearchResultOptimizer year", e)
                }
            }
            0
        }

        return list
    }

    /**
     * 计算搜索结果的相关性分数
     * @param vod 视频对象
     * @param keyword 搜索关键词
     * @return 相关性分数 (0-100)
     */
    private fun calculateScore(vod: Vod, keyword: String): Int {
        var score = 0
        val name = vod.vodName ?: return 0

        val lowerName = name.lowercase()

        // 精确匹配标题（50分）
        if (lowerName == keyword) {
            score += 50
        }
        // 标题以关键词开头（40分）
        else if (lowerName.startsWith(keyword)) {
            score += 40
        }
        // 标题以关键词开头（前缀匹配，考虑空格）（35分）
        else if (lowerName.trim { it <= ' ' }.startsWith(keyword)) {
            score += 35
        }
        // 标题包含完整关键词（30分）
        else if (lowerName.contains(keyword)) {
            score += 30
        }
        // 标题包含关键词的所有字符（但不是连续包含）（20分）
        else if (containsAllChars(lowerName, keyword)) {
            score += 20
        }

        // 标题长度惩罚：太长的标题适当降低分数
        if (lowerName.length > 30) {
            score -= 5
        }

        // 附加信息匹配加分
        // 导演匹配（+10分）
        val director = vod.vodDirector
        if (!director.isNullOrEmpty() && director.lowercase().contains(keyword)) {
            score += 10
        }
        // 演员匹配（+10分）
        val actor = vod.vodActor
        if (!actor.isNullOrEmpty() && actor.lowercase().contains(keyword)) {
            score += 10
        }

        return Math.max(score, 0)
    }

    /**
     * 检查字符串是否包含所有字符（用于模糊匹配）
     */
    private fun containsAllChars(source: String, chars: String): Boolean {
        for (c in chars.toCharArray()) {
            if (source.indexOf(c) == -1) {
                return false
            }
        }
        return true
    }
}
