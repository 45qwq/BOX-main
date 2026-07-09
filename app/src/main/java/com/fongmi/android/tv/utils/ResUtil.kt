package com.fongmi.android.tv.utils

import android.content.Context
import android.content.res.Configuration
import android.content.res.TypedArray
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Display
import android.view.MotionEvent
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.annotation.AnimRes
import androidx.annotation.ArrayRes
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.fongmi.android.tv.App

object ResUtil {

    @JvmStatic
    fun getDisplayMetrics(): DisplayMetrics {
        return getDisplayMetrics(App.get())
    }

    @JvmStatic
    fun getDisplayMetrics(context: Context): DisplayMetrics {
        return context.resources.displayMetrics
    }

    @JvmStatic
    fun getWindowManager(context: Context): WindowManager {
        return context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    @JvmStatic
    fun getScreenWidth(): Int {
        return getScreenWidth(App.get())
    }

    @JvmStatic
    fun getScreenWidth(context: Context): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val rect = getWindowManager(context).currentWindowMetrics.bounds
            if (isLand(context)) Math.max(rect.width(), rect.height()) else Math.min(rect.width(), rect.height())
        } else {
            getDisplayMetrics(context).widthPixels
        }
    }

    @JvmStatic
    fun getScreenHeight(): Int {
        return getScreenHeight(App.get())
    }

    @JvmStatic
    fun getScreenHeight(context: Context): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val rect = getWindowManager(context).currentWindowMetrics.bounds
            if (isLand(context)) Math.min(rect.width(), rect.height()) else Math.max(rect.width(), rect.height())
        } else {
            getDisplayMetrics(context).heightPixels
        }
    }

    @JvmStatic
    fun isEdge(context: Context, e: MotionEvent, edge: Int): Boolean {
        return e.rawX < edge || e.rawX > getScreenWidth(context) - edge || e.rawY < edge || e.rawY > getScreenHeight(context) - edge
    }

    @JvmStatic
    fun isLand(context: Context): Boolean {
        return context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    @JvmStatic
    fun isPad(): Boolean {
        return App.get().resources.configuration.smallestScreenWidthDp >= 600
    }

    @JvmStatic
    fun sp2px(sp: Int): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp.toFloat(), getDisplayMetrics()).toInt()
    }

    @JvmStatic
    fun dp2px(dp: Int): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), getDisplayMetrics()).toInt()
    }

    @JvmStatic
    fun getDrawable(resId: String): Int {
        return App.get().resources.getIdentifier(resId, "drawable", App.get().packageName)
    }

    @JvmStatic
    fun getString(@StringRes resId: Int): String {
        return App.get().resources.getString(resId)
    }

    @JvmStatic
    fun getString(@StringRes resId: Int, vararg formatArgs: Any): String {
        return App.get().resources.getString(resId, *formatArgs)
    }

    @JvmStatic
    fun getStringArray(@ArrayRes resId: Int): Array<String> {
        return App.get().resources.getStringArray(resId)
    }

    @JvmStatic
    fun getTypedArray(@ArrayRes resId: Int): TypedArray {
        return App.get().resources.obtainTypedArray(resId)
    }

    @JvmStatic
    fun getDrawable(@DrawableRes resId: Int): Drawable? {
        return ContextCompat.getDrawable(App.get(), resId)
    }

    @JvmStatic
    fun getColor(@ColorRes resId: Int): Int {
        return ContextCompat.getColor(App.get(), resId)
    }

    @JvmStatic
    fun getAnim(@AnimRes resId: Int): Animation {
        return AnimationUtils.loadAnimation(App.get(), resId)
    }

    @JvmStatic
    fun getDisplay(context: Context): Display? {
        return ContextCompat.getDisplayOrDefault(context)
    }

    @JvmStatic
    fun getTextWidth(content: String, size: Int): Int {
        val paint = Paint()
        paint.textSize = sp2px(size).toFloat()
        return paint.measureText(content).toInt()
    }
}
