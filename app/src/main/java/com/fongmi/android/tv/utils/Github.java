package com.fongmi.android.tv.utils;

import android.text.TextUtils;

import com.fongmi.android.tv.BuildConfig;

/**
 * 二次开发：自己的更新渠道（地址在 gradle.properties 的 customUpdateJson / customUpdateApk）。
 * 版本信息 JSON：{"name": "5.6.1.2", "code": 56102, "desc": "更新说明", "apk": "可选，覆盖 APK 下载目录", "url": "可选，完整下载直链（优先）"}
 */
public class Github {

    public static String getJson(String name) {
        return BuildConfig.UPDATE_JSON + "/" + name + ".json";
    }

    public static String getApk(String name) {
        return getApk(null, name);
    }

    /** base 为空时用打包时配置的默认下载地址 */
    public static String getApk(String base, String name) {
        String root = TextUtils.isEmpty(base) ? BuildConfig.UPDATE_APK : base;
        return (root.endsWith("/") ? root : root + "/") + name + ".apk";
    }
}
