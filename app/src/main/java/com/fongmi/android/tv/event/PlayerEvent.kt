package com.fongmi.android.tv.event

import org.greenrobot.eventbus.EventBus

data class PlayerEvent(val tag: String, val state: Int) : AppEvent {
    companion object {
        const val PREPARE = 0
        const val TRACK = 21
        const val SIZE = 11

        @JvmStatic fun prepare(tag: String) = EventBus.getDefault().post(PlayerEvent(tag, PREPARE))
        @JvmStatic fun track(tag: String) = EventBus.getDefault().post(PlayerEvent(tag, TRACK))
        @JvmStatic fun size(tag: String) = EventBus.getDefault().post(PlayerEvent(tag, SIZE))
        @JvmStatic fun state(tag: String, state: Int) = EventBus.getDefault().post(PlayerEvent(tag, state))
    }
}
