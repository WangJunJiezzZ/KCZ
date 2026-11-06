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
    private static final String IMAGE = "https://image.tmdb.org/t/p/w1280";
    /** 片名 → 剧照地址；"" 表示查过但没有 */
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    private Backdrop() {
    }

    public static boolean enabled() {
        return !TextUtils.isEmpty(BuildConfig.TMDB_KEY);
    }

    /** 异步查询，结果（可能为空字符串）在主线程回调 */
    public static void find(String name, Consumer<String> callback) {
        String key = clean(name);
        if (!enabled() || key.isEmpty()) {
            callback.accept("");
            return;
        }
        String cached = CACHE.get(key);
        if (cached != null) {
            callback.accept(cached);
            return;
        }
        EXECUTOR.submit(() -> {
            String url = "";
            String used = key;
            // 二次开发：一种写法查不到就换下一种（去掉 -副标题、冒号后半段、3D、年份等），提高命中率
            for (String query : queries(key)) {
                used = query;
                url = search(query);
                if (!url.isEmpty()) break;
            }
            CACHE.put(key, url);
            Log.d(TAG, key + (used.equals(key) ? "" : " (用「" + used + "」)") + " -> " + (url.isEmpty() ? "(无剧照，用海报)" : url));
            String result = url;
            App.post(() -> callback.accept(result));
        });
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

    private static String search(String query) {
        try {
            String url = API + "?api_key=" + BuildConfig.TMDB_KEY + "&language=zh-CN&include_adult=false&query=" + Uri.encode(query);
            JSONArray results = new JSONObject(OkHttp.string(url)).optJSONArray("results");
            if (results == null) return "";
            for (int i = 0; i < results.length(); i++) {
                JSONObject item = results.optJSONObject(i);
                if (item == null || "person".equals(item.optString("media_type"))) continue;
                String path = item.optString("backdrop_path");
                if (!TextUtils.isEmpty(path) && !"null".equals(path)) return IMAGE + path;
            }
        } catch (Exception e) {
            Log.w(TAG, "查询失败：" + query, e);
        }
        return "";
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
