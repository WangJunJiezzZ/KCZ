package com.fongmi.android.tv.player.effect.compat;

import android.content.Context;

import androidx.media3.exoplayer.ExoPlayer;

/**
 * 二次开发兼容层：替代未公开的 androidx.media3.exoplayer.audio.AudioEqualizerController。
 * ExoPlayer 侧音效暂不支持：apply 返回 false，界面会提示"无法应用音效"。
 */
public final class AudioEqualizerController {

    public AudioEqualizerController(Context context) {
    }

    public boolean apply(ExoPlayer player, AudioEffectParameters parameters) {
        return false;
    }

    public void release() {
    }
}
