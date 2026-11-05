package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.bean.Class;
import com.fongmi.android.tv.bean.Site;

import java.util.ArrayList;
import java.util.List;

/**
 * 二次开发：首页按类型分行的规则。
 * 首页站给的分类常常重复（如豆瓣类站：热门电影 / 电影筛选 / 电影榜单 内容大量重叠），
 * 首页改成固定几种类型各一行：热门电影 → 热门剧集 → 热门综艺 → 动漫 → 短剧；
 * 每种类型在首页站里挑最合适的一个分类，首页站没有的类型去「名字带该类型」的专门站补。
 */
public final class HomeRowPlanner {

    public enum Genre {
        MOVIE("热门电影", new String[]{"电影"}, new String[]{"微电影"}),
        TV("热门剧集", new String[]{"剧集", "电视剧", "电视"}, new String[]{"短剧"}),
        SHOW("热门综艺", new String[]{"综艺"}, new String[0]),
        ANIME("动漫", new String[]{"动漫", "动画", "番剧"}, new String[0]),
        SHORT("短剧", new String[]{"短剧"}, new String[0]);

        public final String title;
        private final String[] keywords;
        private final String[] excludes;

        Genre(String title, String[] keywords, String[] excludes) {
            this.title = title;
            this.keywords = keywords;
            this.excludes = excludes;
        }

        public boolean matches(String name) {
            if (name == null) return false;
            for (String word : excludes) if (name.contains(word)) return false;
            for (String word : keywords) if (name.contains(word)) return true;
            return false;
        }
    }

    private HomeRowPlanner() {
    }

    /** 分类名属于哪种类型；都不是返回 null */
    public static Genre genreOf(String typeName) {
        for (Genre genre : Genre.values()) if (genre.matches(typeName)) return genre;
        return null;
    }

    /** 在一组分类里挑该类型最合适的一个：热门/热播/推荐 > 普通 > 榜单/排行 > 筛选；没有返回 null */
    public static Class pick(List<Class> types, Genre genre) {
        Class best = null;
        int bestScore = -1;
        for (Class type : types) {
            if (!genre.matches(type.getTypeName())) continue;
            int score = preference(type.getTypeName());
            if (score > bestScore) {
                best = type;
                bestScore = score;
            }
        }
        return best;
    }

    private static int preference(String name) {
        if (name.contains("热门") || name.contains("热播") || name.contains("推荐")) return 3;
        if (name.contains("筛选")) return 0;
        if (name.contains("榜单") || name.contains("排行")) return 1;
        return 2;
    }

    /** 名字像该类型专门站的站点（如「巴士┃动漫」「短剧┃小猫」），按配置顺序，排除首页站和配置中心类站 */
    public static List<Site> sitesFor(List<Site> sites, Genre genre, Site home) {
        List<Site> items = new ArrayList<>();
        for (Site site : sites) {
            if (isExcluded(site, home)) continue;
            if (genre.matches(site.getName())) items.add(site);
        }
        return items;
    }

    /** 综合影视站（名字带 采集/影视/秒播/多线，如「爱看┃采集」），没有专门站时到它们的分类里找该类型 */
    public static List<Site> generalSites(List<Site> sites, Site home) {
        List<Site> items = new ArrayList<>();
        for (Site site : sites) {
            if (isExcluded(site, home) || site.isIndex()) continue;
            String name = site.getName();
            if (name.contains("采集") || name.contains("影视") || name.contains("秒播") || name.contains("多线")) items.add(site);
        }
        return items;
    }

    private static boolean isExcluded(Site site, Site home) {
        return site.getKey().equals(home.getKey()) || site.getName().contains("配置");
    }
}
