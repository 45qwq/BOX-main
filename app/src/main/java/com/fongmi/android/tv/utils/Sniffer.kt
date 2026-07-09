package com.fongmi.android.tv.utils

import android.net.Uri
import com.fongmi.android.tv.api.config.VodConfig
import com.fongmi.android.tv.bean.Rule
import com.github.catvod.utils.Json
import java.util.regex.Pattern

object Sniffer {

    @JvmField
    val CLICKER: Pattern = Pattern.compile("\\[a=cr:(\\{.*?\\})\\/](.*?)\\[\\/a]")

    @JvmField
    val AI_PUSH: Pattern = Pattern.compile("(https?|thunder|magnet|ed2k|video):\\S+")

    @JvmField
    val SNIFFER: Pattern = Pattern.compile("https?://[^\\s]{12,}\\.(?:m3u8|mp4|mkv|flv|mp3|m4a|aac|mpd)(?:\\?.*)?|https?://.*?video/tos[^\\s]*|rtmp:[^\\s]+")

    @JvmStatic
    fun getUrl(text: String): String {
        if (Json.isObj(text) || text.contains("$")) return text
        val m = AI_PUSH.matcher(text)
        if (m.find()) return m.group(0)
        return text
    }

    @JvmStatic
    fun isVideoFormat(url: String): Boolean {
        val rule = getRule(UrlUtil.uri(url))
        for (exclude in rule.getExclude()) if (url.contains(exclude)) return false
        for (exclude in rule.getExclude()) if (Pattern.compile(exclude).matcher(url).find()) return false
        for (regex in rule.getRegex()) if (url.contains(regex)) return true
        for (regex in rule.getRegex()) if (Pattern.compile(regex).matcher(url).find()) return true
        if (url.contains("url=http") || url.contains("v=http") || url.contains(".html")) return false
        return SNIFFER.matcher(url).find()
    }

    @JvmStatic
    fun getRegex(uri: Uri): List<String> {
        return getRule(uri).getRegex()
    }

    @JvmStatic
    fun getScript(uri: Uri): List<String> {
        return getRule(uri).getScript()
    }

    private fun getRule(uri: Uri): Rule {
        if (uri.host == null) return Rule.empty()
        val hosts = listOf(UrlUtil.host(uri), UrlUtil.host(uri.getQueryParameter("url"))).joinToString(",")
        for (rule in VodConfig.get().getRules()) for (host in rule.getHosts()) if (Util.containOrMatch(hosts, host)) return rule
        return Rule.empty()
    }
}
