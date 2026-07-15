package com.fongmi.android.tv.download;

import android.content.Context;
import com.github.catvod.utils.Logger;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.bean.Download;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.utils.HttpDownloader;
import com.fongmi.android.tv.utils.FluxDownDownloader;
import com.fongmi.android.tv.utils.Notify;
import com.github.catvod.utils.Path;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import com.fongmi.android.tv.download.DownloadStateMachine.Status;
import com.fongmi.android.tv.download.DownloadStateMachine.ErrorType;

/**
 * 下载管理器实现
 * 统一管理下载队列、并发控制、任务状态机和重试策略
 */
public class DownloadManagerImpl {

    private static final int DEFAULT_MAX_CONCURRENT = 3;
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final int MIN_CONCURRENT = 1;
    private static final int MAX_CONCURRENT = 5;

    private static volatile DownloadManagerImpl instance;

    private ExecutorService executor;
    private int maxConcurrent;
    private int maxRetries;

    private final Map<String, DownloadTask> activeTasks = new ConcurrentHashMap<>();
    private final Queue<String> pendingQueue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger activeTaskCount = new AtomicInteger(0);

    private final List<DownloadListener> listeners = new ArrayList<>();

    public static DownloadManagerImpl get() {
        if (instance == null) {
            synchronized (DownloadManagerImpl.class) {
                if (instance == null) {
                    instance = new DownloadManagerImpl();
                }
            }
        }
        return instance;
    }

    private DownloadManagerImpl() {
        this.maxConcurrent = clampConcurrent(Setting.getDownloadConcurrent());
        this.maxRetries = DEFAULT_MAX_RETRIES;
        this.executor = Executors.newFixedThreadPool(this.maxConcurrent);
        Logger.i("DownloadManagerImpl: 初始化，并发数: " + this.maxConcurrent + "，最大重试: " + this.maxRetries);
    }

    // ==================== 监听器管理 ====================

    public void addListener(DownloadListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(DownloadListener listener) {
        listeners.remove(listener);
    }

    private void notifyStatusChanged(Download download, String oldStatus, String newStatus) {
        for (DownloadListener l : listeners) {
            try {
                l.onStatusChanged(download, oldStatus, newStatus);
            } catch (Exception e) {
                Logger.w("DownloadManagerImpl: 监听器异常", e);
            }
        }
    }

    private void notifyProgress(Download download, int progress, long speed) {
        for (DownloadListener l : listeners) {
            try {
                l.onProgress(download, progress, speed);
            } catch (Exception e) {
                Logger.w("DownloadManagerImpl: 监听器异常", e);
            }
        }
    }

    private void notifyCompleted(Download download) {
        for (DownloadListener l : listeners) {
            try {
                l.onCompleted(download);
            } catch (Exception e) {
                Logger.w("DownloadManagerImpl: 监听器异常", e);
            }
        }
    }

    private void notifyFailed(Download download, String error) {
        for (DownloadListener l : listeners) {
            try {
                l.onFailed(download, error);
            } catch (Exception e) {
                Logger.w("DownloadManagerImpl: 监听器异常", e);
            }
        }
    }

    // ==================== 核心操作 ====================

    /**
     * 开始下载任务
     * 如果当前活跃任务数已达到并发上限，则加入排队队列
     */
    public void startDownload(Download download) {
        if (download == null || download.getId() == null) return;

        // 如果已在活跃列表中，跳过
        if (activeTasks.containsKey(download.getId())) {
            Logger.i("DownloadManagerImpl: 任务已在下载中: " + download.getVodName());
            return;
        }

        // 检查并发限制
        if (activeTaskCount.get() >= maxConcurrent) {
            enqueueDownload(download);
            return;
        }

        executeDownload(download);
    }

    /**
     * 将任务加入排队队列
     */
    private void enqueueDownload(Download download) {
        String oldStatus = download.getStatus();
        pendingQueue.add(download.getId());
        download.setStatus(Status.QUEUED.name());
        // 数据库操作必须在后台线程执行
        App.execute(() -> {
            try {
                download.save();
            } catch (Exception e) {
                Logger.e("DownloadManagerImpl: 保存排队状态失败", e);
            }
            App.post(() -> {
                Logger.i("DownloadManagerImpl: 排队中: " + download.getVodName() + " (活跃: " + activeTaskCount.get() + "/" + maxConcurrent + ")");
                notifyStatusChanged(download, oldStatus, Status.QUEUED.name());
                RefreshEvent.download();
                notifyQueued(download);
            });
        });
    }

    private void notifyQueued(Download download) {
        for (DownloadListener l : listeners) {
            try {
                l.onQueued(download);
            } catch (Exception e) {
                Logger.w("DownloadManagerImpl: 监听器异常", e);
            }
        }
    }

    /**
     * 执行下载任务
     */
    private void executeDownload(Download download) {
        activeTaskCount.incrementAndGet();
        Logger.i("DownloadManagerImpl: 开始下载: " + download.getVodName() + " (活跃: " + activeTaskCount.get() + "/" + maxConcurrent + ")");

        String oldStatus = download.getStatus();
        download.setStatus(Status.DOWNLOADING.name());
        // 数据库操作必须在后台线程执行
        App.execute(() -> {
            try {
                download.save();
            } catch (Exception e) {
                Logger.e("DownloadManagerImpl: 保存下载状态失败", e);
            }
            // executor.execute 不需要在主线程执行，直接在这里运行可以减少线程池被关闭的窗口期
            DownloadTask task = new DownloadTask(download);
            activeTasks.put(download.getId(), task);
            try {
                executor.execute(task);
            } catch (java.util.concurrent.RejectedExecutionException e) {
                Logger.e("DownloadManagerImpl: 线程池已关闭，无法执行下载任务: " + download.getVodName(), e);
                activeTasks.remove(download.getId());
                activeTaskCount.decrementAndGet();
                download.setStatus(Status.FAILED.name());
                download.setErrorMsg("下载服务已停止");
                saveDownload(download);
                App.post(() -> {
                    notifyStatusChanged(download, oldStatus, Status.FAILED.name());
                    notifyFailed(download, "下载服务已停止");
                    RefreshEvent.download();
                });
                return;
            }
            App.post(() -> {
                notifyStatusChanged(download, oldStatus, Status.DOWNLOADING.name());
                RefreshEvent.download();
            });
        });
    }

    /**
     * 取消下载任务
     */
    public void cancelDownload(String downloadId) {
        if (downloadId == null) return;

        // 检查是否在排队队列中
        if (pendingQueue.remove(downloadId)) {
            Logger.i("DownloadManagerImpl: 取消排队任务: " + downloadId);
            Download download = Download.find(downloadId);
            if (download != null) {
                String oldStatus = download.getStatus();
                download.setStatus(Status.CANCELLED.name());
                download.save();
                notifyStatusChanged(download, oldStatus, Status.CANCELLED.name());
                RefreshEvent.download();
            }
            return;
        }

        DownloadTask task = activeTasks.get(downloadId);
        if (task != null) {
            task.cancel();
            activeTasks.remove(downloadId);
            // 删除不完整的文件
            task.deleteOutputFile();
            // 处理队列中的下一个
            processNextInQueue();
        }
    }

    /**
     * 取消所有下载任务
     */
    public void cancelAll() {
        pendingQueue.clear();
        for (String key : new ArrayList<>(activeTasks.keySet())) {
            cancelDownload(key);
        }
    }

    /**
     * 暂停下载任务
     */
    public void pauseDownload(String downloadId) {
        if (downloadId == null) return;

        DownloadTask task = activeTasks.get(downloadId);
        if (task != null) {
            task.cancel();
            activeTasks.remove(downloadId);
        }
        // 也检查队列
        pendingQueue.remove(downloadId);

        Download download = Download.find(downloadId);
        if (download != null) {
            String oldStatus = download.getStatus();
            download.setStatus(Status.PAUSED.name());
            download.save();
            notifyStatusChanged(download, oldStatus, Status.PAUSED.name());
            RefreshEvent.download();
        }

        processNextInQueue();
    }

    /**
     * 恢复下载任务
     */
    public void resumeDownload(String downloadId) {
        Download download = Download.find(downloadId);
        if (download == null) return;

        String oldStatus = download.getStatus();
        download.setStatus(Status.PENDING.name());
        download.setProgress(0);
        download.save();
        notifyStatusChanged(download, oldStatus, Status.PENDING.name());
        RefreshEvent.download();

        startDownload(download);
    }

    /**
     * 重新下载（重置进度后重试）
     */
    public void retryDownload(String downloadId) {
        Download download = Download.find(downloadId);
        if (download == null) return;

        // 删除旧文件
        File downloadDir = Download.getDownloadDir();
        String fileName = download.generateFileName();
        File oldFile = new File(downloadDir, fileName);
        if (oldFile.exists()) oldFile.delete();

        String oldStatus = download.getStatus();
        download.setStatus(Status.PENDING.name());
        download.setProgress(0);
        download.setSpeed(0);
        download.setSegmentInfo(null);
        download.save();
        notifyStatusChanged(download, oldStatus, Status.PENDING.name());
        RefreshEvent.download();

        startDownload(download);
    }

    // ==================== 队列管理 ====================

    /**
     * 从排队队列中取出下一个任务并执行
     */
    private void processNextInQueue() {
        String nextId = pendingQueue.poll();
        if (nextId == null) {
            checkAllTasksCompleted();
            return;
        }

        // 防止重复：如果该任务已在活跃列表中，跳过
        if (activeTasks.containsKey(nextId)) {
            Logger.w("DownloadManagerImpl: 队列任务已在活跃列表中，跳过: " + nextId);
            processNextInQueue();
            return;
        }

        Download download = Download.find(nextId);
        if (download == null) {
            // 数据库记录可能已被删除，继续尝试下一个
            processNextInQueue();
            return;
        }

        // 检查状态：只有 queued 或 pending 状态的任务才能开始下载
        String status = download.getStatus();
        if (!Status.QUEUED.name().equals(status) && !Status.PENDING.name().equals(status)) {
            Logger.w("DownloadManagerImpl: 队列任务状态非排队/等待，跳过: " + nextId + " status=" + status);
            processNextInQueue();
            return;
        }

        executeDownload(download);
    }

    /**
     * 检查是否所有任务都已完成
     */
    private void checkAllTasksCompleted() {
        if (activeTaskCount.get() == 0) {
            Logger.i("DownloadManagerImpl: 所有下载任务已完成");
        }
    }

    // ==================== 配置管理 ====================

    /**
     * 重新配置并发数和最大重试次数
     */
    public void reconfigure(int newMaxConcurrent, int newMaxRetries) {
        int clamped = clampConcurrent(newMaxConcurrent);
        if (clamped != this.maxConcurrent) {
            this.maxConcurrent = clamped;
            // 重建线程池
            ExecutorService oldExecutor = executor;
            executor = Executors.newFixedThreadPool(this.maxConcurrent);
            if (oldExecutor != null && !oldExecutor.isShutdown()) {
                oldExecutor.shutdown();
            }
            Logger.i("DownloadManagerImpl: 线程池已重新配置，并发数: " + this.maxConcurrent);
        }
        if (newMaxRetries >= 0 && newMaxRetries <= 10) {
            this.maxRetries = newMaxRetries;
        }
    }

    public int getMaxConcurrent() {
        return maxConcurrent;
    }

    public int getActiveCount() {
        return activeTaskCount.get();
    }

    public int getQueuedCount() {
        return pendingQueue.size();
    }

    public boolean isActive(String downloadId) {
        return activeTasks.containsKey(downloadId) || pendingQueue.contains(downloadId);
    }

    public List<String> getActiveDownloadIds() {
        return new ArrayList<>(activeTasks.keySet());
    }

    private int clampConcurrent(int value) {
        return Math.max(MIN_CONCURRENT, Math.min(MAX_CONCURRENT, value));
    }

    // ==================== 生命周期 ====================

    public void shutdown() {
        cancelAll();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
        activeTaskCount.set(0);
        listeners.clear();
    }

    // ==================== 内部任务类 ====================

    /**
     * 下载任务 Runnable
     * 封装直链下载和 M3U8 下载的完整生命周期
     */
    private class DownloadTask implements Runnable {

        private final Download download;
        private volatile boolean cancelled = false;
        private final CountDownLatch completionLatch = new CountDownLatch(1);
        File outputFile;
        private FluxDownDownloader fluxDownDownloader;
        private HttpDownloader httpDownloader;
        private long lastRefreshTime = 0;
        private long lastGoodSpeed = 0;
        private int retryCount = 0;

        DownloadTask(Download download) {
            this.download = download;
        }

        void cancel() {
            cancelled = true;
            if (fluxDownDownloader != null) fluxDownDownloader.cancel();
            if (httpDownloader != null) httpDownloader.cancel();
            completionLatch.countDown();
        }

        void deleteOutputFile() {
            if (outputFile != null && outputFile.exists()) {
                outputFile.delete();
            }
        }

        private boolean isThrottledRefresh() {
            long now = System.currentTimeMillis();
            if (now - lastRefreshTime > 800) {
                lastRefreshTime = now;
                return true;
            }
            return false;
        }

        private Map<String, String> parseHeaders() {
            java.util.Map<String, String> headers = new java.util.HashMap<>();
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
                Logger.i("DownloadManagerImpl: 任务开始: " + download.getVodName() + " URL: " + download.getUrl());

                File downloadDir = Download.getDownloadDir();
                Logger.i("DownloadManagerImpl: 下载路径: " + downloadDir.getAbsolutePath() + " 可写: " + downloadDir.canWrite());
                if (!downloadDir.exists()) downloadDir.mkdirs();

                String fileName = download.generateFileName();
                outputFile = new File(downloadDir, fileName);

                boolean isM3U8 = isM3U8Url(download.getUrl());
                boolean hasM3U8Header = download.getHeader() != null && download.getHeader().contains("M3U8-Content");

                if (isM3U8 || hasM3U8Header) {
                    downloadWithM3U8();
                } else {
                    downloadWithHttp();
                }

                if (!cancelled) {
                    completionLatch.await(60, TimeUnit.MINUTES);
                }

            } catch (CancellationException e) {
                Logger.i("DownloadManagerImpl: 任务被取消: " + download.getVodName());
            } catch (Exception e) {
                if (!cancelled) {
                    Logger.e("DownloadManagerImpl: 任务异常: " + download.getVodName(), e);
                    handleError(download, outputFile, e.getMessage() != null ? e.getMessage() : "未知错误");
                }
            } finally {
                activeTasks.remove(download.getId());
                activeTaskCount.decrementAndGet();
                processNextInQueue();
            }
        }

        /**
         * 直链下载（使用 OkHttp）
         */
        private void downloadWithHttp() {
            Logger.i("DownloadManagerImpl: 直链下载: " + download.getUrl());

            httpDownloader = new HttpDownloader();
            httpDownloader.download(download.getUrl(), outputFile.getParentFile(),
                    outputFile.getName(), parseHeaders(),
                    new HttpDownloader.Callback() {
                        @Override
                        public void onStart(long totalSize) {
                            updateNotification(download.getVodName() + " - 下载中", 0);
                        }

                        @Override
                        public void onProgress(int progress, long speed, long downloaded, long total) {
                            // 速度平滑
                            long actualSpeed = speed;
                            if (speed > 0) {
                                lastGoodSpeed = speed;
                            } else if (lastGoodSpeed > 0 && progress >= 0 && progress < 100) {
                                actualSpeed = lastGoodSpeed;
                            }
                            if (progress >= 0) download.setProgress(progress);
                            download.setSpeed(actualSpeed);
                            String oldStatus = download.getStatus();
                            download.setStatus(Status.DOWNLOADING.name());
                            saveDownload(download);
                            notifyProgress(download, progress >= 0 ? progress : 0, actualSpeed);
                            notifyStatusChanged(download, oldStatus, Status.DOWNLOADING.name());

                            String info = download.getVodName() + " - " + formatSpeed(actualSpeed) + "/s";
                            if (progress >= 0) info += " " + progress + "%";
                            updateNotification(info, progress >= 0 ? progress : 0);
                            if (isThrottledRefresh()) RefreshEvent.download();
                        }

                        @Override
                        public void onSuccess(File file) {
                            if (file == null || !file.exists() || file.length() == 0) {
                                handleError(download, file, "下载文件不存在或为空");
                                completionLatch.countDown();
                                return;
                            }
                            Logger.i("DownloadManagerImpl: 下载完成: " + file.getAbsolutePath() + " 大小: " + file.length());
                            completeDownload(download, file);
                        }

                        @Override
                        public void onError(String error) {
                            if (shouldRetry(error)) {
                                retryCount++;
                                Logger.i("DownloadManagerImpl: 直链下载重试 (" + retryCount + "/" + maxRetries + "): " + error);
                                httpDownloader.download(download.getUrl(), outputFile.getParentFile(),
                                        outputFile.getName(), parseHeaders(), this);
                            } else {
                                handleError(download, outputFile, error);
                                completionLatch.countDown();
                            }
                        }
                    });
        }

        /**
         * M3U8 下载（使用 FluxDownDownloader）
         */
        private void downloadWithM3U8() {
            Logger.i("DownloadManagerImpl: M3U8下载: " + download.getVodName() + " URL: " + download.getUrl());

            fluxDownDownloader = new FluxDownDownloader(
                    download.getUrl(),
                    parseHeaders(),
                    outputFile.getParentFile(),
                    outputFile.getName(),
                    new FluxDownDownloader.Callback() {
                        @Override
                        public void onStart() {
                            updateNotification(download.getVodName() + " - 开始下载", 0);
                            String oldStatus = download.getStatus();
                            download.setStatus(Status.DOWNLOADING.name());
                            saveDownload(download);
                            notifyStatusChanged(download, oldStatus, Status.DOWNLOADING.name());
                            RefreshEvent.download();
                        }

                        @Override
                        public void onProgress(int progress, int downloadedSegments, int totalSegments, long speed) {
                            long actualSpeed = speed;
                            if (speed > 0) {
                                lastGoodSpeed = speed;
                            } else if (lastGoodSpeed > 0 && progress < 100) {
                                actualSpeed = lastGoodSpeed;
                            }
                            download.setProgress(progress);
                            download.setSpeed(actualSpeed);
                            download.setSegmentInfo(downloadedSegments + "/" + totalSegments);
                            String oldStatus = download.getStatus();
                            download.setStatus(Status.DOWNLOADING.name());
                            saveDownload(download);
                            notifyProgress(download, progress, actualSpeed);
                            notifyStatusChanged(download, oldStatus, Status.DOWNLOADING.name());
                            updateNotification(download.getVodName() + " " + formatSpeed(actualSpeed) + "/s", progress);
                            if (isThrottledRefresh()) RefreshEvent.download();
                        }

                        @Override
                        public void onMerging() {
                            String oldStatus = download.getStatus();
                            download.setStatus(Status.MERGING.name());
                            saveDownload(download);
                            notifyStatusChanged(download, oldStatus, Status.MERGING.name());
                            updateNotification(download.getVodName() + " - 视频合并中", download.getProgress());
                            RefreshEvent.download();

                            // 兜底：2秒后检查状态是否仍为 merging（防止事件丢失）
                            App.post(() -> {
                                App.execute(() -> {
                                    Download current = Download.find(download.getId());
                                    if (current != null && Status.MERGING.name().equals(current.getStatus())) {
                                        App.post(() -> RefreshEvent.download());
                                    }
                                });
                            }, 2000);
                        }

                        @Override
                        public void onSuccess(File file) {
                            if (file == null || !file.exists() || file.length() == 0) {
                                handleError(download, file, "合并后的文件不存在或为空");
                                completionLatch.countDown();
                                return;
                            }
                            // M3U8 完整性校验
                            if (!verifyM3U8Integrity(file)) {
                                handleError(download, file, "M3U8 文件完整性校验失败");
                                file.delete();
                                completionLatch.countDown();
                                return;
                            }
                            Logger.i("DownloadManagerImpl: M3U8下载完成: " + file.getAbsolutePath() + " 大小: " + file.length());
                            completeDownload(download, file);
                        }

                        @Override
                        public void onError(String error) {
                            Logger.e("DownloadManagerImpl: M3U8 下载失败: " + error);
                            handleError(download, outputFile, error);
                            completionLatch.countDown();
                        }
                    });
            fluxDownDownloader.start();
        }

        /**
         * 完成下载
         */
        private void completeDownload(Download download, File file) {
            download.setFilePath(file.getAbsolutePath());
            download.setProgress(100);
            String oldStatus = download.getStatus();
            download.setStatus(Status.COMPLETED.name());
            saveDownload(download);
            notifyStatusChanged(download, oldStatus, Status.COMPLETED.name());
            notifyCompleted(download);
            updateNotification(download.getVodName() + " - 下载完成", 100);
            RefreshEvent.download();
            Notify.show("下载完成: " + download.getVodName());
            completionLatch.countDown();
        }

        /**
         * 处理错误
         */
        private void handleError(Download download, File outputFile, String error) {
            Logger.e("DownloadManagerImpl: 下载失败: " + error + " URL: " + download.getUrl() + " vodName: " + download.getVodName());
            // 取消下载线程
            cancel();
            if (outputFile != null && outputFile.exists()) {
                outputFile.delete();
            }
            String oldStatus = download.getStatus();
            download.setStatus(Status.FAILED.name());
            download.setErrorMsg(error);
            saveDownload(download);
            notifyStatusChanged(download, oldStatus, Status.FAILED.name());
            notifyFailed(download, error);
            Notify.show("下载失败: " + error);
            RefreshEvent.download();
        }

        /**
         * 判断错误是否可重试
         */
        private boolean shouldRetry(String error) {
            if (retryCount >= maxRetries) return false;
            DownloadStateMachine.ErrorType errorType = DownloadStateMachine.classifyError(
                    new RuntimeException(error));
            return errorType == DownloadStateMachine.ErrorType.RETRYABLE;
        }

        /**
         * M3U8 完整性校验
         * 检查合并后的文件头（MP4 文件头或视频文件特征）
         */
        private boolean verifyM3U8Integrity(File file) {
            if (file == null || !file.exists() || file.length() == 0) return false;
            // 检查文件头：MP4 文件以 ftyp box 开头
            try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                byte[] header = new byte[8];
                int read = fis.read(header);
                if (read < 8) return false;
                // MP4 ftyp: 00 00 00 XX 66 74 79 70
                boolean isMP4 = (header[4] == 0x66 && header[5] == 0x74 && header[6] == 0x79 && header[7] == 0x70);
                if (!isMP4) {
                    Logger.w("DownloadManagerImpl: M3U8合并文件MP4头检测失败，可能不是标准MP4格式");
                    // 对于部分非标准MP4文件，如果文件大小>1MB则认为通过
                    return file.length() > 1024 * 1024;
                }
                return true;
            } catch (Exception e) {
                Logger.w("DownloadManagerImpl: 完整性校验异常", e);
                return file.length() > 0;
            }
        }

        private void updateNotification(String title, int progress) {
        }
    }

    // ==================== 工具方法 ====================

    private void saveDownload(Download download) {
        App.execute(() -> {
            try {
                download.save();
            } catch (Exception e) {
                Logger.e("DownloadManagerImpl: 保存下载状态失败", e);
            }
        });
    }

    private boolean isM3U8Url(String url) {
        if (url == null || url.isEmpty()) return false;
        String lowerUrl = url.toLowerCase();
        if (lowerUrl.contains(".m3u8") || lowerUrl.contains("m3u8")
                || lowerUrl.contains("hls") || lowerUrl.contains("playlist")) {
            return true;
        }
        return false;
    }

    private String formatSpeed(long speed) {
        if (speed < 1024) return speed + "B";
        if (speed < 1024 * 1024) return String.format("%.1fKB", speed / 1024.0);
        return String.format("%.1fMB", speed / 1024.0 / 1024.0);
    }
}