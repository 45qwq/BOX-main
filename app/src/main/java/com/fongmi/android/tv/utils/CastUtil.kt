package com.fongmi.android.tv.utils

import android.text.Html
import android.text.TextUtils

import com.fongmi.android.tv.bean.CastMember

object CastUtil {

    @JvmStatic
    fun parseCastMembers(text: String?, type: CastMember.CastType): List<CastMember> {
        val members = mutableListOf<CastMember>()
        if (TextUtils.isEmpty(text)) return members

        val cleanText = sanitizeHtml(text!!)
        if (TextUtils.isEmpty(cleanText)) return members

        val names: Array<String> = when {
            cleanText.contains(",") -> cleanText.split(",").toTypedArray()
            cleanText.contains("/") -> cleanText.split("/").toTypedArray()
            cleanText.contains("、") -> cleanText.split("、").toTypedArray()
            cleanText.matches(Regex(".*\\s{2,}.*")) -> cleanText.split(Regex("\\s{2,}")).toTypedArray()
            cleanText.contains(" ") -> {
                val parts = cleanText.split(Regex("\\s+")).toTypedArray()
                if (parts.size > 1 && parts.size <= 20) {
                    val allReasonable = parts.all { it.length in 2..10 }
                    if (allReasonable) parts else null
                } else null
            }
            else -> null
        } ?: arrayOf(cleanText)

        for (name in names) {
            val trimmed = name.trim()
            if (trimmed.isNotEmpty()) {
                members.add(CastMember(trimmed, type))
            }
        }
        return members
    }

    private fun sanitizeHtml(text: String): String {
        if (TextUtils.isEmpty(text)) return ""
        return Html.fromHtml(text, Html.FROM_HTML_MODE_COMPACT).toString().trim()
    }

    fun isValidCastName(name: String?): Boolean {
        return name != null && name.trim().isNotEmpty()
    }
}
