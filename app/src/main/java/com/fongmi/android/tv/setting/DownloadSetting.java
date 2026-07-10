package com.fongmi.android.tv.setting;

import android.net.Uri;
import android.provider.DocumentsContract;

import com.fongmi.android.tv.Constant;
import com.github.catvod.utils.Logger;
import com.github.catvod.utils.Prefers;

import java.io.File;

/**
 * 下载相关配置
 */
public class DownloadSetting {

    public static String getDownloadPath() {
        String path = Prefers.getString("download_path", "");
        if (path == null || path.isEmpty()) return path;
        if (!path.startsWith("content://")) return path;
        return resolveContentUri(path);
    }

    public static void putDownloadPath(String path) {
        Prefers.put("download_path", path);
    }

    public static int getDownloadConcurrent() {
        return Math.min(Math.max(Prefers.getInt("download_concurrent", 3), 1), 5);
    }

    public static void putDownloadConcurrent(int count) {
        Prefers.put("download_concurrent", Math.min(Math.max(count, 1), 5));
    }

    /**
     * 将 SAF 返回的 content:// URI 转换为真实文件路径
     */
    private static String resolveContentUri(String contentUri) {
        try {
            Uri uri = Uri.parse(contentUri);
            String docId = DocumentsContract.getTreeDocumentId(uri);
            int colon = docId.indexOf(':');
            if (colon < 0) return "";
            String volumeId = docId.substring(0, colon);
            String relativePath = docId.substring(colon + 1);
            String[] mounts = {
                "/storage/" + volumeId,
                "/storage/emulated/" + ("primary".equals(volumeId) ? "0" : volumeId),
                "/mnt/media_rw/" + volumeId,
                "/mnt/" + volumeId
            };
            for (String mount : mounts) {
                File f = new File(mount, relativePath);
                if (f.exists() && f.canWrite()) return f.getAbsolutePath();
                if (f.exists()) return f.getAbsolutePath();
            }
        } catch (Exception e) {
            Logger.w("DownloadSetting resolveContentUri", e);
        }
        return "";
    }
}
