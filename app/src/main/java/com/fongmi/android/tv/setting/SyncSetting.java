package com.fongmi.android.tv.setting;

import com.github.catvod.utils.Prefers;

/**
 * WebDAV/局域网同步相关配置
 */
public class SyncSetting {

    public static int getSyncMode() {
        return Prefers.getInt("sync_mode");
    }

    public static void putSyncMode(int mode) {
        Prefers.put("sync_mode", mode);
    }

    public static boolean isAutoSync() {
        return Prefers.getBoolean("auto_sync", false);
    }

    public static void putAutoSync(boolean autoSync) {
        Prefers.put("auto_sync", autoSync);
    }

    public static int getSyncInterval() {
        return Prefers.getInt("sync_interval", 30);
    }

    public static void putSyncInterval(int minutes) {
        Prefers.put("sync_interval", minutes);
    }

    public static boolean isSyncEnabled() {
        return Prefers.getBoolean("sync_enabled", false);
    }

    public static void putSyncEnabled(boolean enabled) {
        Prefers.put("sync_enabled", enabled);
    }

    // WebDAV
    public static String getWebDAVUrl() {
        return Prefers.getString("webdav_url", "");
    }

    public static void putWebDAVUrl(String url) {
        Prefers.put("webdav_url", url);
    }

    public static String getWebDAVUsername() {
        return Prefers.getString("webdav_username", "");
    }

    public static void putWebDAVUsername(String username) {
        Prefers.put("webdav_username", username);
    }

    public static String getWebDAVPassword() {
        return Prefers.getString("webdav_password", "");
    }

    public static void putWebDAVPassword(String password) {
        Prefers.put("webdav_password", password);
    }

    public static String getWebDAVSyncMode() {
        return Prefers.getString("webdav_sync_mode", "ACCOUNT");
    }

    public static void putWebDAVSyncMode(String mode) {
        Prefers.put("webdav_sync_mode", mode);
    }

    public static String getWebDAVSyncCode() {
        return Prefers.getString("webdav_sync_code", "");
    }

    public static void putWebDAVSyncCode(String code) {
        Prefers.put("webdav_sync_code", code);
    }

    public static String getWebDAVPublicUrl() {
        return Prefers.getString("webdav_public_url", "");
    }

    public static void putWebDAVPublicUrl(String url) {
        Prefers.put("webdav_public_url", url);
    }

    public static boolean isWebDAVAutoSync() {
        return Prefers.getBoolean("webdav_auto_sync", false);
    }

    public static void putWebDAVAutoSync(boolean autoSync) {
        Prefers.put("webdav_auto_sync", autoSync);
    }

    public static int getWebDAVSyncInterval() {
        return Prefers.getInt("webdav_sync_interval", 60);
    }

    public static void putWebDAVSyncInterval(int minutes) {
        Prefers.put("webdav_sync_interval", minutes);
    }
}
