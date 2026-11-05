package com.fongmi.android.tv.player.effect.compat;

/**
 * 二次开发兼容层：上游依赖的 androidx.media3.common.audio.AudioChannelMode 未在公开的 FongMi/media 中发布，
 * 这里按 TV 的调用方式补齐。
 */
public final class AudioChannelMode {

    public static final int AUTO = 0;
    public static final int STEREO = 1;
    public static final int MONO = 2;
    public static final int REVERSE = 3;

    private AudioChannelMode() {
    }

    public static int clamp(int mode) {
        return mode >= AUTO && mode <= REVERSE ? mode : AUTO;
    }

    public static boolean isAuto(int mode) {
        return clamp(mode) == AUTO;
    }

    /** 声道混合至少需要 2 个、最多 8 个声道。 */
    public static boolean isAvailable(int channelCount) {
        return channelCount >= 2 && channelCount <= 8;
    }

    public static boolean isAvailable(int mode, int channelCount) {
        return isAvailable(channelCount) && clamp(mode) == mode;
    }

    /** 单声道输出时左右平衡没有意义。 */
    public static boolean canBalance(int mode) {
        return clamp(mode) != MONO;
    }

    public static int resolve(int mode, int channelCount) {
        return isAvailable(channelCount) ? clamp(mode) : AUTO;
    }
}
