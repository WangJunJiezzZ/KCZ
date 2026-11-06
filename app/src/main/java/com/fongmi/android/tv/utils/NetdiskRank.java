package com.fongmi.android.tv.utils;

import android.text.TextUtils;

import com.fongmi.android.tv.bean.Flag;
import com.github.catvod.utils.Prefers;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 二次开发：网盘线路排序。
 * 1. 能用的网盘（播放成功过 / 在本 App 扫码登录成功过，记在本地）排最前 → 普通线路 → 没登录过的网盘排最后；
 * 2. 同一网盘内：原画 > 最高急速 > 急速 > 其他（会员不限速，原画画质最好）。
 * 例：站源把「百度原画」排第一而百度没登录，打开就会先碰到它去弹扫码；排序后直接从能用的「夸克原画」开播。
 */
public final class NetdiskRank {

    private static final String KEY = "netdisk_ok";

    private NetdiskRank() {
    }

    private static Set<String> load() {
        Set<String> items = new LinkedHashSet<>();
        String value = Prefers.getString(KEY);
        if (!TextUtils.isEmpty(value)) for (String item : value.split(",")) if (!item.isEmpty()) items.add(item);
        return items;
    }

    private static void save(Set<String> items) {
        Prefers.put(KEY, TextUtils.join(",", items));
    }

    /** 该网盘能用（播放成功 / 扫码登录成功）时调用 */
    public static synchronized void markOk(String disk) {
        if (TextUtils.isEmpty(disk)) return;
        Set<String> items = load();
        if (items.add(disk)) save(items);
    }

    /** 该网盘明确报「没登录 / Cookie 失效」时调用 */
    public static synchronized void markBad(String disk) {
        if (TextUtils.isEmpty(disk)) return;
        Set<String> items = load();
        if (items.remove(disk)) save(items);
    }

    public static synchronized boolean isOk(String disk) {
        return !TextUtils.isEmpty(disk) && load().contains(disk);
    }

    /** 0 = 能用的网盘，1 = 普通线路，2 = 没登录过的网盘 */
    public static int rank(Flag flag) {
        String disk = NetdiskLogin.diskOfFlag(flag.getFlag());
        if (disk == null) return 1;
        return isOk(disk) ? 0 : 2;
    }

    /** 网盘线路画质：原画 3 > 最高急速 2 > 急速 1 > 其他 0；普通线路返回 0 */
    public static int quality(Flag flag) {
        String name = flag.getFlag();
        if (NetdiskLogin.diskOfFlag(name) == null) return 0;
        if (name.contains("原画")) return 3;
        if (name.contains("最高")) return 2;
        if (name.contains("急速") || name.contains("极速")) return 1;
        return 0;
    }

    /** 只按网盘规则排（稳定排序，普通线路之间保持原顺序） */
    public static void sort(List<Flag> flags) {
        flags.sort(comparator());
    }

    public static Comparator<Flag> comparator() {
        return Comparator.comparingInt(NetdiskRank::rank).thenComparing(Comparator.comparingInt(NetdiskRank::quality).reversed());
    }
}
