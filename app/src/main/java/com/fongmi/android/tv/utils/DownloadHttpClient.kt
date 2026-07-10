package com.fongmi.android.tv.utils

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 下载专用 OkHttpClient 工厂
 *
 * 所有下载器共享同一个 OkHttpClient 实例，复用连接池和线程池。
 * 独立于 catvod 的 OkHttp.client()，绕过自定义 DNS 和拦截器。
 */
object DownloadHttpClient {

    @Volatile
    private var instance: OkHttpClient? = null

    @JvmStatic
    fun get(): OkHttpClient {
        if (instance == null) {
            synchronized(this) {
                if (instance == null) {
                    instance = OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(60, TimeUnit.SECONDS)
                        .writeTimeout(60, TimeUnit.SECONDS)
                        .retryOnConnectionFailure(true)
                        .followRedirects(true)
                        .build()
                }
            }
        }
        return instance!!
    }

    /**
     * 快速 HEAD 探测用 client，超时较短
     */
    @Volatile
    private var headInstance: OkHttpClient? = null

    @JvmStatic
    fun head(): OkHttpClient {
        if (headInstance == null) {
            synchronized(this) {
                if (headInstance == null) {
                    headInstance = get().newBuilder()
                        .connectTimeout(5, TimeUnit.SECONDS)
                        .readTimeout(5, TimeUnit.SECONDS)
                        .build()
                }
            }
        }
        return headInstance!!
    }
}
