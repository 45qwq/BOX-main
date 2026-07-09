package com.fongmi.android.tv.event

import com.fongmi.android.tv.bean.Config
import com.fongmi.android.tv.bean.Device
import com.fongmi.android.tv.bean.History
import org.greenrobot.eventbus.EventBus

data class CastEvent(val config: Config, val device: Device, val history: History) : AppEvent {
    companion object {
        @JvmStatic
        fun post(config: Config, device: Device, history: History) {
            EventBus.getDefault().post(CastEvent(config, device, history))
        }
    }
}
