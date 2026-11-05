package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.BuildConfig;

public class Github {

    // 二次开发：更新源指向自己的仓库（customUpdateRepo）；仓库里没有 apk/*.json 时 App 不会提示更新
    public static final String URL = "https://raw.githubusercontent.com/" + BuildConfig.UPDATE_REPO + "/release";

    private static String getUrl(String name) {
        return URL + "/apk/" + name;
    }

    public static String getJson(String name) {
        return getUrl(name + ".json");
    }

    public static String getApk(String name) {
        return getUrl(name + ".apk");
    }
}
