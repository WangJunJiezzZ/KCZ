package com.fongmi.android.tv.player.effect.compat;

/**
 * 二次开发兼容层：替代未公开的 androidx.media3.common.audio.AudioEffectParameters。
 * 只保存音效参数；mpv 播放器据此生成音频滤镜（MpvAudioEffectFilter），ExoPlayer 侧暂不支持音效。
 */
public final class AudioEffectParameters {

    public static final AudioEffectParameters DEFAULT = new Builder().build();

    private final int[] frequencies;
    private final short[] levels;
    private final float stability;
    private final int boostMillibels;
    private final int preampMillibels;
    private final int centerGainMillibels;
    private final float balance;
    private final int channelMode;
    private final boolean loudness;

    private AudioEffectParameters(Builder b) {
        this.frequencies = b.frequencies;
        this.levels = b.levels;
        this.stability = b.stability;
        this.boostMillibels = b.boostMillibels;
        this.preampMillibels = b.preampMillibels;
        this.centerGainMillibels = b.centerGainMillibels;
        this.balance = b.balance;
        this.channelMode = b.channelMode;
        this.loudness = b.loudness;
    }

    /** 有前置中置声道（3 声道及以上）才能单独调中置音量。 */
    public static boolean isCenterGainAvailable(int channelCount) {
        return channelCount >= 3;
    }

    public boolean hasBands() {
        return levels.length > 0;
    }

    public boolean hasCenterGain(int channelCount) {
        return centerGainMillibels != 0 && isCenterGainAvailable(channelCount);
    }

    public boolean hasEffect() {
        return hasBands() || stability > 0 || boostMillibels != 0 || centerGainMillibels != 0 || balance != 0 || !AudioChannelMode.isAuto(channelMode) || loudness;
    }

    /** 提升了音量（增益、正向均衡、中置增益）时加限幅器，防止爆音。 */
    public boolean shouldLimitOutput(int channelCount) {
        if (boostMillibels > 0 || (hasCenterGain(channelCount) && centerGainMillibels > 0)) return true;
        for (short level : levels) if (level + preampMillibels > 0) return true;
        return false;
    }

    public float getCenterGainFactor() {
        return (float) Math.pow(10.0, centerGainMillibels / 2000.0);
    }

    public short[] getEqualizerLevelsMillibels() {
        return levels;
    }

    public int[] getEqualizerFrequencies() {
        return frequencies;
    }

    public float getStability() {
        return stability;
    }

    public int getBoostMillibels() {
        return boostMillibels;
    }

    public int getPreampMillibels() {
        return preampMillibels;
    }

    public float getBalance() {
        return balance;
    }

    public int getChannelMode() {
        return channelMode;
    }

    public boolean isLoudnessEnabled() {
        return loudness;
    }

    public static final class Builder {

        private int[] frequencies = new int[0];
        private short[] levels = new short[0];
        private float stability;
        private int boostMillibels;
        private int preampMillibels;
        private int centerGainMillibels;
        private float balance;
        private int channelMode = AudioChannelMode.AUTO;
        private boolean loudness;

        public Builder setEqualizerBands(int[] frequencies, short[] levels) {
            this.frequencies = frequencies;
            this.levels = levels;
            return this;
        }

        public Builder setStability(float stability) {
            this.stability = stability;
            return this;
        }

        public Builder setBoostMillibels(int boostMillibels) {
            this.boostMillibels = boostMillibels;
            return this;
        }

        public Builder setPreampMillibels(int preampMillibels) {
            this.preampMillibels = preampMillibels;
            return this;
        }

        public Builder setCenterGainMillibels(int centerGainMillibels) {
            this.centerGainMillibels = centerGainMillibels;
            return this;
        }

        public Builder setBalance(float balance) {
            this.balance = balance;
            return this;
        }

        public Builder setChannelMode(int channelMode) {
            this.channelMode = AudioChannelMode.clamp(channelMode);
            return this;
        }

        public Builder setLoudnessEnabled(boolean loudness) {
            this.loudness = loudness;
            return this;
        }

        public AudioEffectParameters build() {
            return new AudioEffectParameters(this);
        }
    }
}
