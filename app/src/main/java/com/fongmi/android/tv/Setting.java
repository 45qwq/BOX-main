package com.fongmi.android.tv;

import android.content.Intent;
import android.provider.Settings;

import com.fongmi.android.tv.setting.DownloadSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.SyncSetting;
import com.github.catvod.utils.Prefers;

/**
 * 全局配置入口（向后兼容）
 *
 * 内部委托给分类配置类：
 * - PlayerSetting: 播放器、弹幕、字幕、超分
 * - SyncSetting: WebDAV/局域网同步
 * - DownloadSetting: 下载路径、并发数
 *
 * 新代码建议直接使用分类类
 */
public class Setting {

    // ==================== 网络/全局配置 ====================

    public static String getDoh() {
        return Prefers.getString("doh");
    }

    public static void putDoh(String doh) {
        Prefers.put("doh", doh);
    }

    public static String getProxy() {
        return Prefers.getString("proxy");
    }

    public static void putProxy(String proxy) {
        Prefers.put("proxy", proxy);
    }

    public static String getKeyword() {
        return Prefers.getString("keyword");
    }

    public static void putKeyword(String keyword) {
        Prefers.put("keyword", keyword);
    }

    public static String getHot() {
        return Prefers.getString("hot");
    }

    public static void putHot(String hot) {
        Prefers.put("hot", hot);
    }

    public static String getUa() {
        return Prefers.getString("ua");
    }

    public static void putUa(String ua) {
        Prefers.put("ua", ua);
    }

    public static int getWall() {
        return Prefers.getInt("wall", Constant.DEFAULT_WALL);
    }

    public static void putWall(int wall) {
        Prefers.put("wall", wall);
    }

    public static int getDarkMode() {
        return Prefers.getInt("dark_mode", 1);
    }

    public static void putDarkMode(int mode) {
        Prefers.put("dark_mode", mode);
    }

    public static int getReset() {
        return Prefers.getInt("reset", 0);
    }

    public static void putReset(int reset) {
        Prefers.put("reset", reset);
    }

    public static int getSiteMode() {
        return Prefers.getInt("site_mode");
    }

    public static void putSiteMode(int mode) {
        Prefers.put("site_mode", mode);
    }

    public static boolean isIncognito() {
        return Prefers.getBoolean("incognito");
    }

    public static void putIncognito(boolean incognito) {
        Prefers.put("incognito", incognito);
    }

    public static boolean isInvert() {
        return Prefers.getBoolean("invert");
    }

    public static void putInvert(boolean invert) {
        Prefers.put("invert", invert);
    }

    public static boolean isAcross() {
        return Prefers.getBoolean("across", true);
    }

    public static void putAcross(boolean across) {
        Prefers.put("across", across);
    }

    public static boolean isChange() {
        return Prefers.getBoolean("change", true);
    }

    public static void putChange(boolean change) {
        Prefers.put("change", change);
    }

    public static boolean getUpdate() {
        return Prefers.getBoolean("update", true);
    }

    public static void putUpdate(boolean update) {
        Prefers.put("update", update);
    }

    public static boolean getAutoUpdateCheck() {
        return Prefers.getBoolean("auto_update_check", false);
    }

    public static void putAutoUpdateCheck(boolean autoUpdateCheck) {
        Prefers.put("auto_update_check", autoUpdateCheck);
    }

    public static boolean getUseCnMirror() {
        return Prefers.getBoolean("use_cn_mirror", false);
    }

    public static void putUseCnMirror(boolean useCnMirror) {
        Prefers.put("use_cn_mirror", useCnMirror);
    }

    public static boolean isPrivacyAgreed() {
        return Prefers.getBoolean("privacy_agreed_v1", false);
    }

    public static void setPrivacyAgreed(boolean agreed) {
        Prefers.put("privacy_agreed_v1", agreed);
    }

    public static boolean isHistoryVisible() {
        return false;
    }

    public static void putHistoryVisible(boolean visible) {
        Prefers.put("history_visible", visible);
    }

    public static boolean isAIAdBlockEnabled() {
        return Prefers.getBoolean("ai_ad_block", true);
    }

    public static void putAIAdBlockEnabled(boolean enabled) {
        Prefers.put("ai_ad_block", enabled);
    }

    public static boolean hasCaption() {
        return new Intent(Settings.ACTION_CAPTIONING_SETTINGS).resolveActivity(App.get().getPackageManager()) != null;
    }

    // ==================== 播放器配置（委托 PlayerSetting）====================

    public static int getDecode() { return PlayerSetting.getDecode(); }
    public static void putDecode(int decode) { PlayerSetting.putDecode(decode); }
    public static int getRender() { return PlayerSetting.getRender(); }
    public static void putRender(int render) { PlayerSetting.putRender(render); }
    public static int getQuality() { return PlayerSetting.getQuality(); }
    public static void putQuality(int quality) { PlayerSetting.putQuality(quality); }
    public static int getSize() { return PlayerSetting.getSize(); }
    public static void putSize(int size) { PlayerSetting.putSize(size); }
    public static int getViewType(int viewType) { return PlayerSetting.getViewType(viewType); }
    public static void putViewType(int viewType) { PlayerSetting.putViewType(viewType); }
    public static int getScale() { return PlayerSetting.getScale(); }
    public static void putScale(int scale) { PlayerSetting.putScale(scale); }
    public static int getBuffer() { return PlayerSetting.getBuffer(); }
    public static void putBuffer(int buffer) { PlayerSetting.putBuffer(buffer); }
    public static int getBackground() { return PlayerSetting.getBackground(); }
    public static void putBackground(int background) { PlayerSetting.putBackground(background); }
    public static boolean isBackgroundOff() { return PlayerSetting.isBackgroundOff(); }
    public static boolean isBackgroundOn() { return PlayerSetting.isBackgroundOn(); }
    public static boolean isBackgroundPiP() { return PlayerSetting.isBackgroundPiP(); }
    public static boolean isCaption() { return PlayerSetting.isCaption(); }
    public static void putCaption(boolean caption) { PlayerSetting.putCaption(caption); }
    public static boolean isTunnel() { return PlayerSetting.isTunnel(); }
    public static void putTunnel(boolean tunnel) { PlayerSetting.putTunnel(tunnel); }
    public static boolean isAudioPrefer() { return PlayerSetting.isAudioPrefer(); }
    public static void putAudioPrefer(boolean audioPrefer) { PlayerSetting.putAudioPrefer(audioPrefer); }
    public static boolean isPreferAAC() { return PlayerSetting.isPreferAAC(); }
    public static void putPreferAAC(boolean preferAAC) { PlayerSetting.putPreferAAC(preferAAC); }
    public static boolean isAutoRotate() { return PlayerSetting.isAutoRotate(); }
    public static void putAutoRotate(boolean autoRotate) { PlayerSetting.putAutoRotate(autoRotate); }
    public static boolean isDanmakuLoad() { return PlayerSetting.isDanmakuLoad(); }
    public static void putDanmakuLoad(boolean danmakuLoad) { PlayerSetting.putDanmakuLoad(danmakuLoad); }
    public static float getDanmakuSize() { return PlayerSetting.getDanmakuSize(); }
    public static void putDanmakuSize(float size) { PlayerSetting.putDanmakuSize(size); }
    public static boolean isDanmakuShow() { return PlayerSetting.isDanmakuShow(); }
    public static void putDanmakuShow(boolean danmakuShow) { PlayerSetting.putDanmakuShow(danmakuShow); }
    public static boolean isZhuyin() { return PlayerSetting.isZhuyin(); }
    public static void putZhuyin(boolean zhuyin) { PlayerSetting.putZhuyin(zhuyin); }
    public static float getSpeed() { return PlayerSetting.getSpeed(); }
    public static void putSpeed(float speed) { PlayerSetting.putSpeed(speed); }
    public static float getSubtitleTextSize() { return PlayerSetting.getSubtitleTextSize(); }
    public static void putSubtitleTextSize(float value) { PlayerSetting.putSubtitleTextSize(value); }
    public static float getSubtitlePosition() { return PlayerSetting.getSubtitlePosition(); }
    public static void putSubtitlePosition(float value) { PlayerSetting.putSubtitlePosition(value); }
    public static float getThumbnail() { return PlayerSetting.getThumbnail(); }
    public static boolean isAnime4K() { return PlayerSetting.isAnime4K(); }
    public static void putAnime4K(boolean enabled) { PlayerSetting.putAnime4K(enabled); }
    public static int getAnime4KStrength() { return PlayerSetting.getAnime4KStrength(); }
    public static void putAnime4KStrength(int strength) { PlayerSetting.putAnime4KStrength(strength); }

    // ==================== 同步配置（委托 SyncSetting）====================

    public static int getSyncMode() { return SyncSetting.getSyncMode(); }
    public static void putSyncMode(int mode) { SyncSetting.putSyncMode(mode); }
    public static boolean isAutoSync() { return SyncSetting.isAutoSync(); }
    public static void putAutoSync(boolean autoSync) { SyncSetting.putAutoSync(autoSync); }
    public static int getSyncInterval() { return SyncSetting.getSyncInterval(); }
    public static void putSyncInterval(int minutes) { SyncSetting.putSyncInterval(minutes); }
    public static boolean isSyncEnabled() { return SyncSetting.isSyncEnabled(); }
    public static void putSyncEnabled(boolean enabled) { SyncSetting.putSyncEnabled(enabled); }
    public static String getWebDAVUrl() { return SyncSetting.getWebDAVUrl(); }
    public static void putWebDAVUrl(String url) { SyncSetting.putWebDAVUrl(url); }
    public static String getWebDAVUsername() { return SyncSetting.getWebDAVUsername(); }
    public static void putWebDAVUsername(String username) { SyncSetting.putWebDAVUsername(username); }
    public static String getWebDAVPassword() { return SyncSetting.getWebDAVPassword(); }
    public static void putWebDAVPassword(String password) { SyncSetting.putWebDAVPassword(password); }
    public static String getWebDAVSyncMode() { return SyncSetting.getWebDAVSyncMode(); }
    public static void putWebDAVSyncMode(String mode) { SyncSetting.putWebDAVSyncMode(mode); }
    public static String getWebDAVSyncCode() { return SyncSetting.getWebDAVSyncCode(); }
    public static void putWebDAVSyncCode(String code) { SyncSetting.putWebDAVSyncCode(code); }
    public static String getWebDAVPublicUrl() { return SyncSetting.getWebDAVPublicUrl(); }
    public static void putWebDAVPublicUrl(String url) { SyncSetting.putWebDAVPublicUrl(url); }
    public static boolean isWebDAVAutoSync() { return SyncSetting.isWebDAVAutoSync(); }
    public static void putWebDAVAutoSync(boolean autoSync) { SyncSetting.putWebDAVAutoSync(autoSync); }
    public static int getWebDAVSyncInterval() { return SyncSetting.getWebDAVSyncInterval(); }
    public static void putWebDAVSyncInterval(int minutes) { SyncSetting.putWebDAVSyncInterval(minutes); }

    // ==================== 下载配置（委托 DownloadSetting）====================

    public static String getDownloadPath() { return DownloadSetting.getDownloadPath(); }
    public static void putDownloadPath(String path) { DownloadSetting.putDownloadPath(path); }
    public static int getDownloadConcurrent() { return DownloadSetting.getDownloadConcurrent(); }
    public static void putDownloadConcurrent(int count) { DownloadSetting.putDownloadConcurrent(count); }
}
