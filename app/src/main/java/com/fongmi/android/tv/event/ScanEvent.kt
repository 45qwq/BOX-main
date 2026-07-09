package com.fongmi.android.tv.event

import org.greenrobot.eventbus.EventBus

data class ScanEvent(val address: String) : AppEvent {
    companion object {
        @JvmStatic
        fun post(address: String) {
            EventBus.getDefault().post(ScanEvent(address))
        }
    }
}
