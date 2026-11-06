package com.fongmi.android.tv.utils;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.BuildConfig;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * 二次开发：按片名去 TMDB 查高清横版剧照，作为首页 / 详情页的大图背景。
 * - 国内 api.themoviedb.org 被 DNS 污染，改用备用域名 api.tmdb.org；图片走 image.tmdb.org（国内可访问）
 * - API 密钥在 local.properties 的 tmdbKey（不提交到 GitHub）；没配置时直接返回空，调用方退回用海报
 */
public final class Backdrop {

    private static final String API = "https://api.tmdb.org/3/search/multi";
    /**
     * 二次开发：一律用原图（通常 1920 或 3840 宽）。背景已改成全屏 16:9，1280 宽的图在 1080P 上也要放大 1.5 倍、发软；
     * Glide 会按控件尺寸缩小解码，不会多占内存，只是下载稍大（有预加载兜着）
     */
    private static final String IMAGE = "https://image.tmdb.org/t/p/original";
    /** 片名 → 剧照地址；"" 表示查过但没有 */
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    private Backdrop() {
    }

    public static boolean enabled() {
        return !TextUtils.isEmpty(BuildConfig.TMDB_KEY);
    }

    /** 异步查询，结果（可能为空字符串）在主线程回调 */
    public static void find(String name, Consumer<String> callback) {
        find(name, "", callback);
    }

    /** year：源给的年份（如 "2024"），用来在同名结果里挑对的那一部；没有就传空 */
    public static void find(String name, String year, Consumer<String> callback) {
        String key = clean(name);
        int y = parseYear(year);
        if (!enabled() || key.isEmpty()) {
            callback.accept("");
            return;
        }
        String cacheKey = y > 0 ? key + "#" + y : key;
        String cached = CACHE.get(cacheKey);
        if (cached != null) {
            callback.accept(cached);
            return;
        }
        EXECUTOR.submit(() -> {
            String result = lookup(key, y, cacheKey);
            App.post(() -> callback.accept(result));
        });
    }

    /**
     * 二次开发：预取（焦点旁边几部片子的剧照）。走单独的单线程低优先级通道，不和正在看的那部抢线程；
     * 查到了在主线程回调 onUrl（只在有剧照时回调），调用方再让 Glide 预下载图片。
     */
    public static void prefetch(String name, String year, Consumer<String> onUrl) {
        String key = clean(name);
        int y = parseYear(year);
        if (!enabled() || key.isEmpty()) return;
        String cacheKey = y > 0 ? key + "#" + y : key;
        String cached = CACHE.get(cacheKey);
        if (cached != null) {
            if (!cached.isEmpty()) onUrl.accept(cached);
            return;
        }
        if (!PENDING.add(cacheKey)) return;
        PREFETCH.submit(() -> {
            try {
                String url = CACHE.containsKey(cacheKey) ? CACHE.get(cacheKey) : lookup(key, y, cacheKey);
                if (!url.isEmpty()) App.post(() -> onUrl.accept(url));
            } finally {
                PENDING.remove(cacheKey);
            }
        });
    }

    /** 查询并写缓存（后台线程调用）：一种写法查不到就换下一种（去掉 -副标题、冒号后半段、3D、年份等） */
    private static String lookup(String key, int y, String cacheKey) {
        String url = "";
        String used = key;
        for (String query : queries(key)) {
            used = query;
            url = search(query, y);
            if (!url.isEmpty()) break;
        }
        CACHE.put(cacheKey, url);
        Log.d(TAG, key + (y > 0 ? "(" + y + ")" : "") + (used.equals(key) ? "" : " (用「" + used + "」)") + " -> " + (url.isEmpty() ? "(无剧照，用海报)" : url));
        return url;
    }

    private static final java.util.Set<String> PENDING = ConcurrentHashMap.newKeySet();
    private static final ExecutorService PREFETCH = Executors.newSingleThreadExecutor();

    /** 取出 4 位年份（"2024"、"2024-05"、"2024年" 都行），没有返回 0 */
    static int parseYear(String text) {
        if (TextUtils.isEmpty(text)) return 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(19|20)\\d{2}").matcher(text);
        return m.find() ? Integer.parseInt(m.group()) : 0;
    }

    /**
     * 依次尝试的查询写法，最多 3 种：
     * 原样（已 clean）→ 去掉「-副标题」「：副标题」「3D」「末尾年份」→ 只取第一个空格前的主名。
     * 例：「死神千年血战篇-祸进谭-动漫」→「死神千年血战篇」；「打歌2026」→「打歌」；「逆天邪神3D」→「逆天邪神」
     */
    static List<String> queries(String key) {
        LinkedHashSet<String> items = new LinkedHashSet<>();
        items.add(key);
        String shorter = key.replaceAll("\\s*[-—–].*$", "")
                .replaceAll("\\s*[:：].*$", "")
                .replaceAll("(?i)3D", "")
                // 末尾的版本/语言后缀（中文之间没有单词边界，clean 里的 \b 去不掉，在这里按结尾去）
                .replaceAll("(国语|粤语|国粤|中字|普通话|动漫版|动画版|真人版|未删减|加更版|特别版|完整版|精编版|TV版)+$", "")
                .replaceAll("(19|20)\\d{2}$", "")
                .replaceAll("\\s+", " ").trim();
        if (shorter.length() >= 2) items.add(shorter);
        int space = shorter.indexOf(' ');
        if (space >= 2) items.add(shorter.substring(0, space));
        List<String> list = new ArrayList<>(items);
        return list.size() > 3 ? list.subList(0, 3) : list;
    }

    private static final String TAG = "Backdrop";
    /** 独立线程池：App 公共任务池只有 5 个线程，常被配置加载 / 爬虫占满，剧照查询排不上队 */
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);

    /**
     * 在 TMDB 结果里挑一张剧照。二次开发：不再直接取第一个有剧照的结果，而是打分：
     * 片名完全一致 +2；年份一致 +3、差一年 +2（剧集首播和源给的年份常差一年）；年份差太多 -3。
     * 同分保持 TMDB 原顺序（它本身按热度排）。解决同名片（如不同年代的同名电影/剧）拿错剧照。
     */
    private static String search(String query, int year) {
        try {
            String url = API + "?api_key=" + BuildConfig.TMDB_KEY + "&language=zh-CN&include_adult=false&query=" + Uri.encode(query);
            JSONArray results = new JSONObject(OkHttp.string(url)).optJSONArray("results");
            if (results == null) return "";
            String best = "";
            int bestScore = Integer.MIN_VALUE;
            for (int i = 0; i < results.length(); i++) {
                JSONObject item = results.optJSONObject(i);
                if (item == null || "person".equals(item.optString("media_type"))) continue;
                String path = item.optString("backdrop_path");
                if (TextUtils.isEmpty(path) || "null".equals(path)) continue;
                int score = score(item, query, year);
                if (score > bestScore) {
                    best = IMAGE + path;
                    bestScore = score;
                }
            }
            return best;
        } catch (Exception e) {
            Log.w(TAG, "查询失败：" + query, e);
        }
        return "";
    }

    static int score(JSONObject item, String query, int year) {
        int score = 0;
        String title = item.optString("title", item.optString("name"));
        String original = item.optString("original_title", item.optString("original_name"));
        if (query.equals(title) || query.equals(original)) score += 2;
        if (year > 0) {
            int itemYear = parseYear(item.optString("release_date", item.optString("first_air_date")));
            if (itemYear > 0) {
                int diff = Math.abs(itemYear - year);
                if (diff == 0) score += 3;
                else if (diff == 1) score += 2;
                else if (diff > 3) score -= 3;
            }
        }
        return score;
    }

    /** 去掉"第二季""4K""(2024)""【国语】"这类后缀，提高命中率 */
    static String clean(String name) {
        if (name == null) return "";
        return name.replaceAll("[\\[【(（][^\\]】)）]*[\\]】)）]", " ")
                .replaceAll("第[一二三四五六七八九十0-9]+[季部]", " ")
                .replaceAll("(?i)\\b(4K|1080P|HD|蓝光|国语|粤语|中字)\\b", " ")
                .replaceAll("\\s+", " ").trim();
    }
}
