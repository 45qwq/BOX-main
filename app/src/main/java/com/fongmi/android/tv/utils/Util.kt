package com.fongmi.android.tv.utils

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Parcelable
import android.provider.Settings
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import com.fongmi.android.tv.App
import com.fongmi.android.tv.BuildConfig
import com.fongmi.android.tv.R
import com.github.catvod.utils.Logger
import com.github.catvod.utils.Shell
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Formatter
import java.util.regex.Pattern

object Util {

    @JvmStatic
    fun toggleFullscreen(activity: Activity, fullscreen: Boolean) {
        if (fullscreen) hideSystemUI(activity) else showSystemUI(activity)
    }

    @JvmStatic
    fun showSystemUI(activity: Activity) {
        @Suppress("DEPRECATION")
        activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }

    @JvmStatic
    fun hideSystemUI(activity: Activity) {
        hideSystemUI(activity.window)
    }

    @JvmStatic
    fun hideSystemUI(window: Window) {
        @Suppress("DEPRECATION")
        val flags = (View.SYSTEM_UI_FLAG_LOW_PROFILE
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
        window.decorView.systemUiVisibility = flags
    }

    @JvmStatic
    fun showKeyboard(view: View) {
        if (!view.requestFocus()) return
        val imm = App.get().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        if (imm != null) view.postDelayed({ imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT) }, 250)
    }

    @JvmStatic
    fun hideKeyboard(view: View) {
        val imm = App.get().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val windowToken = view.windowToken
        if (imm == null || windowToken == null) return
        imm.hideSoftInputFromWindow(windowToken, 0)
    }

    @Suppress("DEPRECATION")
    @JvmStatic
    fun getBrightness(activity: Activity): Float {
        try {
            val value = activity.window.attributes.screenBrightness
            if (WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL >= value && value >= WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF) return value
            return Settings.System.getFloat(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 128
        } catch (e: Exception) {
            return 0.5f
        }
    }

    @JvmStatic
    fun getClipText(): CharSequence {
        val manager = App.get().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clipData = manager?.primaryClip
        if (clipData == null || clipData.itemCount == 0) return ""
        return clipData.getItemAt(0).text ?: ""
    }

    @JvmStatic
    fun copy(text: String) {
        try {
            val manager = App.get().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            manager?.setPrimaryClip(ClipData.newPlainText("", text))
            Notify.show(R.string.copied)
        } catch (e: Exception) {
            Logger.e("Error", e)
        }
    }

    @JvmStatic
    fun getDigit(text: String): Int {
        try {
            if (text.startsWith("上") || text.startsWith("下")) return -1
            return text.replace(Regex("(?i)(mp4|H264|H265|720p|1080p|2160p|4K)"), "").replace(Regex("\\D+"), "").toInt()
        } catch (e: Exception) {
            return -1
        }
    }

    @JvmStatic
    fun getAndroidId(): String {
        try {
            val id = Settings.Secure.getString(App.get().contentResolver, Settings.Secure.ANDROID_ID)
            if (id.isNullOrEmpty()) throw NullPointerException()
            return id
        } catch (e: Exception) {
            return "0000000000000000"
        }
    }

    @JvmStatic
    fun getSerial(): String {
        return Shell.exec("getprop ro.serialno").replace("\n", "")
    }

    @JvmStatic
    fun getMac(name: String): String {
        try {
            val sb = StringBuilder()
            val nif = NetworkInterface.getByName(name)
            val mac = nif.hardwareAddress
            if (mac == null) return ""
            if (mac.size == 6 && mac[0] == 0x02.toByte() && mac[1] == 0x00.toByte() && mac[2] == 0x00.toByte() && mac[3] == 0x00.toByte() && mac[4] == 0x00.toByte() && mac[5] == 0x00.toByte()) return ""
            for (b in mac) sb.append(String.format("%02X:", b))
            return substring(sb.toString()) ?: ""
        } catch (e: Exception) {
            return ""
        }
    }

    @JvmStatic
    fun getDeviceName(): String {
        val model = Build.MODEL
        val manufacturer = Build.MANUFACTURER
        return if (model.startsWith(manufacturer)) model else "$manufacturer $model"
    }

    @JvmStatic
    fun substring(text: String): String {
        return substring(text, 1) ?: ""
    }

    @JvmStatic
    fun substring(text: String?, num: Int): String? {
        if (text != null && text.length > num) return text.substring(0, text.length - num)
        return text
    }

    @JvmStatic
    fun format(src: String, formats: List<SimpleDateFormat>): Long {
        for (format in formats) try {
            return format.parse(src)?.time ?: 0
        } catch (e: Exception) {
            Logger.w("Util format", e)
        }
        return 0
    }

    @JvmStatic
    fun isMobile(): Boolean {
        return "mobile" == BuildConfig.FLAVOR_mode
    }

    @JvmStatic
    fun format(builder: StringBuilder, formatter: Formatter, timeMs: Long): String {
        return try {
            androidx.media3.common.util.Util.getStringForTime(builder, formatter, timeMs)
        } catch (e: Exception) {
            ""
        }
    }

    @JvmStatic
    fun getChooser(intent: Intent): Intent {
        val components = ArrayList<ComponentName>()
        for (resolveInfo in App.get().packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)) {
            val pkgName = resolveInfo.activityInfo.packageName
            if (pkgName == App.get().packageName) {
                components.add(ComponentName(pkgName, resolveInfo.activityInfo.name))
            }
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Intent.createChooser(intent, null).putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, components.toTypedArray<Parcelable>())
        } else {
            Intent.createChooser(intent, null)
        }
    }

    @JvmStatic
    fun containOrMatch(text: String, pattern: String): Boolean {
        return text.contains(pattern) || Pattern.compile(pattern).matcher(text).find()
    }
}
