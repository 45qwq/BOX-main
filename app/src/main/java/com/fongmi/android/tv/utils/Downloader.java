package com.fongmi.android.tv.utils;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Download;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.service.DownloadService;
import com.fongmi.android.tv.service.DownloadWorker;
import com.github.catvod.utils.Path;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.net.URI;
import java.util.Map;
import java.util.UUID;

public class Downloader {

    private Result result;
    private Activity activity;
    private String title;
    private String image;

    private static class Loader {
        static volatile Downloader INSTANCE = new Downloader();
    }

    public static Downloader get() {
        return Loader.INSTANCE;
    }

    public Downloader title(String title) {
        this.title = title;
        return this;
    }
    public Downloader image(String image) {
        this.image = image;
        return this;
    }

    public Downloader result(Result result) {
        this.result = result;
        return this;
    }

    public void start(Activity activity) {
        this.activity = activity;
        if (result.hasMsg()) {
            Notify.show(result.getMsg());
        } else {
            // 始终创建下载任务
            download();
        }
    }

    public void start(Activity activity, String url, Map<String, String> headers) {
        this.activity = activity;
        if (url == null || url.isEmpty()) {
            Notify.show(R.string.error_play_url);
            return;
        }
        // 提取真实URL（代理URL中可能包含真实地址）
        String realUrl = extractRealUrl(url);
        if (realUrl == null || isBlockedUrl(realUrl)) {
            Notify.show(R.string.download_blocked);
            return;
        }
        // 检查文件是否已存在（弹窗询问是否覆盖）
        if (checkFileOverwrite(activity, realUrl, () -> downloadDirect(realUrl, headers))) {
            return;
        }
        // 创建下载任务（app-specific 目录，无需额外存储权限）
        downloadDirect(realUrl, headers);
    }

    /**
     * 检查目标文件是否已存在，存在则弹窗询问是否覆盖
     * @return true 文件已存在且弹窗已显示（等待用户操作），false 文件不存在可直接下载
     */
    private boolean checkFileOverwrite(Activity activity, String url, Runnable onProceed) {
        String fileName = Download.generateDownloadFileName(this.title != null ? this.title : "video", url);
        File targetFile = new File(Path.download(), fileName);
        if (targetFile.exists()) {
            String fileSize = formatFileSize(targetFile.length());
            new MaterialAlertDialogBuilder(activity)
                    .setTitle("文件已存在")
                    .setMessage("" + fileName + "\n\n大小: " + fileSize + "\n\n是否重新下载？（将覆盖原文件）")
                    .setCancelable(true)
                    .setNegativeButton("取消", null)
                    .setPositiveButton("重新下载", (dialog, which) -> {
                        targetFile.delete();
                        if (onProceed != null) onProceed.run();
                    })
                    .show();
            return true;
        }
        return false;
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    /**
     * 从代理URL中提取真实下载地址
     * 如 http://127.0.0.1:9978/proxy?do=m3u8&url=https%3A%2F%2Fxxx.com%2Findex.m3u8
     * 提取 https://xxx.com/index.m3u8
     * 如果URL不含代理或无法提取，返回原URL
     */
    private String extractRealUrl(String url) {
        if (url == null || url.isEmpty()) return url;
        try {
            // 先检查是否含127.0.0.1/0.0.0.0/localhost
            String host = URI.create(url).getHost();
            if (host == null) return url;
            host = host.toLowerCase();
            boolean isProxy = host.equals("127.0.0.1") || host.equals("0.0.0.0") || host.equals("localhost") || host.startsWith("127.");
            if (!isProxy) return url; // 非代理URL，直接返回

            // 从查询参数中提取url参数
            int qIdx = url.indexOf('?');
            if (qIdx < 0) return null; // 代理URL但没有查询参数，无法下载
            String query = url.substring(qIdx + 1);
            String[] params = query.split("&");
            for (String param : params) {
                if (param.startsWith("url=")) {
                    String encoded = param.substring(4);
                    String decoded = Uri.decode(encoded);
                    if (decoded != null && !decoded.isEmpty() &&
                        (decoded.startsWith("http://") || decoded.startsWith("https://"))) {
                        Log.i("Downloader", "从代理URL提取真实地址: " + decoded);
                        return decoded;
                    }
                }
            }
            // 有代理但没找到有效url参数，无法下载
            Log.w("Downloader", "代理URL中未找到有效真实地址: " + url);
            return null;
        } catch (Exception e) {
            Log.w("Downloader", "提取真实URL异常", e);
            return url;
        }
    }

    private boolean isBlockedUrl(String url) {
        if (url == null) return true;
        try {
            String host = URI.create(url).getHost();
            if (host == null) return false;
            host = host.toLowerCase();
            return host.equals("127.0.0.1") || host.equals("0.0.0.0") || host.equals("localhost") || host.startsWith("127.");
        } catch (Exception e) {
            return false;
        }
    }

    private void downloadDirect(String url, Map<String, String> headers) {
        try {
            String downloadId = UUID.randomUUID().toString();
            String headersStr = convertHeadersToString(headers);

            if (url == null || url.isEmpty()) {
                Notify.show(R.string.error_play_url);
                return;
            }

            boolean isM3U8 = url.toLowerCase().contains(".m3u8") ||
                           url.toLowerCase().contains("m3u8") ||
                           url.toLowerCase().contains("playlist");

            if (isM3U8) {
                Log.i("Downloader", "检测到M3U8流媒体: " + url);
            }

            Download download = new Download(downloadId, image, title, url, headersStr);

            App.execute(() -> {
                try {
                    download.save();
                } catch (Exception e) {
                    App.post(() -> Notify.show("下载失败: 数据库错误"));
                    return;
                }
                App.post(() -> {
                    try {
                        if (DownloadService.isWorkManagerEnabled()) {
                            DownloadWorker.enqueue(App.get(), download);
                        } else {
                            DownloadService.startDownload(download);
                        }
                        Notify.show(R.string.download_start);
                    } catch (Exception e) {
                        Notify.show("下载失败: 服务启动错误");
                    }
                });
            });

        } catch (Exception e) {
            Notify.show("下载失败: " + e.getMessage());
        }
    }

    private void download() {
        try {
            String downloadId = UUID.randomUUID().toString();
            String url = result.getRealUrl();
            String headers = convertHeadersToString(result.getHeaders());

            if (url == null || url.isEmpty()) {
                Notify.show(R.string.error_play_url);
                return;
            }

            // 提取真实URL（代理URL中可能包含真实地址）
            String realUrl = extractRealUrl(url);
            if (realUrl == null || isBlockedUrl(realUrl)) {
                Notify.show(R.string.download_blocked);
                return;
            }

            boolean isM3U8 = realUrl.toLowerCase().contains(".m3u8") ||
                           realUrl.toLowerCase().contains("m3u8") ||
                           realUrl.toLowerCase().contains("playlist");

            if (isM3U8) {
                Log.i("Downloader", "检测到M3U8流媒体: " + realUrl);
            }

            Download download = new Download(downloadId, image, title, realUrl, headers);

            App.execute(() -> {
                try {
                    download.save();
                } catch (Exception e) {
                    App.post(() -> Notify.show("下载失败: 数据库错误"));
                    return;
                }
                App.post(() -> {
                    try {
                        if (DownloadService.isWorkManagerEnabled()) {
                            DownloadWorker.enqueue(App.get(), download);
                        } else {
                            DownloadService.startDownload(download);
                        }
                        Notify.show(R.string.download_start);
                    } catch (Exception e) {
                        Notify.show("下载失败: 服务启动错误");
                    }
                });
            });

        } catch (Exception e) {
            Notify.show("下载失败: " + e.getMessage());
        }
    }

    private String convertHeadersToString(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(entry.getKey()).append(": ").append(entry.getValue());
        }
        return sb.toString();
    }
}