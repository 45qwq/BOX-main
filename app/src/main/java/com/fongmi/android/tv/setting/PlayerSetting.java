package com.fongmi.android.tv.setting;

import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.player.Players;
import com.github.catvod.utils.Prefers;

/**
 * 播放器相关配置
 */
public class PlayerSetting {

    public static int getDecode() {
        return Prefers.getInt("decode", Players.AUTO);
    }

    public static void putDecode(int decode) {
        Prefers.put("decode", decode);
    }

    public static int getRender() {
        return Prefers.getInt("render", 0);
    }

    public static void putRender(int render) {
        Prefers.put("render", render);
    }

    public static int getQuality() {
        return Prefers.getInt("quality", 2);
    }

    public static void putQuality(int quality) {
        Prefers.put("quality", quality);
    }

    public static int getSize() {
        return Prefers.getInt("size", 2);
    }

    public static void putSize(int size) {
        Prefers.put("size", size);
    }

    public static int getViewType(int viewType) {
        return Prefers.getInt("viewType", viewType);
    }

    public static void putViewType(int viewType) {
        Prefers.put("viewType", viewType);
    }

    public static int getScale() {
        return Prefers.getInt("scale");
    }

    public static void putScale(int scale) {
        Prefers.put("scale", scale);
    }

    public static int getBuffer() {
        return Math.min(Math.max(Prefers.getInt("buffer"), 1), 10);
    }

    public static void putBuffer(int buffer) {
        Prefers.put("buffer", buffer);
    }

    public static int getBackground() {
        return Prefers.getInt("background", 0);
    }

    public static void putBackground(int background) {
        Prefers.put("background", background);
    }

    public static boolean isBackgroundOff() {
        return getBackground() == 0;
    }

    public static boolean isBackgroundOn() {
        return getBackground() == 1 || getBackground() == 2;
    }

    public static boolean isBackgroundPiP() {
        return getBackground() == 2;
    }

    public static boolean isCaption() {
        return Prefers.getBoolean("caption");
    }

    public static void putCaption(boolean caption) {
        Prefers.put("caption", caption);
    }

    public static boolean isTunnel() {
        return Prefers.getBoolean("tunnel");
    }

    public static void putTunnel(boolean tunnel) {
        Prefers.put("tunnel", tunnel);
    }

    public static boolean isAudioPrefer() {
        return Prefers.getBoolean("audio_prefer");
    }

    public static void putAudioPrefer(boolean audioPrefer) {
        Prefers.put("audio_prefer", audioPrefer);
    }

    public static boolean isPreferAAC() {
        return Prefers.getBoolean("prefer_aac");
    }

    public static void putPreferAAC(boolean preferAAC) {
        Prefers.put("prefer_aac", preferAAC);
    }

    public static boolean isDanmakuLoad() {
        return Prefers.getBoolean("danmaku_load");
    }

    public static void putDanmakuLoad(boolean danmakuLoad) {
        Prefers.put("danmaku_load", danmakuLoad);
    }

    public static float getDanmakuSize() {
        return Prefers.getFloat("danmaku_size", 1.0f);
    }

    public static void putDanmakuSize(float size) {
        Prefers.put("danmaku_size", size);
    }

    public static boolean isDanmakuShow() {
        return Prefers.getBoolean("danmaku_show");
    }

    public static void putDanmakuShow(boolean danmakuShow) {
        Prefers.put("danmaku_show", danmakuShow);
    }

    public static boolean isZhuyin() {
        return Prefers.getBoolean("zhuyin");
    }

    public static void putZhuyin(boolean zhuyin) {
        Prefers.put("zhuyin", zhuyin);
    }

    public static float getSpeed() {
        return Math.min(Math.max(Prefers.getFloat("speed", 3), 2), 5);
    }

    public static void putSpeed(float speed) {
        Prefers.put("speed", speed);
    }

    public static float getSubtitleTextSize() {
        return Prefers.getFloat("subtitle_text_size");
    }

    public static void putSubtitleTextSize(float value) {
        Prefers.put("subtitle_text_size", value);
    }

    public static float getSubtitlePosition() {
        return Prefers.getFloat("subtitle_position");
    }

    public static void putSubtitlePosition(float value) {
        Prefers.put("subtitle_position", value);
    }

    public static float getThumbnail() {
        return 0.3f * getQuality() + 0.4f;
    }

    public static boolean isAnime4K() {
        return Prefers.getBoolean("anime4k", false);
    }

    public static void putAnime4K(boolean enabled) {
        Prefers.put("anime4k", enabled);
    }

    public static int getAnime4KStrength() {
        return Prefers.getInt("anime4k_strength", 1);
    }

    public static void putAnime4KStrength(int strength) {
        Prefers.put("anime4k_strength", strength);
    }
}
