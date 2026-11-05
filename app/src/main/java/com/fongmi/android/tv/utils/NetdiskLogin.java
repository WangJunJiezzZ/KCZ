package com.fongmi.android.tv.utils;

import android.text.TextUtils;

import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Class;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 二次开发：网盘没登录时，直接调出配置里「配置中心」站的扫码登录。
 * 不写死站名，按规则找：名字带「配置」的站 → 分类名带网盘名（如「夸克网盘」）→ 条目名带「扫码」且是 action 条目。
 * 二维码由站源自己弹出；扫完后这个条目的备注（如「未扫码」）会变，用来判断登录成功。
 */
public final class NetdiskLogin {

    /**
     * 网盘名 → 各种写法。第一个是标准名；配置中心常故意用谐音（如「跨壳网盘设置」= 夸克、「优熙」= UC），
     * 英文小写别名只用于报错文字，不用于线路名（免得 "source" 误判成 UC）。
     */
    private static final String[][] DISKS = {
            {"夸克", "跨壳", "quark"},
            {"UC", "优熙", "优汐", "优夕", "uc"},
            {"百度", "baidu"},
            {"阿里", "阿狸", "aliyun", "alipan"},
            {"115"},
            {"迅雷", "迅蕾", "thunder", "xunlei"},
            {"天翼", "天逸", "189"},
            {"移动", "YD", "139"},
            {"123"},
    };
    /** 登录条目里要排除的（清除、注册、手动输入等） */
    private static final String[] NOT_LOGIN = {"清除", "clear", "注册", "register", "手动", "manual", "退出", "logout"};
    private static final String[] NEED_LOGIN = {"cookie", "登录", "扫码", "token", "auth"};

    /** 网盘名 → 扫码入口；找到一次就缓存，换配置后会重新找 */
    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();
    private static String cacheConfig = "";

    private NetdiskLogin() {
    }

    /** vodId：扫码条目的 id（配置中心的条目大多没有 action 字段，只有 id）；action 可能为空 */
    public record Entry(String siteKey, String typeId, String vodId, String action, String remark) {
    }

    /** 从报错信息判断是不是「某个网盘没登录」，是就返回网盘名（如「夸克」），否则 null */
    public static String detect(String msg) {
        if (TextUtils.isEmpty(msg)) return null;
        String lower = msg.toLowerCase(Locale.ROOT);
        boolean needLogin = false;
        for (String word : NEED_LOGIN) if (lower.contains(word)) needLogin = true;
        if (!needLogin) return null;
        for (String[] disk : DISKS) for (String alias : disk) if (lower.contains(alias.toLowerCase(Locale.ROOT))) return disk[0];
        return null;
    }

    /**
     * 从线路名认网盘（如「夸克最高急速」→「夸克」）。只用中文名和大写 UC，
     * 不用英文小写别名，免得 "source" 之类的词误判成 UC。
     */
    public static String diskOfFlag(String flag) {
        if (TextUtils.isEmpty(flag)) return null;
        for (String[] disk : DISKS) if (containsName(flag, disk)) return disk[0];
        return null;
    }

    /** 中文名/谐音和大写缩写（UC、YD）按原样匹配，不用英文小写别名 */
    private static boolean containsName(String text, String[] disk) {
        for (String alias : disk) {
            boolean lowerAscii = alias.chars().allMatch(c -> c < 128) && !alias.equals(alias.toUpperCase(Locale.ROOT));
            if (!lowerAscii && text.contains(alias)) return true;
        }
        return false;
    }

    /** 登录条目打分：扫码 3 > 登录/login 2 > 账号/设置/cookie 1；清除、注册等为 0（不要） */
    private static int loginScore(Vod vod) {
        String name = vod.getName();
        String text = (name + " " + vod.getId()).toLowerCase(Locale.ROOT);
        for (String word : NOT_LOGIN) if (text.contains(word)) return 0;
        if (name.contains("扫码")) return 3;
        if (name.contains("登录") || text.contains("login")) return 2;
        if (name.contains("账号") || name.contains("设置") || text.contains("cookie")) return 1;
        return 0;
    }

    /** 找扫码入口（会请求网络，需在后台线程调用）；找不到返回 null */
    public static Entry find(String disk) throws Exception {
        String config = VodConfig.getUrl();
        if (!config.equals(cacheConfig)) {
            CACHE.clear();
            cacheConfig = config;
        }
        Entry cached = CACHE.get(disk);
        if (cached != null) return refresh(cached);
        String[] names = names(disk);
        for (Site site : VodConfig.get().getSites()) {
            if (!site.getName().contains("配置")) continue;
            for (Class type : SiteApi.homeContent(site).getTypes()) {
                if (!containsName(type.getTypeName(), names)) continue;
                Vod vod = findQrItem(site.getKey(), type.getTypeId(), null);
                if (vod == null) continue;
                Entry entry = new Entry(site.getKey(), type.getTypeId(), vod.getId(), vod.getAction(), vod.getRemarks());
                CACHE.put(disk, entry);
                return entry;
            }
        }
        return null;
    }

    private static String[] names(String disk) {
        for (String[] item : DISKS) if (item[0].equals(disk)) return item;
        return new String[]{disk};
    }

    /** 当前备注，用来和打开二维码前的备注比较；查不到返回 null */
    public static String status(Entry entry) throws Exception {
        Vod vod = findQrItem(entry.siteKey(), entry.typeId(), entry.vodId());
        return vod == null ? null : vod.getRemarks();
    }

    /** 弹出二维码（站源自己画对话框），后台线程调用：有 action 走 action，否则按点击条目的方式请求详情 */
    public static void showQr(Entry entry) throws Exception {
        if (!TextUtils.isEmpty(entry.action())) SiteApi.action(entry.siteKey(), entry.action());
        else SiteApi.detailContent(entry.siteKey(), entry.vodId());
    }

    private static Entry refresh(Entry cached) throws Exception {
        String remark = status(cached);
        return remark == null ? cached : new Entry(cached.siteKey(), cached.typeId(), cached.vodId(), cached.action(), remark);
    }

    /** vodId 为空时按 loginScore 挑最像「登录」的条目；否则按 id 找回同一个条目（查备注用） */
    private static Vod findQrItem(String key, String typeId, String vodId) throws Exception {
        Result result = SiteApi.categoryContent(key, typeId, "1", false, new HashMap<>());
        Vod best = null;
        int bestScore = 0;
        for (Vod vod : result.getList()) {
            if (vodId != null) {
                if (vodId.equals(vod.getId())) return vod;
                continue;
            }
            int score = loginScore(vod);
            if (score > bestScore) {
                best = vod;
                bestScore = score;
            }
        }
        return best;
    }
}
