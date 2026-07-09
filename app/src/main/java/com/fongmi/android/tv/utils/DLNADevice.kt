package com.fongmi.android.tv.utils

import com.android.cast.dlna.dmc.DLNACastManager
import com.fongmi.android.tv.bean.Device
import java.util.concurrent.CopyOnWriteArrayList

class DLNADevice private constructor() {

    private val devices = CopyOnWriteArrayList<org.fourthline.cling.model.meta.Device<*, *, *>>()

    companion object {
        @JvmStatic
        val instance: DLNADevice by lazy { DLNADevice() }

        @JvmStatic
        fun get(): DLNADevice = instance
    }

    fun getAll(): List<Device> {
        return devices.map { Device.get(it) }
    }

    fun add(item: org.fourthline.cling.model.meta.Device<*, *, *>): List<Device> {
        if (!devices.contains(item)) {
            devices.add(item)
        }
        return getAll()
    }

    fun remove(device: org.fourthline.cling.model.meta.Device<*, *, *>): Device {
        devices.remove(device)
        return Device.get(device)
    }

    fun disconnect() {
        for (device in devices) {
            DLNACastManager.disconnectDevice(device)
        }
        devices.clear()
    }

    fun find(item: Device): org.fourthline.cling.model.meta.Device<*, *, *>? {
        for (device in devices) {
            if (device.identity.udn.identifierString == item.uuid) return device
        }
        return null
    }
}
