package com.fongmi.android.tv.utils;

import android.os.Handler;
import android.os.Looper;

import com.github.catvod.utils.Logger;
import com.xunlei.downloadlib.XLTaskHelper;
import com.xunlei.downloadlib.parameter.GetTaskId;
import com.xunlei.downloadlib.parameter.XLTaskInfo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 下载器，先尝试迅雷SDK（带30秒超时），超时或失败立即降级为OkHttp直接下载
 */
public class ThunderDownloader {

    private static final int POLL_INTERVAL_MS = 500;
    private static final int POLL_TIMEOUT_MS = 30_000; // 迅雷轮询超时30秒
    private static final int BUFFER_SIZE = 8192;
    private static final long PROGRESS_INTERVAL_MS = 300;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();

    private volatile boolean cancelled = false;
    private GetTaskId taskId;
    private File outputFile;

    public interface Callback {
        void onStart(long totalSize);
        void onProgress(int progress, long speed, long downloaded, long total);
        void onSuccess(File outputFile);
        void onError(String error);
    }

    public void download(String url, File saveDir, String fileName,
                         Map<String, String> headers, Callback callback) {
        executor.submit(() -> {
            try {
                Logger.d("下载开始: " + url);
                if (!saveDir.exists()) saveDir.mkdirs();
                outputFile = new File(saveDir, fileName);

                // HTTP/HTTPS 链接直接走 OkHttp，跳过迅雷SDK
                // 迅雷SDK下载文件到自己的临时目录，不写入目标路径，导致文件不可见
                if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                    Logger.d("HTTP链接，直接使用OkHttp下载");
                    okHttpDownload(url, outputFile, headers, callback);
                    return;
                }

                // 非HTTP链接先尝试迅雷SDK（带30秒超时）
                boolean thunderDone = tryThunderDownload(url, saveDir, callback);
                if (thunderDone || cancelled) return;

                // 超时或失败 → OkHttp直接下载
                Logger.d("迅雷不可用，使用OkHttp直接下载");
                okHttpDownload(url, outputFile, headers, callback);

            } catch (Exception e) {
                Logger.e("下载失败: " + e.getMessage());
                if (callback != null) {
                    mainHandler.post(() -> callback.onError("下载失败: " + e.getMessage()));
                }
            }
        });
    }

    /**
     * 尝试迅雷SDK下载
     * @return true=下载已完成(成功或失败已回调), false=需降级到OkHttp
     */
    private boolean tryThunderDownload(String url, File saveDir, Callback callback) {
        try {
            taskId = XLTaskHelper.get().addThunderTask(url, saveDir);
            if (taskId == null || taskId.getTaskId() == 0) {
                Logger.e("迅雷任务ID为0");
                return false;
            }

            Logger.d("迅雷任务已创建: taskId=" + taskId.getTaskId());

            // 轮询进度（带超时）
            long startTime = System.currentTimeMillis();
            long lastProgressTime = startTime;
            long lastDownloaded = 0;

            while (!cancelled) {
                // 超时检查（30秒无进度或总超时60秒）
                if (System.currentTimeMillis() - startTime > POLL_TIMEOUT_MS * 2) {
                    Logger.e("迅雷轮询超时，降级到OkHttp");
                    try { XLTaskHelper.get().stopTask(taskId); } catch (Exception e) {}
                    return false;
                }

                try { Thread.sleep(POLL_INTERVAL_MS); } catch (InterruptedException e) { break; }

                try {
                    XLTaskInfo info = XLTaskHelper.get().getTaskInfo(taskId);
                    int status = info.getTaskStatus();
                    long downloaded = info.mDownloadSize;
                    long total = info.mFileSize;
                    long speed = info.mDownloadSpeed;

                    long now = System.currentTimeMillis();
                    if (now - lastProgressTime > 1000) {
                        long bytesDiff = downloaded - lastDownloaded;
                        long timeDiff = now - lastProgressTime;
                        if (bytesDiff == 0 && timeDiff > 10_000) {
                            // 10秒无数据，可能卡住了，降级
                            Logger.e("迅雷10秒无数据，降级到OkHttp");
                            try { XLTaskHelper.get().stopTask(taskId); } catch (Exception e) {}
                            return false;
                        }
                        lastProgressTime = now;
                        lastDownloaded = downloaded;
                    }

                    // 无总大小时无法计算百分比
                    int progress = total > 0 ? (int)(downloaded * 100 / total) : -1;

                    Logger.d("迅雷状态: status=" + status + " progress=" + progress + "% speed=" + speed);

                    if (callback != null) {
                        final int p = progress;
                        final long s = speed;
                        final long d = downloaded;
                        final long t = total;
                        mainHandler.post(() -> callback.onProgress(p, s, d, t));
                    }

                    if (status == 2) {
                        // 完成
                        File resultFile = taskId.getSaveFile();
                        Logger.d("迅雷下载完成: " + (resultFile != null ? resultFile.getAbsolutePath() : "?"));
                        if (callback != null) {
                            final File f = resultFile;
                            mainHandler.post(() -> callback.onSuccess(f));
                        }
                        return true;
                    } else if (status == 3) {
                        // 失败，降级
                        Logger.e("迅雷下载失败(" + info.mErrorCode + ")，降级到OkHttp");
                        try { XLTaskHelper.get().stopTask(taskId); } catch (Exception e) {}
                        return false;
                    }
                    // status == 0 → 继续轮询
                } catch (Exception e) {
                    Logger.e("迅雷轮询异常: " + e.getMessage());
                }
            }
            return cancelled;
        } catch (Exception e) {
            Logger.e("迅雷任务异常: " + e.getMessage());
            return false;
        }
    }

    /**
     * OkHttp直接下载（带3次重试）
     */
    private void okHttpDownload(String url, File outputFile, Map<String, String> headers, Callback callback) {
        Headers.Builder hb = new Headers.Builder();
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    hb.add(entry.getKey(), entry.getValue());
                }
            }
        }
        boolean hasUA = headers != null && (headers.get("User-Agent") != null || headers.get("user-agent") != null);
        if (!hasUA) {
            hb.add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        }
        okHttpDownloadRetry(url, outputFile, hb.build(), callback, 1);
    }

    private void okHttpDownloadRetry(String url, File outputFile, okhttp3.Headers headers, Callback callback, int attempt) {
        try {
            if (attempt > 1) {
                long delay = 1000L * attempt;
                Logger.d("OkHttp重试(" + attempt + "/3) 等待 " + delay + "ms");
                Thread.sleep(delay);
            }

            if (outputFile.exists()) outputFile.delete();

            long expectedSize = getContentLength(url, headers);

            Request request = new Request.Builder().url(url).headers(headers).build();
            Response response = client.newCall(request).execute();

            if (!response.isSuccessful()) {
                String body = response.body() != null ? response.body().string() : "";
                response.close();
                throw new Exception("HTTP " + response.code() + " " + body);
            }

            long responseLength = response.body().contentLength();
            if (responseLength < 0) responseLength = 0;
            long totalSize = Math.max(expectedSize, responseLength);

            Logger.d("OkHttp开始下载(尝试" + attempt + "/3), HEAD: " + expectedSize + " bytes, 响应: " + responseLength + " bytes");
            if (callback != null) {
                final long ft = totalSize;
                mainHandler.post(() -> callback.onStart(ft));
            }

            long downloaded = 0;
            long lastTime = System.currentTimeMillis();
            long lastBytes = 0;

            try (InputStream is = response.body().byteStream();
                 FileOutputStream fos = new FileOutputStream(outputFile)) {
                byte[] buffer = new byte[BUFFER_SIZE * 4];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    if (cancelled) {
                        fos.flush();
                        return;
                    }
                    fos.write(buffer, 0, bytesRead);
                    downloaded += bytesRead;

                    long now = System.currentTimeMillis();
                    if (now - lastTime >= PROGRESS_INTERVAL_MS) {
                        long timeDiff = now - lastTime;
                        long speed = timeDiff > 0 ? (downloaded - lastBytes) * 1000 / timeDiff : 0;
                        int progress = totalSize > 0 ? (int)(downloaded * 100 / totalSize) : -1;

                        if (callback != null) {
                            final int p = progress;
                            final long s = speed;
                            final long d = downloaded;
                            final long t = totalSize;
                            mainHandler.post(() -> callback.onProgress(p, s, d, t));
                        }
                        lastTime = now;
                        lastBytes = downloaded;
                    }
                }
                fos.flush();
            }
            response.close();

            if (cancelled) return;

            if (!verifyFileIntegrity(outputFile, downloaded, totalSize, expectedSize, callback)) {
                return;
            }

            Logger.d("OkHttp下载完成: " + outputFile.getAbsolutePath() + " 大小: " + outputFile.length());
            if (callback != null) {
                mainHandler.post(() -> callback.onSuccess(outputFile));
            }

        } catch (Exception e) {
            Logger.e("OkHttp下载失败(尝试" + attempt + "/3): " + e.getMessage());
            if (outputFile.exists()) outputFile.delete();
            if (cancelled) return;
            if (attempt < 3) {
                okHttpDownloadRetry(url, outputFile, headers, callback, attempt + 1);
            } else {
                if (callback != null) {
                    mainHandler.post(() -> callback.onError("下载失败: " + e.getMessage()));
                }
            }
        }
    }

    /**
     * 发送HEAD请求获取文件真实大小
     */
    private long getContentLength(String url, okhttp3.Headers headers) {
        try {
            Request headRequest = new Request.Builder().url(url).headers(headers).head().build();
            Response headResponse = client.newCall(headRequest).execute();
            long length = headResponse.body() != null ? headResponse.body().contentLength() : -1;
            headResponse.close();
            return length > 0 ? length : 0;
        } catch (Exception e) {
            Logger.d("HEAD请求失败，忽略: " + e.getMessage());
            return 0;
        }
    }

    /**
     * 验证下载文件的完整性
     * @return true=验证通过, false=验证失败（已回调onError）
     */
    private boolean verifyFileIntegrity(File outputFile, long downloaded, long totalSize, long expectedSize, Callback callback) {
        // 检查1：已知总量时验证下载量是否匹配
        if (totalSize > 0 && downloaded < totalSize) {
            Logger.e("下载不完整: 期望 " + totalSize + " bytes, 实际 " + downloaded + " bytes");
            if (outputFile.exists()) outputFile.delete();
            final String errorMsg = "下载不完整: 期望 " + totalSize + " bytes, 实际 " + downloaded + " bytes";
            if (callback != null) mainHandler.post(() -> callback.onError(errorMsg));
            return false;
        }

        // 检查2：用磁盘文件大小二次验证（防止缓冲区数据未写入的问题）
        long fileSize = outputFile.exists() ? outputFile.length() : 0;
        if (totalSize > 0 && fileSize < totalSize) {
            Logger.e("文件大小不匹配: 期望 " + totalSize + " bytes, 磁盘 " + fileSize + " bytes");
            if (outputFile.exists()) outputFile.delete();
            final String errorMsg = "文件大小不匹配: 期望 " + totalSize + " bytes, 磁盘 " + fileSize + " bytes";
            if (callback != null) mainHandler.post(() -> callback.onError(errorMsg));
            return false;
        }

        // 检查3：文件大小与已下载量不一致（逻辑错误）
        if (fileSize > 0 && downloaded > 0 && fileSize != downloaded) {
            Logger.w("文件大小与下载量不一致: 文件=" + fileSize + " 下载=" + downloaded);
        }

        // 检查4：有总大小时，验证实际文件大小是否接近（允许1%误差）
        if (totalSize > 0 && fileSize > 0) {
            long diff = Math.abs(fileSize - totalSize);
            if (diff > totalSize * 0.01 && diff > 1024 * 1024) { // 超过1%且大于1MB
                Logger.w("文件大小偏差较大: 期望=" + totalSize + " 实际=" + fileSize + " 偏差=" + diff);
                // 不直接报错，仅记录日志
            }
        }

        return true;
    }

    public void cancel() {
        cancelled = true;
        if (taskId != null) {
            try { XLTaskHelper.get().stopTask(taskId); } catch (Exception e) {}
        }
    }

    public void shutdown() {
        cancel();
        if (!executor.isShutdown()) executor.shutdownNow();
    }

    public File getOutputFile() { return outputFile; }
}