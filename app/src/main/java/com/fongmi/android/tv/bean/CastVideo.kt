package com.fongmi.android.tv.bean

import androidx.media3.common.C
import com.github.catvod.utils.Util

class CastVideo private constructor(val name: String, url: String, val position: Long) {

    val url: String = if (url.contains("127.0.0.1")) url.replace("127.0.0.1", Util.getIp()) else url

    companion object {
        @JvmStatic
        fun get(name: String, url: String): CastVideo {
            return CastVideo(name, url, C.TIME_UNSET)
        }

        @JvmStatic
        fun get(name: String, url: String, position: Long): CastVideo {
            return CastVideo(name, url, position)
        }
    }
}
