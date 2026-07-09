package com.fongmi.android.tv.event

import org.greenrobot.eventbus.EventBus

data class RefreshEvent(val type: RefreshEvent.Type, val path: String = "") : AppEvent {
    enum class Type {
        CONFIG, IMAGE, VIDEO, HISTORY, KEEP, SIZE, WALL, DETAIL, PLAYER, SUBTITLE, DANMAKU, DOWNLOAD
    }

    companion object {
        @JvmStatic fun config() = EventBus.getDefault().post(RefreshEvent(Type.CONFIG))
        @JvmStatic fun image() = EventBus.getDefault().post(RefreshEvent(Type.IMAGE))
        @JvmStatic fun video() = EventBus.getDefault().post(RefreshEvent(Type.VIDEO))
        @JvmStatic fun history() = EventBus.getDefault().post(RefreshEvent(Type.HISTORY))
        @JvmStatic fun keep() = EventBus.getDefault().post(RefreshEvent(Type.KEEP))
        @JvmStatic fun size() = EventBus.getDefault().post(RefreshEvent(Type.SIZE))
        @JvmStatic fun wall() = EventBus.getDefault().post(RefreshEvent(Type.WALL))
        @JvmStatic fun detail() = EventBus.getDefault().post(RefreshEvent(Type.DETAIL))
        @JvmStatic fun player() = EventBus.getDefault().post(RefreshEvent(Type.PLAYER))
        @JvmStatic fun subtitle(path: String) = EventBus.getDefault().post(RefreshEvent(Type.SUBTITLE, path))
        @JvmStatic fun danmaku(path: String) = EventBus.getDefault().post(RefreshEvent(Type.DANMAKU, path))
        @JvmStatic fun download() = EventBus.getDefault().post(RefreshEvent(Type.DOWNLOAD))
    }
}
