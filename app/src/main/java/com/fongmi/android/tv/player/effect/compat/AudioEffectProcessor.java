package com.fongmi.android.tv.player.effect.compat;

import androidx.media3.common.audio.BaseAudioProcessor;

import java.nio.ByteBuffer;

/**
 * 二次开发兼容层：替代未公开的 androidx.media3.common.audio.AudioEffectProcessor。
 * 原实现的音频处理代码没有公开，这里只做直通（不改动声音），保证 ExoPlayer 正常播放。
 */
public final class AudioEffectProcessor extends BaseAudioProcessor {

    @Override
    protected AudioFormat onConfigure(AudioFormat inputAudioFormat) {
        return AudioFormat.NOT_SET;
    }

    @Override
    public void queueInput(ByteBuffer inputBuffer) {
        throw new IllegalStateException("AudioEffectProcessor is inactive");
    }

    public void setParameters(AudioEffectParameters parameters, boolean softwareEqualizer) {
    }

    public void resetParameters() {
    }
}
