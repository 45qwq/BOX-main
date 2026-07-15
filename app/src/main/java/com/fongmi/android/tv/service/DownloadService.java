package com.fongmi.android.tv.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import com.github.catvod.utils.Logger;

import androidx.core.app.NotificationCompat;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.bean.Download;
import com.fongmi.android.tv.download.DownloadListener;
import com.fongmi.android.tv.download.DownloadManagerImpl;
import com.fongmi.android.tv.event.RefreshEvent;

/**
 * 下载前台服务
 *
 * Debug 模式：使用 DownloadManagerImpl 管理下载队列和线程池
 * Release 模式：委托给 DownloadWorker（WorkManager）
 *
 * 职责：
 * - 前台服务生命周期管理
 * - 通知管理
 * - 操作委托转发
 */
public class DownloadService extends Service {

    public static final String ACTION_START = "START_DOWNLOAD";
    public static final String ACTION_STOP = "CANCEL_DOWNLOAD";
    public static final String ACTION_PAUSE = "ACTION_PAUSE_DOWNLOAD";
    public static final String ACTION_RESUME = "ACTION_RESUME_DOWNLOAD";
    public static final String ACTION_RETRY = "ACTION_RETRY_DOWNLOAD";
    public static final String ACTION_RECONFIGURE = "RECONFIGURE_THREAD_POOL";

    private static final String CHANNEL_ID = "download_channel";
    private static final int NOTIFICATION_ID = 1001;

    private NotificationManager notificationManager;
    private DownloadManagerImpl downloadManager;

    private final DownloadListener downloadListener = new DownloadListener() {
        @Override
        public void onStatusChanged(Download download, String oldStatus, String newStatus) {
        }
        @Override
        public void onProgress(Download download, int progress, long speed) {
            String info = download.getVodName() + " - " + formatSpeed(speed) + "/s";
            if (progress >= 0) info += " " + progress + "%";
            updateNotificationInternal(info, progress >= 0 ? progress : 0);
        }
        @Override
        public void onCompleted(Download download) {
            updateNotificationInternal(download.getVodName() + " - 下载完成", 100);
        }
        @Override
        public void onFailed(Download download, String error) {
            updateNotificationInternal(download.getVodName() + " - 下载失败", download.getProgress());
        }
        @Override
        public void onQueued(Download download) {
            updateNotificationInternal(download.getVodName() + " - 排队中", 0);
        }
    };

    private static String formatSpeed(long speed) {
        if (speed < 1024) return speed + "B";
        if (speed < 1024 * 1024) return String.format("%.1fKB", speed / 1024.0);
        return String.format("%.1fMB", speed / 1024.0 / 1024.0);
    }

    public static void startDownload(Download download) {
        Intent intent = new Intent(App.get(), DownloadService.class);
        intent.setAction(ACTION_START);
        intent.putExtra("download", download.toString());
        App.get().startForegroundService(intent);
    }

    public static void cancelDownload(String downloadId) {
        Intent intent = new Intent(App.get(), DownloadService.class);
        intent.setAction(ACTION_STOP);
        intent.putExtra("download_id", downloadId);
        App.get().startService(intent);
    }

    public static void pauseDownload(String downloadId) {
        Intent intent = new Intent(App.get(), DownloadService.class);
        intent.setAction(ACTION_PAUSE);
        intent.putExtra("download_id", downloadId);
        App.get().startService(intent);
    }

    public static void resumeDownload(String downloadId) {
        Intent intent = new Intent(App.get(), DownloadService.class);
        intent.setAction(ACTION_RESUME);
        intent.putExtra("download_id", downloadId);
        App.get().startService(intent);
    }

    public static void retryDownload(String downloadId) {
        Intent intent = new Intent(App.get(), DownloadService.class);
        intent.setAction(ACTION_RETRY);
        intent.putExtra("download_id", downloadId);
        App.get().startService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (isWorkManagerEnabled()) {
            Logger.i("DownloadService: WorkManager 模式，跳过旧线程池初始化");
            return;
        }
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createNotificationChannel();
        downloadManager = DownloadManagerImpl.get();
        downloadManager.addListener(downloadListener);
        Logger.i("DownloadService: 初始化完成，并发数: " + downloadManager.getMaxConcurrent());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        String action = intent.getAction();
        if (isWorkManagerEnabled()) {
            handleWorkManagerAction(action, intent);
            return START_NOT_STICKY;
        }

        switch (action) {
            case ACTION_START:
                String downloadJson = intent.getStringExtra("download");
                Download download = Download.objectFrom(downloadJson);
                startForeground(NOTIFICATION_ID, createNotification("准备下载...", 0));
                downloadManager.startDownload(download);
                break;
            case ACTION_STOP:
                String cancelId = intent.getStringExtra("download_id");
                downloadManager.cancelDownload(cancelId);
                break;
            case ACTION_PAUSE:
                String pauseId = intent.getStringExtra("download_id");
                downloadManager.pauseDownload(pauseId);
                break;
            case ACTION_RESUME:
                String resumeId = intent.getStringExtra("download_id");
                downloadManager.resumeDownload(resumeId);
                break;
            case ACTION_RETRY:
                String retryId = intent.getStringExtra("download_id");
                downloadManager.retryDownload(retryId);
                break;
            case ACTION_RECONFIGURE:
                downloadManager.reconfigure(Setting.getDownloadConcurrent(), 3);
                break;
        }

        return START_STICKY;
    }

    /**
     * WorkManager 模式下的操作处理
     */
    private void handleWorkManagerAction(String action, Intent intent) {
        Logger.i("DownloadService: WorkManager 模式，操作: " + action);
        switch (action) {
            case ACTION_START:
                String downloadJson = intent.getStringExtra("download");
                Download download = Download.objectFrom(downloadJson);
                download.setStatus(com.fongmi.android.tv.download.DownloadStateMachine.Status.PENDING.name());
                download.save();
                DownloadWorker.enqueue(this, download);
                break;
            case ACTION_STOP:
                String cancelId = intent.getStringExtra("download_id");
                DownloadWorker.cancel(this, cancelId);
                break;
            case ACTION_RETRY:
                String retryId = intent.getStringExtra("download_id");
                Download retryDownload = Download.find(retryId);
                if (retryDownload != null) {
                    retryDownload.setStatus(com.fongmi.android.tv.download.DownloadStateMachine.Status.PENDING.name());
                    retryDownload.setProgress(0);
                    retryDownload.setSpeed(0);
                    retryDownload.save();
                    RefreshEvent.download();
                    DownloadWorker.enqueue(this, retryDownload);
                }
                break;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (downloadManager != null) {
            downloadManager.removeListener(downloadListener);
            downloadManager.shutdown();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "视频下载", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("视频下载进度通知");
            notificationManager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification(String title, int progress) {
        Intent intent = new Intent();
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_action_download)
                .setContentTitle(title)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW);
        if (progress > 0) {
            builder.setProgress(100, progress, false)
                    .setContentText(progress + "%");
        } else {
            builder.setProgress(0, 0, true);
        }
        return builder.build();
    }

    private void updateNotificationInternal(String title, int progress) {
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, createNotification(title, progress));
        }
    }

    /**
     * 过渡期开关：Debug 保留旧逻辑，Release 走 WorkManager
     */
    public static boolean isWorkManagerEnabled() {
        return !BuildConfig.DEBUG;
    }

    /**
     * 重新配置线程池（设置中修改并发数后调用）
     */
    public static void reconfigureThreadPool(Service service) {
        if (service instanceof DownloadService) {
            DownloadService ds = (DownloadService) service;
            if (ds.downloadManager != null) {
                ds.downloadManager.reconfigure(
                        Setting.getDownloadConcurrent(), 3);
            }
        }
    }
}