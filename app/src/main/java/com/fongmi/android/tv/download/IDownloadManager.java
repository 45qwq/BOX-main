package com.fongmi.android.tv.download;

import com.fongmi.android.tv.bean.Download;

/**
 * 统一下载管理器接口
 * Debug 模式由 DownloadService 实现，Release 模式由 DownloadWorker 实现
 * 提供统一的下载任务管理 API
 */
public interface IDownloadManager {

    /** 开始下载任务 */
    void startDownload(Download download);

    /** 取消单个下载任务 */
    void cancelDownload(String downloadId);

    /** 取消所有下载任务 */
    void cancelAll();

    /** 暂停下载任务 */
    void pauseDownload(String downloadId);

    /** 恢复下载任务 */
    void resumeDownload(String downloadId);

    /** 重新下载（重置进度后重试） */
    void retryDownload(String downloadId);

    /** 重新配置并发数和最大重试次数 */
    void reconfigure(int maxConcurrent, int maxRetries);

    /** 获取当前最大并发数 */
    int getMaxConcurrent();

    /** 获取当前活跃任务数 */
    int getActiveCount();

    /** 获取排队任务数 */
    int getQueuedCount();

    /** 检查指定任务是否处于活跃状态（下载中或排队中） */
    boolean isActive(String downloadId);

    /** 获取所有活跃任务 ID */
    java.util.List<String> getActiveDownloadIds();

    /** 注册下载状态监听器 */
    void addListener(DownloadListener listener);

    /** 移除下载状态监听器 */
    void removeListener(DownloadListener listener);
}