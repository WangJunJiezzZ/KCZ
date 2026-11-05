package com.fongmi.android.tv.player.effect.video;

import androidx.media3.exoplayer.ExoPlayer;

import java.util.List;

/**
 * 二次开发：上游的 ColorToneAdjustEffect / DetailAdjustEffect / VideoAdjustment 未在公开的 FongMi/media 中发布，
 * ExoPlayer 下的画面调节暂不可用（ExoPlayerEffect.supportsVideoEffect 返回 false），这里只保留清除逻辑。
 */
public final class ExoVideoEffectController {

    private boolean configured;

    public void apply(ExoPlayer player, VideoEffectProfile profile) {
        clear(player);
    }

    public void clear(ExoPlayer player) {
        if (!configured) return;
        player.setVideoEffects(List.of());
        configured = false;
    }
}
