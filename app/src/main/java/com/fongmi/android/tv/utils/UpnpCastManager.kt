package com.fongmi.android.tv.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.fongmi.android.tv.event.CastEvent
import com.fongmi.android.tv.event.CastEvent.TransportState
import org.fourthline.cling.android.AndroidUpnpService
import org.fourthline.cling.controlpoint.ActionCallback
import org.fourthline.cling.model.action.ActionInvocation
import org.fourthline.cling.model.message.UpnpResponse
import org.fourthline.cling.model.meta.Device
import org.fourthline.cling.model.meta.Service
import org.fourthline.cling.model.types.UDAServiceId
import org.fourthline.cling.registry.Registry
import org.fourthline.cling.registry.RegistryListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * UPnP/DLNA 投屏管理器
 * 基于 Cling core，仅支持 URL 投屏（无 Jetty 本地文件服务器）
 */
object UpnpCastManager : RegistryListener {

    private const val INSTANCE_ID = "0"
    private const val SEEK_UNIT = "REL_TIME"
    private const val CHANNEL = "Master"

    private var upnpService: AndroidUpnpService? = null
    private var selectedDevice: Device<*, *, *>? = null
    private var bound = false
    private var conn: ServiceConnection? = null
    private val handler = Handler(Looper.getMainLooper())
    private var pollRunnable: Runnable? = null

    /** 绑定 UPnP 服务 */
    @JvmStatic
    fun bind(context: Context) {
        if (bound) return
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                upnpService = service as? AndroidUpnpService
                upnpService?.registry?.addListener(this@UpnpCastManager)
                CastEvent.searchStarted()
                upnpService?.controlPoint?.search()
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                upnpService = null
            }
        }
        conn = connection
        val intent = Intent(context, CastUpnpServiceImpl::class.java)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        bound = true
    }

    /** 解绑 UPnP 服务 */
    @JvmStatic
    fun unbind(context: Context) {
        if (!bound) return
        stopPolling()
        upnpService?.registry?.removeListener(this)
        // 必须使用与 bindService 相同的 ServiceConnection 实例，否则 unbindService 抛异常导致 Service 泄漏
        conn?.let {
            try { context.unbindService(it) } catch (_: Exception) {}
        }
        conn = null
        upnpService = null
        bound = false
    }

    /** 开始搜索设备 */
    @JvmStatic
    fun search() {
        CastEvent.searchStarted()
        upnpService?.controlPoint?.search()
    }

    /** 获取已发现的设备列表 */
    @JvmStatic
    fun getDevices(): List<Device<*, *, *>> {
        return upnpService?.registry?.devices?.toList()
            ?.filter { isValidDevice(it) } ?: emptyList()
    }

    /** 选择投屏设备 */
    @JvmStatic
    fun selectDevice(device: Device<*, *, *>?) {
        selectedDevice = device
        CastEvent.deviceSelected(device)
    }

    @JvmStatic
    fun getSelectedDevice(): Device<*, *, *>? = selectedDevice

    @JvmStatic
    fun isCasting(): Boolean = selectedDevice != null

    // ==================== RegistryListener ====================

    override fun remoteDeviceDiscoveryStarted(registry: Registry?, device: org.fourthline.cling.model.meta.RemoteDevice?) {}
    override fun remoteDeviceDiscoveryFailed(registry: Registry?, device: org.fourthline.cling.model.meta.RemoteDevice?, ex: Exception?) {}

    override fun remoteDeviceAdded(registry: Registry?, device: org.fourthline.cling.model.meta.RemoteDevice?) {
        if (device != null && isValidDevice(device)) {
            handler.post { CastEvent.deviceFound(device) }
        }
    }

    override fun remoteDeviceUpdated(registry: Registry?, device: org.fourthline.cling.model.meta.RemoteDevice?) {}
    override fun remoteDeviceRemoved(registry: Registry?, device: org.fourthline.cling.model.meta.RemoteDevice?) {
        if (device != null && isValidDevice(device)) {
            handler.post { CastEvent.deviceRemoved(device) }
        }
    }

    override fun localDeviceAdded(registry: Registry?, device: org.fourthline.cling.model.meta.LocalDevice?) {}
    override fun localDeviceRemoved(registry: Registry?, device: org.fourthline.cling.model.meta.LocalDevice?) {}
    override fun beforeShutdown(registry: Registry?) {}
    override fun afterShutdown() {}

    // ==================== 投屏控制 ====================

    /** 设置播放地址并播放 */
    @JvmStatic
    fun cast(url: String, title: String = "") {
        val device = selectedDevice ?: return
        val service = findAVTransportService(device) ?: return
        val upnp = upnpService ?: return

        val setUri = ActionInvocation(service.getAction("SetAVTransportURI")).apply {
            setInput("InstanceID", INSTANCE_ID)
            setInput("CurrentURI", url)
            setInput("CurrentURIMetaData", buildMetaData(url, title))
        }
        upnp.controlPoint.execute(object : ActionCallback(setUri) {
            override fun success(invocation: ActionInvocation<*>?) {
                play()
                startPolling()
            }
            override fun failure(invocation: ActionInvocation<*>?, operation: UpnpResponse?, defaultMsg: String?) {
                handler.post { CastEvent.error(defaultMsg ?: "设置播放地址失败") }
            }
        })
    }

    /** 播放 */
    @JvmStatic
    fun play() {
        val service = findAVTransportService(selectedDevice ?: return) ?: return
        val invocation = ActionInvocation(service.getAction("Play")).apply {
            setInput("InstanceID", INSTANCE_ID)
            setInput("Speed", "1")
        }
        upnpService?.controlPoint?.execute(object : ActionCallback(invocation) {
            override fun success(invocation: ActionInvocation<*>?) {}
            override fun failure(invocation: ActionInvocation<*>?, operation: UpnpResponse?, defaultMsg: String?) {
                handler.post { CastEvent.error(defaultMsg ?: "播放失败") }
            }
        })
    }

    /** 暂停 */
    @JvmStatic
    fun pause() {
        val service = findAVTransportService(selectedDevice ?: return) ?: return
        val invocation = ActionInvocation(service.getAction("Pause")).apply {
            setInput("InstanceID", INSTANCE_ID)
        }
        upnpService?.controlPoint?.execute(object : ActionCallback(invocation) {
            override fun success(invocation: ActionInvocation<*>?) {}
            override fun failure(invocation: ActionInvocation<*>?, operation: UpnpResponse?, defaultMsg: String?) {
                handler.post { CastEvent.error(defaultMsg ?: "暂停失败") }
            }
        })
    }

    /** 停止 */
    @JvmStatic
    fun stop() {
        val service = findAVTransportService(selectedDevice ?: return) ?: return
        val invocation = ActionInvocation(service.getAction("Stop")).apply {
            setInput("InstanceID", INSTANCE_ID)
        }
        upnpService?.controlPoint?.execute(object : ActionCallback(invocation) {
            override fun success(invocation: ActionInvocation<*>?) {
                stopPolling()
                handler.post { CastEvent.stateChanged(TransportState.STOPPED) }
            }
            override fun failure(invocation: ActionInvocation<*>?, operation: UpnpResponse?, defaultMsg: String?) {
                handler.post { CastEvent.error(defaultMsg ?: "停止失败") }
            }
        })
    }

    /** 跳转（target 格式: h:mm:ss 或 hh:mm:ss） */
    @JvmStatic
    fun seek(target: String) {
        val service = findAVTransportService(selectedDevice ?: return) ?: return
        val invocation = ActionInvocation(service.getAction("Seek")).apply {
            setInput("InstanceID", INSTANCE_ID)
            setInput("Unit", SEEK_UNIT)
            setInput("Target", target)
        }
        upnpService?.controlPoint?.execute(object : ActionCallback(invocation) {
            override fun success(invocation: ActionInvocation<*>?) {}
            override fun failure(invocation: ActionInvocation<*>?, operation: UpnpResponse?, defaultMsg: String?) {
                handler.post { CastEvent.error(defaultMsg ?: "跳转失败") }
            }
        })
    }

    /** 跳转（毫秒） */
    @JvmStatic
    fun seekMillis(millis: Long) {
        seek(formatTime(millis))
    }

    /** 设置音量（0-100） */
    @JvmStatic
    fun setVolume(volume: Int) {
        val service = findRenderingControlService(selectedDevice ?: return) ?: return
        val invocation = ActionInvocation(service.getAction("SetVolume")).apply {
            setInput("InstanceID", INSTANCE_ID)
            setInput("Channel", CHANNEL)
            setInput("DesiredVolume", volume.toString())
        }
        upnpService?.controlPoint?.execute(object : ActionCallback(invocation) {
            override fun success(invocation: ActionInvocation<*>?) {
                handler.post { CastEvent.volumeChanged(volume, false) }
            }
            override fun failure(invocation: ActionInvocation<*>?, operation: UpnpResponse?, defaultMsg: String?) {
                handler.post { CastEvent.error(defaultMsg ?: "设置音量失败") }
            }
        })
    }

    /** 设置静音 */
    @JvmStatic
    fun setMute(muted: Boolean) {
        val service = findRenderingControlService(selectedDevice ?: return) ?: return
        val invocation = ActionInvocation(service.getAction("SetMute")).apply {
            setInput("InstanceID", INSTANCE_ID)
            setInput("Channel", CHANNEL)
            setInput("DesiredMute", if (muted) "1" else "0")
        }
        upnpService?.controlPoint?.execute(object : ActionCallback(invocation) {
            override fun success(invocation: ActionInvocation<*>?) {
                handler.post { CastEvent.volumeChanged(-1, muted) }
            }
            override fun failure(invocation: ActionInvocation<*>?, operation: UpnpResponse?, defaultMsg: String?) {
                handler.post { CastEvent.error(defaultMsg ?: "设置静音失败") }
            }
        })
    }

    /** 断开投屏 */
    @JvmStatic
    fun disconnect() {
        stop()
        selectedDevice = null
        stopPolling()
        CastEvent.deviceSelected(null)
    }

    // ==================== 状态轮询 ====================

    @JvmStatic
    fun startPolling() {
        stopPolling()
        pollRunnable = object : Runnable {
            override fun run() {
                queryPosition()
                queryTransportInfo()
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(pollRunnable!!)
    }

    @JvmStatic
    fun stopPolling() {
        pollRunnable?.let { handler.removeCallbacks(it) }
        pollRunnable = null
    }

    private fun queryPosition() {
        val service = findAVTransportService(selectedDevice ?: return) ?: return
        val invocation = ActionInvocation(service.getAction("GetPositionInfo")).apply {
            setInput("InstanceID", INSTANCE_ID)
        }
        upnpService?.controlPoint?.execute(object : ActionCallback(invocation) {
            override fun success(invocation: ActionInvocation<*>?) {
                val relTime = invocation?.getOutput("RelTime")?.value?.toString() ?: return
                val duration = invocation.getOutput("TrackDuration")?.value?.toString() ?: "0:00:00"
                val current = parseTime(relTime)
                val total = parseTime(duration)
                handler.post { CastEvent.positionChanged(current, total) }
            }
            override fun failure(invocation: ActionInvocation<*>?, operation: UpnpResponse?, defaultMsg: String?) {}
        })
    }

    private fun queryTransportInfo() {
        val service = findAVTransportService(selectedDevice ?: return) ?: return
        val invocation = ActionInvocation(service.getAction("GetTransportInfo")).apply {
            setInput("InstanceID", INSTANCE_ID)
        }
        upnpService?.controlPoint?.execute(object : ActionCallback(invocation) {
            override fun success(invocation: ActionInvocation<*>?) {
                val state = invocation?.getOutput("CurrentTransportState")?.value?.toString() ?: return
                val ts = when (state) {
                    "PLAYING" -> TransportState.PLAYING
                    "PAUSED_PLAYBACK" -> TransportState.PAUSED
                    "TRANSITIONING" -> TransportState.TRANSITIONING
                    else -> TransportState.STOPPED
                }
                handler.post { CastEvent.stateChanged(ts) }
            }
            override fun failure(invocation: ActionInvocation<*>?, operation: UpnpResponse?, defaultMsg: String?) {}
        })
    }

    // ==================== 辅助方法 ====================

    private fun isValidDevice(device: Device<*, *, *>): Boolean {
        val type = device.type?.type ?: return false
        val hasAVTransport = findAVTransportService(device) != null
        return type == "MediaRenderer" && hasAVTransport
    }

    private fun findAVTransportService(device: Device<*, *, *>): Service<*, *>? {
        return device.findService(UDAServiceId("AVTransport"))
    }

    private fun findRenderingControlService(device: Device<*, *, *>): Service<*, *>? {
        return device.findService(UDAServiceId("RenderingControl"))
    }

    private fun buildMetaData(url: String, title: String): String {
        val safeTitle = title.ifEmpty { "Video" }
        return """<?xml version="1.0" encoding="UTF-8"?>
            |<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
            |<item id="1" parentID="0" restricted="1">
            |<dc:title>$safeTitle</dc:title>
            |<upnp:class>object.item.videoItem</upnp:class>
            |<res protocolInfo="http-get:*:video/*:*">$url</res>
            |</item>
            |</DIDL-Lite>""".trimMargin()
    }

    private fun formatTime(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "0:%02d:%02d", minutes, seconds)
        }
    }

    private fun parseTime(timeStr: String): Long {
        if (timeStr.isBlank() || timeStr == "NOT_IMPLEMENTED" || timeStr == "00:00:00") return 0
        return try {
            val sdf = SimpleDateFormat("H:mm:ss", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("GMT+0")
            val date = sdf.parse(timeStr) ?: return 0
            date.time
        } catch (_: Exception) { 0 }
    }
}
