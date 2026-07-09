package com.fongmi.android.tv.player.effect;

import android.content.Context;

import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.effect.GlEffect;
import androidx.media3.effect.GlShaderProgram;

/**
 * Anime4K 超分 Effect
 * 实现 GlEffect 接口，返回 Anime4KShaderProgram
 * 通过 ExoPlayer.setVideoEffects() 应用到视频画面
 * strength: 0=低 1=中 2=高
 */
public final class Anime4KEffect implements GlEffect {

    private final int strength;

    public Anime4KEffect(int strength) {
        this.strength = strength;
    }

    @Override
    public GlShaderProgram toGlShaderProgram(Context context, boolean useHighPrecisionColorComponents) throws VideoFrameProcessingException {
        return new Anime4KShaderProgram(context, useHighPrecisionColorComponents, strength);
    }

    @Override
    public boolean isNoOp(int inputWidth, int inputHeight) {
        return false;
    }
}
