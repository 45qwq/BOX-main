package com.fongmi.android.tv.event

import org.greenrobot.eventbus.EventBus

data class StateEvent(val type: StateEvent.Type) : AppEvent {
    enum class Type {
        EMPTY, PROGRESS, CONTENT
    }

    companion object {
        @JvmStatic fun empty() = EventBus.getDefault().post(StateEvent(Type.EMPTY))
        @JvmStatic fun progress() = EventBus.getDefault().post(StateEvent(Type.PROGRESS))
        @JvmStatic fun content() = EventBus.getDefault().post(StateEvent(Type.CONTENT))
    }
}
