package com.fongmi.android.tv.utils

import android.Manifest
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.github.catvod.utils.Logger
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import com.fongmi.android.tv.App
import com.fongmi.android.tv.R
import com.fongmi.android.tv.databinding.ViewProgressBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class Notify private constructor() {

    private var mDialog: AlertDialog? = null
    private var mToast: Toast? = null
    private var mHandler: Handler? = null
    // 缓存 Toast 的 TextView，避免每次 inflate
    private var mToastView: TextView? = null

    companion object {
        const val DEFAULT = "default"
        const val ID = 9527
        // Toast 显示时长（毫秒）
        private const val TOAST_DURATION = 2500L

        private val instance: Notify by lazy { Notify() }

        @JvmStatic
        private fun get(): Notify = instance

        @JvmStatic
        fun createChannel() {
            val notifyMgr = NotificationManagerCompat.from(App.get())
            notifyMgr.createNotificationChannel(
                NotificationChannelCompat.Builder(DEFAULT, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName("XMBOX").build()
            )
        }

        @JvmStatic
        fun getError(resId: Int, e: Throwable): String {
            return if (TextUtils.isEmpty(e.message)) ResUtil.getString(resId)
            else ResUtil.getString(resId) + "\n" + e.message
        }

        @JvmStatic
        fun show(notification: Notification) {
            if (Build.VERSION.SDK_INT >= 33 && ActivityCompat.checkSelfPermission(App.get(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                val text = notification.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "通知权限未开启"
                show(text)
                return
            }
            NotificationManagerCompat.from(App.get()).notify(ID, notification)
        }

        @JvmStatic
        fun show(resId: Int) {
            if (resId != 0) show(ResUtil.getString(resId))
        }

        @JvmStatic
        fun show(text: String?) {
            get().makeText(text, false)
        }

        @JvmStatic
        fun showCenter(resId: Int) {
            if (resId != 0) showCenter(ResUtil.getString(resId))
        }

        @JvmStatic
        fun showCenter(text: String?) {
            get().makeText(text, true)
        }

        @JvmStatic
        fun progress(context: Context) {
            dismiss()
            get().create(context)
        }

        @JvmStatic
        fun dismiss() {
            try {
                get().mDialog?.dismiss()
            } catch (e: Exception) {
                Logger.w("Notify dismiss", e)
            }
        }
    }

    private fun create(context: Context) {
        val binding = ViewProgressBinding.inflate(LayoutInflater.from(context))
        mDialog = MaterialAlertDialogBuilder(context).setView(binding.root).create().apply {
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            show()
        }
    }

    private fun getToastView(): TextView {
        if (mToastView == null) {
            mToastView = LayoutInflater.from(App.get()).inflate(R.layout.view_toast, null) as TextView
        }
        return mToastView!!
    }

    private fun makeText(message: String?, center: Boolean) {
        if (TextUtils.isEmpty(message)) return
        if (mHandler == null) mHandler = Handler(Looper.getMainLooper())
        mHandler!!.post {
            mToast?.cancel()
            mToast = Toast(App.get()).apply {
                val view = getToastView()
                view.text = message
                setView(view)
                duration = Toast.LENGTH_SHORT
                if (center) setGravity(Gravity.CENTER, 0, 0)
                show()
            }
            mHandler!!.removeCallbacksAndMessages(null)
            mHandler!!.postDelayed({
                mToast?.cancel()
            }, TOAST_DURATION)
        }
    }
}
