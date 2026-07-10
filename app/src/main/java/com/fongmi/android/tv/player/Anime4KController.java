package com.fongmi.android.tv.player;

import androidx.media3.common.Effect;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlayer;

import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.player.effect.Anime4KEffect;

import java.util.ArrayList;
import java.util.List;

/**
 * Anime4K 超分控制器
 *
 * 从 Players 中分离的职责：
 * - 根据视频分辨率和设置决定是否应用超分
 * - 动态应用/移除超分效果
 */
public class Anime4KController {

    private final ExoPlayer exoPlayer;
    private VideoSize size;
    private boolean applied;

    public Anime4KController(ExoPlayer exoPlayer) {
        this.exoPlayer = exoPlayer;
        this.applied = false;
    }

    /**
     * 更新视频尺寸，触发超分状态检查
     */
    public void onVideoSizeChanged(VideoSize videoSize) {
        this.size = videoSize;
        update();
    }

    /**
     * 初始化时根据已有分辨率信息决定是否应用超分
     */
    public void init() {
        if (Setting.isAnime4K() && shouldApply()) {
            apply();
        }
    }

    /**
     * 根据当前分辨率和设置动态应用/移除超分
     */
    public void update() {
        if (!Setting.isAnime4K()) {
            if (applied) remove();
            return;
        }
        boolean should = shouldApply();
        if (should && !applied) {
            apply();
        } else if (!should && applied) {
            remove();
        }
    }

    /**
     * 超分仅对 < 720p (宽 < 1280) 视频启用
     */
    private boolean shouldApply() {
        if (size == null) return false;
        return size.width > 0 && size.width < Constant.ANIME4K_WIDTH_THRESHOLD;
    }

    private void apply() {
        if (exoPlayer == null) return;
        List<Effect> effects = new ArrayList<>();
        effects.add(new Anime4KEffect(Setting.getAnime4KStrength()));
        exoPlayer.setVideoEffects(effects);
        applied = true;
    }

    private void remove() {
        if (exoPlayer == null) return;
        exoPlayer.setVideoEffects(new ArrayList<>());
        applied = false;
    }

    public boolean isApplied() {
        return applied;
    }
}
