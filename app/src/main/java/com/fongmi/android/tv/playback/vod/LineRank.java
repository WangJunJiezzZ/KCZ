package com.fongmi.android.tv.playback.vod;

import com.fongmi.android.tv.bean.Flag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 二次开发：按测速结果给线路排序。
 * 规则：能流畅播的线路里画质高的排前面 → 没测的线路（如网盘）保持原顺序 → 卡的线路按「速度/码率」排 → 失败的放最后。
 */
public final class LineRank {

    /** 下载速度达到码率的 1.3 倍才算流畅（留余量应对网速波动） */
    private static final double SMOOTH_RATIO = 1.3;
    /** 算不出码率时，速度达到 4 Mbps 就当流畅 */
    private static final long SMOOTH_FALLBACK_KBPS = 4000;

    private static final int SMOOTH = 0;
    private static final int UNKNOWN = 1;
    private static final int SLOW = 2;
    private static final int FAILED = 3;

    private LineRank() {
    }

    /** 原地重排 flags，返回是否有变化 */
    public static boolean sort(List<Flag> flags, Map<String, LineProbe.Result> results) {
        List<Flag> before = new ArrayList<>(flags);
        Comparator<Flag> comparator = Comparator
                .comparingInt((Flag flag) -> group(results.get(flag.getFlag())))
                .thenComparing((a, b) -> compareInGroup(results.get(a.getFlag()), results.get(b.getFlag())));
        flags.sort(comparator); // List.sort 是稳定排序，同组同分保持源给的顺序
        return !before.equals(flags);
    }

    /** 线路按钮上的小标签，如「1080P」「4K · 卡」「失败」；没测的线路返回空串 */
    public static String badge(LineProbe.Result result) {
        if (result == null) return "";
        if (!result.ok()) return "失败";
        String quality = quality(result.height());
        if (group(result) == SLOW) return quality.isEmpty() ? "卡" : quality + " · 卡";
        return quality;
    }

    static int group(LineProbe.Result result) {
        if (result == null) return UNKNOWN;
        if (!result.ok()) return FAILED;
        return isSmooth(result) ? SMOOTH : SLOW;
    }

    static boolean isSmooth(LineProbe.Result result) {
        if (result.bitrateKbps() > 0) return result.speedKbps() >= result.bitrateKbps() * SMOOTH_RATIO;
        return result.speedKbps() >= SMOOTH_FALLBACK_KBPS;
    }

    private static int compareInGroup(LineProbe.Result a, LineProbe.Result b) {
        if (a == null || b == null || !a.ok() || !b.ok()) return 0;
        if (group(a) == SLOW) return Double.compare(ratio(b), ratio(a));
        int byHeight = Integer.compare(estimatedHeight(b), estimatedHeight(a));
        if (byHeight != 0) return byHeight;
        int byBitrate = Long.compare(b.bitrateKbps(), a.bitrateKbps());
        if (byBitrate != 0) return byBitrate;
        return Long.compare(b.speedKbps(), a.speedKbps());
    }

    private static double ratio(LineProbe.Result result) {
        long bitrate = result.bitrateKbps() > 0 ? result.bitrateKbps() : SMOOTH_FALLBACK_KBPS;
        return (double) result.speedKbps() / bitrate;
    }

    /** m3u8 没写分辨率时，用码率粗估档位，只用于排序，不显示 */
    static int estimatedHeight(LineProbe.Result result) {
        if (result.height() > 0) return result.height();
        long kbps = result.bitrateKbps();
        if (kbps >= 8000) return 2160;
        if (kbps >= 2500) return 1080;
        if (kbps >= 1200) return 720;
        return kbps > 0 ? 480 : 0;
    }

    private static String quality(int height) {
        if (height >= 2000) return "4K";
        if (height >= 1400) return "2K";
        if (height >= 1000) return "1080P";
        if (height >= 700) return "720P";
        if (height > 0) return height + "P";
        return "";
    }
}
