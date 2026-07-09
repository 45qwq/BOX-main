package com.fongmi.android.tv.event

import org.greenrobot.eventbus.EventBus

data class ServerEvent(val type: ServerEvent.Type, val text: String, val name: String = "") : AppEvent {
    enum class Type {
        SEARCH, PUSH, SETTING, SYNC_SUCCESS
    }

    companion object {
        @JvmStatic fun search(text: String) = EventBus.getDefault().post(ServerEvent(Type.SEARCH, text))
        @JvmStatic fun push(text: String) = EventBus.getDefault().post(ServerEvent(Type.PUSH, text))
        @JvmStatic fun setting(text: String) = EventBus.getDefault().post(ServerEvent(Type.SETTING, text))
        @JvmStatic fun setting(text: String, name: String) = EventBus.getDefault().post(ServerEvent(Type.SETTING, text, name))
        @JvmStatic fun syncSuccess() = EventBus.getDefault().post(ServerEvent(Type.SYNC_SUCCESS, ""))
    }
}
