package com.fongmi.android.tv.download;

import com.fongmi.android.tv.bean.Download;

/**
 * 下载状态监听器
 * 用于接收下载任务的状态变化、进度更新等事件
 */
public interface DownloadListener {

    /** 任务状态变化（pending→queued→downloading→merging→completed/failed） */
    void onStatusChanged(Download download, String oldStatus, String newStatus);

    /** 进度更新 */
    void onProgress(Download download, int progress, long speed);

    /** 下载完成 */
    void onCompleted(Download download);

    /** 下载失败 */
    void onFailed(Download download, String error);

    /** 任务进入排队 */
    void onQueued(Download download);
}