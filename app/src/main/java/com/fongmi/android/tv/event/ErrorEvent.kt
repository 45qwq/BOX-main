package com.fongmi.android.tv.event

import com.fongmi.android.tv.R
import com.fongmi.android.tv.utils.ResUtil
import org.greenrobot.eventbus.EventBus

data class ErrorEvent(val tag: String, val type: ErrorEvent.Type, private val _msg: String? = null) : AppEvent {
    val msg: String?
        get() = when (type) {
            Type.URL -> ResUtil.getString(R.string.error_play_url)
            Type.DRM -> ResUtil.getString(R.string.error_play_drm_scheme)
            Type.FLAG -> ResUtil.getString(R.string.error_play_flag)
            Type.PARSE -> ResUtil.getString(R.string.error_play_parse)
            Type.TIMEOUT -> ResUtil.getString(R.string.error_play_timeout)
            else -> _msg
        }

    enum class Type {
        URL, DRM, FLAG, PARSE, TIMEOUT, EXTRACT
    }

    companion object {
        @JvmStatic fun url(tag: String) = EventBus.getDefault().post(ErrorEvent(tag, Type.URL))
        @JvmStatic fun drm(tag: String) = EventBus.getDefault().post(ErrorEvent(tag, Type.DRM))
        @JvmStatic fun flag(tag: String) = EventBus.getDefault().post(ErrorEvent(tag, Type.FLAG))
        @JvmStatic fun parse(tag: String) = EventBus.getDefault().post(ErrorEvent(tag, Type.PARSE))
        @JvmStatic fun timeout(tag: String) = EventBus.getDefault().post(ErrorEvent(tag, Type.TIMEOUT))
        @JvmStatic fun extract(tag: String, msg: String) = EventBus.getDefault().post(ErrorEvent(tag, Type.EXTRACT, msg))
    }
}
