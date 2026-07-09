package com.fongmi.android.tv.event

import com.fongmi.android.tv.BuildConfig
import org.greenrobot.eventbus.EventBus

data class ActionEvent(val action: String) : AppEvent {

    val isUpdate: Boolean get() = UPDATE == action

    companion object {
        @JvmField val STOP = BuildConfig.APPLICATION_ID + ".stop"
        @JvmField val PREV = BuildConfig.APPLICATION_ID + ".prev"
        @JvmField val NEXT = BuildConfig.APPLICATION_ID + ".next"
        @JvmField val PLAY = BuildConfig.APPLICATION_ID + ".play"
        @JvmField val PAUSE = BuildConfig.APPLICATION_ID + ".pause"
        @JvmField val UPDATE = BuildConfig.APPLICATION_ID + ".update"

        @JvmStatic
        fun send(action: String) {
            EventBus.getDefault().post(ActionEvent(action))
        }

        @JvmStatic fun update() = send(UPDATE)
        @JvmStatic fun next() = send(NEXT)
        @JvmStatic fun prev() = send(PREV)
        @JvmStatic fun pause() = send(PAUSE)
    }
}
