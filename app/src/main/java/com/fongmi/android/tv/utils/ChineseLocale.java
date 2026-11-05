package com.fongmi.android.tv.utils;

import android.content.Context;
import android.content.res.Configuration;
import android.os.LocaleList;

import java.util.Locale;

/**
 * 二次开发：不管电视系统语言是什么，App 界面一律显示简体中文。
 * 在 Application 和每个 Activity 的 attachBaseContext 里调用 wrap()。
 */
public final class ChineseLocale {

    public static final Locale LOCALE = Locale.SIMPLIFIED_CHINESE;

    private ChineseLocale() {
    }

    public static Context wrap(Context base) {
        Locale.setDefault(LOCALE);
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocale(LOCALE);
        config.setLocales(new LocaleList(LOCALE));
        return base.createConfigurationContext(config);
    }
}
