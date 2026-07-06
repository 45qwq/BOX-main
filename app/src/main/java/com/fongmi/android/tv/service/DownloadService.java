package com.fongmi.android.tv.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.util.Log;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.bean.Download;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.FluxDownDownloader;
import com.fongmi.android.tv.utils.ThunderDownloader;
import com.github.catvod.utils.Path;

import java.io.File;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;
import java.util.HashMap;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class DownloadService extends Service {

    public static final String ACTION_START = "START_DOWNLOAD";
    public static final String ACTION_STOP = "CANCEL_DOWNLOAD";

    private static final String CHANNEL_ID = "download_channel";
    private static final int NOTIFICATION_ID = 1001;

    private ExecutorService executor;
    private NotificationManager notificationManager;
    private final Map<String, DownloadTask> activeTasks = new ConcurrentHashMap<>();
    private final Queue<String> pendingQueue = new ConcurrentLinkedQueue<>();
    // 独立计数器，避免因 activeTasks 被 handleError 提前移除导致误判 isEmpty()
    private final AtomicInteger activeTaskCount = new AtomicInteger(0);

    public static void startDownload(Download download) {
        Intent intent = new Intent(App.get(), DownloadService.class);
        intent.setAction("START_DOWNLOAD");
        intent.putExtra("download", download.toString());
        App.get().startForegroundService(intent);
    }

    public static void cancelDownload(String downloadId) {
        Intent intent = new Intent(App.get(), DownloadService.class);
        intent.setAction("CANCEL_DOWNLOAD");
        intent.putExtra("download_id", downloadId);
        App.get().startService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (isWorkManagerEnabled()) {
            Log.i("DownloadService", "WorkManager 模式，跳过旧线程池初始化");
            return;
        }
        executor = Executors.newFixedThreadPool(Setting.getDownloadConcurrent());
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        String action = intent.getAction();
        if ("START_DOWNLOAD".equals(action)) {
            String downloadJson = intent.getStringExtra("download");
            Download download = Download.objectFrom(downloadJson);
            startForeground(NOTIFICATION_ID, createNotification("准备下载...", 0));
            startDownloadTask(download);
        } else if ("ACTION_PAUSE_DOWNLOAD".equals(action)) {
            String downloadId = intent.getStringExtra("download_id");
            cancelDownloadTask(downloadId);
            Download download = Download.find(downloadId);
            if (download != null) {
                download.setStatus("paused");
                download.save();
                com.fongmi.android.tv.event.RefreshEvent.download();
            }
        } else if ("ACTION_RESUME_DOWNLOAD".equals(action)) {
            String downloadId = intent.getStringExtra("download_id");
            Download download = Download.find(downloadId);
            if (download != null) {
                download.setStatus("pending");
                download.save();
                com.fongmi.android.tv.event.RefreshEvent.download();
                startForeground(NOTIFICATION_ID, createNotification("继续下载: " + download.getVodName(), download.getProgress()));
                startDownloadTask(download);
            }
        } else if ("ACTION_RETRY_DOWNLOAD".equals(action)) {
            String downloadId = intent.getStringExtra("download_id");
            Download download = Download.find(downloadId);
            if (download != null) {
                File downloadDir = getDownloadDir();
                String fileName = generateFileName(download.getVodName(), download.getUrl());
                File oldFile = new File(downloadDir, fileName);
                if (oldFile.exists()) oldFile.delete();
                download.setStatus("pending");
                download.setProgress(0);
                download.setSpeed(0);
                download.setSegmentInfo(null);
                download.save();
                com.fongmi.android.tv.event.RefreshEvent.download();
                startForeground(NOTIFICATION_ID, createNotification("重新下载: " + download.getVodName(), 0));
                startDownloadTask(download);
            }
        } else if ("CANCEL_DOWNLOAD".equals(action)) {
            String downloadId = intent.getStringExtra("download_id");
            cancelDownloadTask(downloadId);
        } else if ("RECONFIGURE_THREAD_POOL".equals(action)) {
            recreateExecutor();
        }

        return START_STICKY;
    }

    private File getDownloadDir() {
        String customPath = Setting.getDownloadPath();
        if (customPath != null && !customPath.isEmpty()) {
            File dir = new File(customPath);
            if (!dir.exists()) dir.mkdirs();
            return dir;
        }
        return Path.download();
    }

    private void startDownloadTask(Download download) {
        if (isWorkManagerEnabled()) {
            Log.i("DownloadService", "WorkManager 模式，通过 Worker 下载: " + download.getVodName());
            download.setStatus("pending");
            download.save();
            DownloadWorker.enqueue(this, download);
            return;
        }

        if (activeTasks.containsKey(download.getId())) return;

        // 如果当前正在运行的任务数已达到并发数上限，则加入排队队列
        if (activeTasks.size() >= Setting.getDownloadConcurrent()) {
            android.util.Log.i("DownloadService", "排队中: " + download.getVodName() + " (当前活跃: " + activeTasks.size() + "/" + Setting.getDownloadConcurrent() + ")");
            pendingQueue.add(download.getId());
            download.setStatus("queued");
            download.save();
            com.fongmi.android.tv.event.RefreshEvent.download();
            return;
        }

        activeTaskCount.incrementAndGet();
        android.util.Log.i("DownloadService", "开始下载任务: " + download.getVodName() + " (当前活跃: " + activeTasks.size() + "/" + Setting.getDownloadConcurrent() + ")");
        com.fongmi.android.tv.event.RefreshEvent.download();
        DownloadTask task = new DownloadTask(download);
        activeTasks.put(download.getId(), task);
        executor.execute(task);
    }

    /**
     * 从排队队列中取出下一个任务并执行
     */
    private void processNextInQueue() {
        String nextId = pendingQueue.poll();
        if (nextId == null) return;

        // 防止重复：如果该任务已在活跃列表中，跳过（可能被 handleError 中的旧任务残留）
        if (activeTasks.containsKey(nextId)) {
            android.util.Log.w("DownloadService", "队列任务已在活跃列表中，跳过: " + nextId);
            return;
        }

        Download download = Download.find(nextId);
        if (download == null) {
            // 数据库记录可能已被删除，继续尝试下一个
            processNextInQueue();
            return;
        }

        activeTaskCount.incrementAndGet();
        android.util.Log.i("DownloadService", "从队列取出并执行: " + download.getVodName() + " (当前活跃: " + activeTasks.size() + "/" + Setting.getDownloadConcurrent() + ")");
        DownloadTask task = new DownloadTask(download);
        activeTasks.put(download.getId(), task);
        executor.execute(task);
    }

    private void cancelDownloadTask(String downloadId) {
        if (downloadId == null) return;

        if (isWorkManagerEnabled()) {
            Log.i("DownloadService", "WorkManager 模式，通过 Worker 取消: " + downloadId);
            DownloadWorker.cancel(this, downloadId);
            return;
        }

        // 检查是否在排队队列中
        if (pendingQueue.remove(downloadId)) {
            android.util.Log.i("DownloadService", "取消排队任务: " + downloadId);
            Download download = Download.find(downloadId);
            if (download != null) {
                download.setStatus("cancelled");
                download.save();
                com.fongmi.android.tv.event.RefreshEvent.download();
            }
            return;
        }

        DownloadTask task = activeTasks.get(downloadId);
        if (task != null) {
            task.cancel();
            activeTasks.remove(downloadId);
            // 删除不完整的文件
            if (task.outputFile != null && task.outputFile.exists()) {
                task.outputFile.delete();
            }
            // 处理队列中的下一个
            processNextInQueue();
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cancelAll();
        if (executor != null) executor.shutdownNow();
        // 强制重置计数器，确保不会因线程中断导致计数残留
        activeTaskCount.set(0);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "视频下载", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("视频下载进度通知");
            notificationManager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification(String title, int progress) {
        Intent intent = new Intent();
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_action_download)
                .setContentTitle(title)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW);
        if (progress > 0) {
            builder.setProgress(100, progress, false).setContentText(progress + "%");
        } else {
            builder.setProgress(0, 0, true);
        }
        return builder.build();
    }

    private void updateNotification(String title, int progress) {
        notificationManager.notify(NOTIFICATION_ID, createNotification(title, progress));
    }

    private String generateFileName(String vodName, String url) {
        String cleanName = vodName.replaceAll("[\\\\/:*?\"<>|]", "_");
        String extension = ".mp4";
        if (url != null && url.toLowerCase().contains(".m3u8")) {
            extension = ".mp4";
        } else if (url != null && url.contains(".")) {
            String urlExt = url.substring(url.lastIndexOf("."));
            if (urlExt.contains("?")) urlExt = urlExt.substring(0, urlExt.indexOf("?"));
            if (urlExt.matches("\\.(mp4|mkv|avi|mov|wmv|flv|webm|ts)")) {
                extension = urlExt;
            }
        }
        return cleanName + extension;
    }

    private String formatSpeed(long speed) {
        if (speed < 1024) return speed + "B";
        if (speed < 1024 * 1024) return String.format("%.1fKB", speed / 1024.0);
        return String.format("%.1fMB", speed / 1024.0 / 1024.0);
    }

    private boolean isM3U8Url(String url) {
        if (url == null || url.isEmpty()) return false;
        String lowerUrl = url.toLowerCase();
        // 快速路径：URL包含明显的M3U8关键词
        if (lowerUrl.contains(".m3u8") || lowerUrl.contains("m3u8") || lowerUrl.contains("hls") || lowerUrl.contains("playlist")) {
            return true;
        }
        // 慢速路径：发HEAD请求检测Content-Type
        return isM3U8ByContentType(url);
    }

    private final OkHttpClient m3u8DetectClient = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .followRedirects(true)
            .build();

    /**
     * 发送HEAD请求，根据Content-Type判断是否为M3U8流
     */
    private boolean isM3U8ByContentType(String url) {
        try {
            Request request = new Request.Builder().url(url).head().build();
            Response response = m3u8DetectClient.newCall(request).execute();
            String contentType = response.header("Content-Type");
            int code = response.code();
            response.close();
            Log.i("DownloadService", "HEAD检测: url=" + url + " HTTP=" + code + " Content-Type=" + contentType);
            if (contentType != null) {
                String lower = contentType.toLowerCase();
                return lower.contains("mpegurl") || lower.contains("m3u8") || lower.contains("vnd.apple");
            }
        } catch (Exception e) {
            Log.d("DownloadService", "HEAD检测失败(不影响下载): " + e.getMessage() + " url=" + url);
        }
        return false;
    }

    private void handleError(Download download, File outputFile, String error) {
        android.util.Log.e("DownloadService", "下载失败: " + error + " URL: " + download.getUrl() + " vodName: " + download.getVodName() + " header: " + download.getHeader());
        // 只取消下载线程，不移除 activeTasks 和调用 processNextInQueue
        // 这些由 run() 的 finally 块统一处理，避免竞态条件
        DownloadTask task = activeTasks.get(download.getId());
        if (task != null) {
            task.cancel();
        }
        download.setStatus("failed");
        download.save();
        Notify.show("下载失败: " + error);
        com.fongmi.android.tv.event.RefreshEvent.download();
    }

    // 重新初始化线程池（当用户在设置中修改并发数后调用）
    public static void reconfigureThreadPool(Service service) {
        if (service instanceof DownloadService) {
            ((DownloadService) service).recreateExecutor();
        }
    }

    private void recreateExecutor() {
        ExecutorService oldExecutor = executor;
        executor = Executors.newFixedThreadPool(Setting.getDownloadConcurrent());
        // 关闭旧线程池，等待已有任务完成
        if (oldExecutor != null && !oldExecutor.isShutdown()) {
            oldExecutor.shutdown();
        }
        android.util.Log.i("DownloadService", "线程池已重新配置，并发数: " + Setting.getDownloadConcurrent());
    }

    public void cancelAll() {
        if (isWorkManagerEnabled()) {
            DownloadWorker.cancelAll(this);
            return;
        }
        pendingQueue.clear();
        for (String key : new HashMap<>(activeTasks).keySet()) {
            cancelDownloadTask(key);
        }
    }

    /**
     * 过渡期开关：Debug 保留旧逻辑，Release 走 WorkManager
     */
    public static boolean isWorkManagerEnabled() {
        return !BuildConfig.DEBUG;
    }

    private class DownloadTask implements Runnable {
        private final Download download;
        private volatile boolean cancelled = false;
        private final CountDownLatch completionLatch = new CountDownLatch(1);
        File outputFile;
        private FluxDownDownloader fluxDownDownloader;
        private ThunderDownloader thunderDownloader;
        private long lastRefreshTime = 0;
        private long lastGoodSpeed = 0;

        public DownloadTask(Download download) {
            this.download = download;
        }

        public void cancel() {
            cancelled = true;
            if (fluxDownDownloader != null) fluxDownDownloader.cancel();
            if (thunderDownloader != null) thunderDownloader.shutdown();
            completionLatch.countDown(); // 让 run() 退出等待
        }

        private void notifyRefreshThrottled() {
            long now = System.currentTimeMillis();
            if (now - lastRefreshTime > 800) {
                lastRefreshTime = now;
                com.fongmi.android.tv.event.RefreshEvent.download();
            }
        }

        private Map<String, String> parseHeaders() {
            Map<String, String> headers = new HashMap<>();
            if (download.getHeader() != null && !download.getHeader().isEmpty()) {
                String[] headerLines = download.getHeader().split("\n");
                for (String header : headerLines) {
                    String[] parts = header.split(":", 2);
                    if (parts.length == 2) {
                        headers.put(parts[0].trim(), parts[1].trim());
                    }
                }
            }
            return headers;
        }

        @Override
        public void run() {
            try {
                android.util.Log.i("DownloadService", "开始下载: " + download.getVodName() + " URL: " + download.getUrl());

                File downloadDir = getDownloadDir();
                android.util.Log.i("DownloadService", "下载路径: " + downloadDir.getAbsolutePath() + " 可写: " + downloadDir.canWrite());
                if (!downloadDir.exists()) downloadDir.mkdirs();

                String fileName = generateFileName(download.getVodName(), download.getUrl());
                outputFile = new File(downloadDir, fileName);

                boolean isM3U8 = isM3U8Url(download.getUrl());
                boolean hasM3U8Header = download.getHeader() != null && download.getHeader().contains("M3U8-Content");

                if (isM3U8 || hasM3U8Header) {
                    downloadWithM3U8();
                } else {
                    downloadWithThunder();
                }

                // 等待下载完成，防止 service 提前停止
                if (!cancelled) {
                    completionLatch.await();
                }

            } catch (Exception e) {
                if (!cancelled) {
                    e.printStackTrace();
                    handleError(download, outputFile, e.getMessage());
                }
            } finally {
                activeTasks.remove(download.getId());
                activeTaskCount.decrementAndGet(); // 先减计数再决定是否停止服务
                processNextInQueue(); // 处理排队队列中的下一个任务
                if (activeTaskCount.get() == 0) {
                    stopForeground(true);
                    stopSelf();
                }
            }
        }

        private void downloadWithThunder() {
            android.util.Log.i("DownloadService", "迅雷下载: " + download.getUrl());

            String fileName = generateFileName(download.getVodName(), download.getUrl());
            File downloadDir = getDownloadDir();
            thunderDownloader = new ThunderDownloader();

            thunderDownloader.download(download.getUrl(), downloadDir, fileName, parseHeaders(),
                new ThunderDownloader.Callback() {
                    @Override
                    public void onStart(long totalSize) {
                        updateNotification(download.getVodName() + " - 下载中", 0);
                        App.execute(() -> {
                            download.setStatus("downloading");
                            download.save();
                        });
                    }

                    @Override
                    public void onProgress(int progress, long speed, long downloaded, long total) {
                        // 速度平滑：非零速度保留，零速度时用上次有效值
                        if (speed > 0) {
                            lastGoodSpeed = speed;
                        } else if (lastGoodSpeed > 0 && progress >= 0 && progress < 100) {
                            speed = lastGoodSpeed;
                        }
                        if (progress >= 0) download.setProgress(progress);
                        download.setSpeed(speed);
                        download.setStatus("downloading");
                        App.execute(() -> download.save());

                        String info = download.getVodName() + " - " + formatSpeed(speed) + "/s";
                        if (progress >= 0) info += " " + progress + "%";
                        updateNotification(info, progress >= 0 ? progress : 0);
                        notifyRefreshThrottled();
                    }

                    @Override
                    public void onSuccess(File outputFile) {
                        // 验证文件完整性：检查文件是否存在且不为空
                        if (outputFile == null || !outputFile.exists() || outputFile.length() == 0) {
                            handleError(download, outputFile, "下载文件不存在或为空");
                            completionLatch.countDown();
                            return;
                        }
                        android.util.Log.i("DownloadService", "下载完成: " + outputFile.getAbsolutePath() + " 大小: " + outputFile.length());
                        download.setFilePath(outputFile.getAbsolutePath());
                        download.setProgress(100);
                        App.execute(() -> {
                            download.setStatus("completed");
                            download.save();
                        });
                        updateNotification(download.getVodName() + " - 下载完成", 100);
                        com.fongmi.android.tv.event.RefreshEvent.download();
                        Notify.show("下载完成: " + download.getVodName());
                        completionLatch.countDown();
                    }

                    @Override
                    public void onError(String error) {
                        handleError(download, outputFile, error);
                        completionLatch.countDown();
                    }
                });
        }

        private void downloadWithM3U8() {
            android.util.Log.i("DownloadService", "M3U8下载(FluxDown): " + download.getVodName() + " URL: " + download.getUrl() + " header: " + download.getHeader());

            fluxDownDownloader = new FluxDownDownloader(
                download.getUrl(),
                parseHeaders(),
                outputFile.getParentFile(),
                outputFile.getName(),
                new FluxDownDownloader.Callback() {
                    @Override
                    public void onStart() {
                        updateNotification(download.getVodName() + " - 开始下载", 0);
                        App.execute(() -> {
                            download.setStatus("downloading");
                            download.save();
                        });
                        com.fongmi.android.tv.event.RefreshEvent.download();
                    }

                    @Override
                    public void onProgress(int progress, int downloadedSegments, int totalSegments, long speed) {
                        // 速度平滑：非零速度保留，零速度时用上次有效值
                        if (speed > 0) {
                            lastGoodSpeed = speed;
                        } else if (lastGoodSpeed > 0 && progress < 100) {
                            speed = lastGoodSpeed;
                        }
                        download.setProgress(progress);
                        download.setSpeed(speed);
                        download.setStatus("downloading");
                        download.setSegmentInfo(downloadedSegments + "/" + totalSegments);
                        App.execute(() -> download.save());
                        updateNotification(download.getVodName() + " " + formatSpeed(speed) + "/s", progress);
                        notifyRefreshThrottled();
                    }

                    @Override
                    public void onMerging() {
                        App.execute(() -> {
                            download.setStatus("merging");
                            download.save();
                        });
                        updateNotification(download.getVodName() + " - 视频合并中", download.getProgress());
                        com.fongmi.android.tv.event.RefreshEvent.download();
                    }

                    @Override
                    public void onSuccess(File outputFile) {
                        // 验证文件完整性：检查合并后的文件是否存在且不为空
                        if (outputFile == null || !outputFile.exists() || outputFile.length() == 0) {
                            handleError(download, outputFile, "合并后的文件不存在或为空");
                            completionLatch.countDown();
                            return;
                        }
                        download.setFilePath(outputFile.getAbsolutePath());
                        download.setProgress(100);
                        App.execute(() -> {
                            download.setStatus("completed");
                            download.save();
                        });
                        updateNotification(download.getVodName() + " 下载完成", 100);
                        com.fongmi.android.tv.event.RefreshEvent.download();
                        Notify.show("下载完成: " + download.getVodName());
                        completionLatch.countDown();
                    }

                    @Override
                    public void onError(String error) {
                        handleError(download, outputFile, error);
                        completionLatch.countDown();
                    }
                }
            );
            fluxDownDownloader.start();
        }
    }

    private long getVideoDuration(File videoFile) {
        if (!videoFile.exists() || videoFile.length() == 0) return 0;
        MediaMetadataRetriever retriever = null;
        try {
            retriever = new MediaMetadataRetriever();
            retriever.setDataSource(videoFile.getAbsolutePath());
            String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (durationStr != null && !durationStr.isEmpty()) {
                return Long.parseLong(durationStr) / 1000;
            }
        } catch (Exception e) {
            Log.e("DownloadService", "获取视频时长失败", e);
        } finally {
            if (retriever != null) {
                try { retriever.release(); } catch (Exception e) { }
            }
        }
        return 0;
    }
}