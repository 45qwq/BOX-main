package com.fongmi.android.tv.utils

import android.net.Uri
import com.github.catvod.utils.UriUtil
import com.google.common.net.HttpHeaders

object UrlUtil {

    @JvmStatic
    fun uri(url: String): Uri {
        return Uri.parse(url.trim().replace("\\", ""))
    }

    @JvmStatic
    fun scheme(url: String?): String {
        return url?.let { scheme(Uri.parse(it)) } ?: ""
    }

    @JvmStatic
    fun scheme(uri: Uri): String {
        val scheme = uri.scheme
        return scheme?.lowercase()?.trim() ?: ""
    }

    @JvmStatic
    fun host(url: String?): String {
        return url?.let { host(Uri.parse(it)) } ?: ""
    }

    @JvmStatic
    fun host(uri: Uri): String {
        val host = uri.host
        return host?.lowercase()?.trim() ?: ""
    }

    @JvmStatic
    fun path(uri: Uri): String {
        val path = uri.path
        return path?.trim() ?: ""
    }

    @JvmStatic
    fun resolve(baseUri: String, referenceUri: String): String {
        return UriUtil.resolve(baseUri, referenceUri)
    }

    @JvmStatic
    fun convert(url: String?): String {
        return url ?: ""
    }

    @JvmStatic
    fun fixHeader(key: String): String {
        if (HttpHeaders.USER_AGENT.equals(key, ignoreCase = true)) return HttpHeaders.USER_AGENT
        if (HttpHeaders.REFERER.equals(key, ignoreCase = true)) return HttpHeaders.REFERER
        if (HttpHeaders.COOKIE.equals(key, ignoreCase = true)) return HttpHeaders.COOKIE
        return key
    }
}
