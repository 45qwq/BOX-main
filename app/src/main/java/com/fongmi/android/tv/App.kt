package com.fongmi.android.tv

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.core.os.HandlerCompat
import com.fongmi.android.tv.ui.activity.CrashActivity
import com.fongmi.android.tv.utils.AutoSyncManager
import com.fongmi.android.tv.utils.CacheCleaner
import com.fongmi.android.tv.utils.Notify
import com.fongmi.android.tv.event.EventIndex
import com.fongmi.android.tv.utils.UpdateInstaller
import com.fongmi.hook.Hook
import com.github.catvod.Init
import com.github.catvod.bean.Doh
import com.github.catvod.net.OkHttp
import com.github.catvod.utils.Logger
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import cat.ereza.customactivityoncrash.config.CaocConfig
import dagger.hilt.android.HiltAndroidApp
import org.greenrobot.eventbus.EventBus
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class App : Application() {

    val executor: ExecutorService
    val handler: Handler
    val gson: Gson
    val time: Long
    var hook: Hook? = null
    var appJustLaunched: Boolean = false
        private set

    private var activity: Activity? = null
    private val cleanTask: Runnable
    private val syncTask: Runnable

    companion object {
        @JvmStatic
        lateinit var instance: App
            private set

        @JvmStatic
        fun get() = instance

        @JvmStatic
        fun gson() = get().gson

        @JvmStatic
        fun time() = get().time

        @JvmStatic
        fun activity() = get().activity

        @JvmStatic
        fun isAppJustLaunched() = get().appJustLaunched

        @JvmStatic
        fun setAppLaunched() {
            get().appJustLaunched = false
        }

        @JvmStatic
        fun execute(runnable: Runnable) {
            get().executor.execute(runnable)
        }

        @JvmStatic
        fun post(runnable: Runnable) {
            get().handler.post(runnable)
        }

        @JvmStatic
        fun post(runnable: Runnable, delayMillis: Long) {
            get().handler.removeCallbacks(runnable)
            if (delayMillis >= 0) get().handler.postDelayed(runnable, delayMillis)
        }

        @JvmStatic
        fun removeCallbacks(runnable: Runnable) {
            get().handler.removeCallbacks(runnable)
        }

        @JvmStatic
        fun removeCallbacks(vararg runnable: Runnable) {
            for (r in runnable) get().handler.removeCallbacks(r)
        }
    }

    init {
        instance = this
        val cpuCores = Runtime.getRuntime().availableProcessors()
        val corePoolSize = maxOf(2, cpuCores)
        val maxPoolSize = corePoolSize * 2
        executor = ThreadPoolExecutor(
            corePoolSize,
            maxPoolSize,
            60L, TimeUnit.SECONDS,
            LinkedBlockingQueue<Runnable>()
        )
        handler = HandlerCompat.createAsync(Looper.getMainLooper())
        time = System.currentTimeMillis()
        gson = GsonBuilder()
            .disableHtmlEscaping()
            .create()
        cleanTask = Runnable { checkCacheClean() }
        syncTask = Runnable { doAutoSync() }
        appJustLaunched = true
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        Init.set(base)
    }

    override fun onCreate() {
        super.onCreate()
        OkHttp.get().setProxy(Setting.getProxy())
        OkHttp.get().setDoh(Doh.objectFrom(Setting.getDoh()))
        EventBus.builder().addIndex(EventIndex()).installDefaultEventBus()
        CaocConfig.Builder.create()
            .backgroundMode(CaocConfig.BACKGROUND_MODE_SILENT)
            .errorActivity(CrashActivity::class.java)
            .apply()
        Notify.createChannel()

        initCacheCleaner()

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(@NonNull activity: Activity, @Nullable savedInstanceState: Bundle?) {
                if (activity != activity()) setActivity(activity)
            }

            override fun onActivityStarted(@NonNull activity: Activity) {
                if (activity != activity()) setActivity(activity)
            }

            override fun onActivityResumed(@NonNull activity: Activity) {
                if (activity != activity()) setActivity(activity)
                checkCacheClean()
                checkPendingInstall()
                checkAutoSync()
                checkAutoUpdate(activity)
            }

            override fun onActivityPaused(@NonNull activity: Activity) {
                if (activity == activity()) setActivity(null)
            }

            override fun onActivityStopped(@NonNull activity: Activity) {
                if (activity == activity()) setActivity(null)
            }

            override fun onActivityDestroyed(@NonNull activity: Activity) {
                if (activity == activity()) setActivity(null)
            }

            override fun onActivitySaveInstanceState(@NonNull activity: Activity, @NonNull outState: Bundle) {
            }
        })
    }

    override fun getPackageManager(): PackageManager {
        return hook ?: baseContext.packageManager
    }

    override fun getPackageName(): String {
        return hook?.packageName ?: baseContext.packageName
    }

    private fun setActivity(activity: Activity?) {
        this.activity = activity
    }

    private fun initCacheCleaner() {
        val cleaner = CacheCleaner.get()
        cleaner.setCacheThreshold(200 * 1024 * 1024)
        post(cleanTask, 30 * 60 * 1000L)
    }

    private fun checkCacheClean() {
        CacheCleaner.get().checkAndClean()
        post(cleanTask, 30 * 60 * 1000L)
    }

    private fun checkPendingInstall() {
        val installer = UpdateInstaller.get()
        if (installer.hasPendingInstall()) {
            val success = installer.autoRetryInstall()
            if (success) {
                Notify.show("正在安装更新...")
            } else {
                Logger.e("App: 自动安装失败")
            }
        }
    }

    private fun checkAutoUpdate(activity: Activity) {
        if (!Setting.getAutoUpdateCheck()) return
        if (!Setting.getUpdate()) return
        post({
            if (!activity.isFinishing && !activity.isDestroyed) {
                Updater.create().auto().release().start(activity)
            }
        }, 2000)
    }

    private fun checkAutoSync() {
        val manager = AutoSyncManager.get()
        if (!manager.isAutoSyncEnabled()) {
            return
        }
        execute { manager.performAutoSync() }
        val interval = manager.getSyncInterval()
        post(syncTask, interval * 60 * 1000L)
    }

    private fun doAutoSync() {
        execute {
            val manager = AutoSyncManager.get()
            if (manager.isAutoSyncEnabled()) {
                manager.performAutoSync()
                val interval = manager.getSyncInterval()
                post(syncTask, interval * 60 * 1000L)
            }
        }
    }
}
