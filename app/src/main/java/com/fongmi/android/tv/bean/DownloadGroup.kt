package com.fongmi.android.tv.bean

import java.util.ArrayList

/**
 * 下载分组模型
 * 用于已完成下载按剧集归类展示
 */
data class DownloadGroup(
    val groupKey: String,
    val groupName: String
) {
    val children: MutableList<Download> = ArrayList()
    var expanded: Boolean = true

    val childCount: Int get() = children.size

    fun addChild(download: Download) {
        children.add(download)
    }

    fun isExpanded(): Boolean = expanded

    /**
     * 获取分组的显示子标题
     */
    fun getSubtitle(): String = "${children.size}集"

    companion object {
        /**
         * 从下载列表中提取分组
         * 按 vodId 分组，若 vodId 为空则使用 vodName 的第一个段落作为分组名
         */
        @JvmStatic fun groupBySeries(downloads: List<Download>): List<DownloadGroup> {
            val groupMap = java.util.LinkedHashMap<String, DownloadGroup>()

            for (d in downloads) {
                var key = d.vodId
                val name = d.vodName

                if (key.isNullOrEmpty()) {
                    key = extractSeriesKey(name)
                }

                val groupName = extractSeriesName(name)

                if (!groupMap.containsKey(key)) {
                    groupMap[key] = DownloadGroup(key, groupName)
                }
                groupMap[key]?.addChild(d)
            }

            return ArrayList(groupMap.values)
        }

        /**
         * 从视频名称中提取系列名
         * 例如："我的电视剧 第01集" → "我的电视剧"
         *       "My Show S01E01" → "My Show"
         *       "电影名称" → "电影名称"
         */
        private fun extractSeriesName(vodName: String?): String {
            if (vodName.isNullOrEmpty()) return "未知"

            val separators = arrayOf(" 第", " S", "第", " - ", " -", "- ", "EP", "ep")
            for (sep in separators) {
                val idx = vodName.indexOf(sep)
                if (idx > 0) {
                    return vodName.substring(0, idx).trim()
                }
            }

            val parts = vodName.split("\\s+".toRegex())
            if (parts.size > 1) {
                val first = parts[0].trim()
                if (!first.matches("\\d+".toRegex())) {
                    return first
                }
            }

            return vodName
        }

        private fun extractSeriesKey(vodName: String?): String {
            val name = extractSeriesName(vodName)
            return "${name.hashCode()}_$name"
        }
    }
}
