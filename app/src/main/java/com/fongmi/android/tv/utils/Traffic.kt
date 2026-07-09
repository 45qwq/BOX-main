package com.fongmi.android.tv.utils

import android.net.TrafficStats
import android.view.View
import android.widget.TextView
import com.fongmi.android.tv.App
import java.text.DecimalFormat

object Traffic {

    private val format = DecimalFormat("#.0")
    private const val UNIT_KB = " KB/s"
    private const val UNIT_MB = " MB/s"
    private var lastTotalRxBytes: Long = 0
    private var lastTimeStamp: Long = 0

    @JvmStatic
    fun setSpeed(view: TextView) {
        if (unsupported()) return
        view.text = getSpeed()
        view.visibility = View.VISIBLE
    }

    private fun unsupported(): Boolean {
        return TrafficStats.getUidRxBytes(App.get().applicationInfo.uid) == TrafficStats.UNSUPPORTED.toLong()
    }

    private fun getSpeed(): String {
        val nowTimeStamp = System.currentTimeMillis()
        val nowTotalRxBytes = TrafficStats.getTotalRxBytes() / 1024
        val speed = (nowTotalRxBytes - lastTotalRxBytes) * 1000 / Math.max(nowTimeStamp - lastTimeStamp, 1)
        lastTimeStamp = nowTimeStamp
        lastTotalRxBytes = nowTotalRxBytes
        return if (speed < 1000) "$speed$UNIT_KB" else format.format(speed / 1024f) + UNIT_MB
    }

    @JvmStatic
    fun reset() {
        lastTotalRxBytes = 0
        lastTimeStamp = 0
    }
}
