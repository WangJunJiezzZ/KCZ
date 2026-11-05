package com.fongmi.android.tv.bean;

/** 二次开发：首页分类行的标题（如"电影""剧集"），由 HomeHeaderPresenter 显示 */
public class HomeRowTitle {

    private final String text;

    public HomeRowTitle(String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return text;
    }
}
