package com.fongmi.android.tv.utils

import android.widget.TextView
import com.fongmi.android.tv.App
import com.github.catvod.utils.Logger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Timer
import java.util.TimerTask
import java.util.Locale

class Clock {

    private var format: SimpleDateFormat? = null
    @set:JvmName("setCallbackProp")
    var callback: Callback? = null
    private val date = Date()
    private var view: TextView? = null
    private var timer: Timer? = null

    interface Callback {
        fun onTimeChanged()
    }

    companion object {
        @JvmStatic
        fun create(): Clock = Clock()

        @JvmStatic
        fun create(view: TextView): Clock = Clock().view(view).format("HH:mm:ss")
    }

    fun view(view: TextView): Clock {
        this.view = view
        return this
    }

    fun format(format: String): Clock {
        this.format = SimpleDateFormat(format, Locale.getDefault())
        return this
    }

    fun setCallback(callback: Callback?) {
        this.callback = callback
    }

    fun start() {
        val t = Timer()
        timer = t
        t.schedule(object : TimerTask() {
            override fun run() {
                App.post { doJob() }
            }
        }, 0L, 1000L)
    }

    private fun doJob() {
        try {
            date.time = System.currentTimeMillis()
            callback?.onTimeChanged()
            view?.text = format?.format(date)
        } catch (e: Exception) {
            Logger.w("Clock doJob", e)
        }
    }

    fun stop(): Clock {
        timer?.cancel()
        return this
    }

    fun release() {
        timer?.cancel()
        callback = null
    }
}
