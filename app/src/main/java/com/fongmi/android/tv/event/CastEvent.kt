package com.fongmi.android.tv.event

import org.fourthline.cling.model.meta.Device
import org.greenrobot.eventbus.EventBus

sealed class CastEvent : AppEvent {
    data class DeviceFound(val device: Device<*, *, *>) : CastEvent()
    data class DeviceRemoved(val device: Device<*, *, *>) : CastEvent()
    data class DeviceSelected(val device: Device<*, *, *>?) : CastEvent()
    data class StateChanged(val state: TransportState) : CastEvent()
    data class PositionChanged(val current: Long, val total: Long) : CastEvent()
    data class VolumeChanged(val volume: Int, val isMuted: Boolean) : CastEvent()
    data class Error(val message: String) : CastEvent()
    object SearchStarted : CastEvent()
    object SearchCompleted : CastEvent()

    enum class TransportState { TRANSITIONING, PLAYING, PAUSED, STOPPED }

    companion object {
        @JvmStatic fun deviceFound(device: Device<*, *, *>) = EventBus.getDefault().post(DeviceFound(device))
        @JvmStatic fun deviceRemoved(device: Device<*, *, *>) = EventBus.getDefault().post(DeviceRemoved(device))
        @JvmStatic fun deviceSelected(device: Device<*, *, *>?) = EventBus.getDefault().post(DeviceSelected(device))
        @JvmStatic fun stateChanged(state: TransportState) = EventBus.getDefault().post(StateChanged(state))
        @JvmStatic fun positionChanged(current: Long, total: Long) = EventBus.getDefault().post(PositionChanged(current, total))
        @JvmStatic fun volumeChanged(volume: Int, muted: Boolean) = EventBus.getDefault().post(VolumeChanged(volume, isMuted = muted))
        @JvmStatic fun error(message: String) = EventBus.getDefault().post(Error(message))
        @JvmStatic fun searchStarted() = EventBus.getDefault().post(SearchStarted)
        @JvmStatic fun searchCompleted() = EventBus.getDefault().post(SearchCompleted)
    }
}
