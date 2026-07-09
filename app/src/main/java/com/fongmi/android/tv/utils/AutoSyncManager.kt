package com.fongmi.android.tv.utils

import com.fongmi.android.tv.App
import com.fongmi.android.tv.Constant
import com.fongmi.android.tv.Setting
import com.fongmi.android.tv.api.config.VodConfig
import com.fongmi.android.tv.bean.Config
import com.fongmi.android.tv.bean.Device
import com.fongmi.android.tv.bean.History
import com.github.catvod.net.OkHttp
import com.github.catvod.utils.Logger
import java.io.IOException
import java.util.*
import okhttp3.Call
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Response

class AutoSyncManager private constructor() : ScanTask.Listener {

    private val client: OkHttpClient = OkHttp.client(Constant.TIMEOUT_SYNC)
    private var scanTask: ScanTask? = null
    @Volatile
    private var isSyncing = false

    companion object {
        @JvmStatic
        val instance: AutoSyncManager by lazy { AutoSyncManager() }

        @JvmStatic
        fun get(): AutoSyncManager = instance
    }

    fun isAutoSyncEnabled(): Boolean = Setting.isAutoSync()

    fun getSyncInterval(): Int = Setting.getSyncInterval()

    fun performAutoSync() {
        if (isSyncing) {
            Logger.d("AutoSync: 同步正在进行中，跳过本次")
            return
        }
        if (!isAutoSyncEnabled()) {
            Logger.d("AutoSync: 自动同步未启用")
            return
        }
        Logger.d("AutoSync: 开始自动同步")
        isSyncing = true

        val devices = Device.getAll()
        if (devices.isEmpty()) {
            Logger.d("AutoSync: 没有保存的设备，开始扫描局域网")
            scanAndSync()
        } else {
            Logger.d("AutoSync: 找到 ${devices.size} 个已保存的设备")
            syncToDevice(devices[0])
        }
    }

    private fun scanAndSync() {
        scanTask?.stop()
        scanTask = ScanTask(this)
        val savedDevices = Device.getAll()
        val ips = savedDevices.map { it.ip }
        scanTask?.start(ips)
    }

    private fun syncToDevice(device: Device) {
        Logger.d("AutoSync: 同步到设备: ${device.name} (${device.ip})")
        val body = FormBody.Builder().apply {
            add("device", Device.get().toString())
            add("config", Config.vod().toString())
            add("targets", App.gson().toJson(History.get()))
        }.build()
        val mode = Setting.getSyncMode()
        val url = String.format(Locale.getDefault(), "%s/action?do=sync&mode=%d&type=history", device.ip, mode)
        Logger.d("AutoSync: 发送同步请求: $url")

        OkHttp.newCall(client, url, body).enqueue(object : okhttp3.Callback {
            override fun onResponse(call: Call, response: Response) {
                Logger.d("AutoSync: 同步成功")
                isSyncing = false
                App.post { /* 可以在这里发送通知或更新UI */ }
            }

            override fun onFailure(call: Call, e: IOException) {
                Logger.e("AutoSync: 同步失败: ${e.message}")
                isSyncing = false
            }
        })
    }

    override fun onFind(devices: List<Device>) {
        Logger.d("AutoSync: 扫描到 ${devices.size} 个设备")
        scanTask?.stop()
        scanTask = null
        if (devices.isNotEmpty()) {
            syncToDevice(devices[0])
        } else {
            Logger.d("AutoSync: 未找到可用设备")
            isSyncing = false
        }
    }

    fun stop() {
        scanTask?.stop()
        scanTask = null
        isSyncing = false
    }
}
