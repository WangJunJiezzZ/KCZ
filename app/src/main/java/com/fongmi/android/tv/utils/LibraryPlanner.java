package com.fongmi.android.tv.utils;

import android.text.TextUtils;

import com.fongmi.android.tv.bean.Filter;
import com.fongmi.android.tv.bean.Value;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 二次开发：奈飞风格片库——把一个分类按它自带的筛选条件拆成多行。
 * 源没有「动作片一行」这种数据，但大多数站的分类带筛选（排序 / 剧情 / 地区 / 年份），
 * 每一行就是「这个分类 + 一个筛选值」的第一页：
 * 热门 → 最新 → 剧情前几个 → 高分 → 地区 → 其余剧情 → 最近两年。
 * 有「人气排序」时，剧情 / 地区 / 年份行都按人气排，行里先出现大家在看的片子。
 */
public final class LibraryPlanner {

    /** 一行：标题 + 请求该行时用的筛选条件（已含各筛选的默认值）。也用作行尾「查看全部」卡片 */
    public static final class Row {

        public final String title;
        public final HashMap<String, String> extend;

        public Row(String title, HashMap<String, String> extend) {
            this.title = title;
            this.extend = extend;
        }
    }

    private static final int GENRE_HEAD = 4;
    private static final int MAX_GENRE = 12;
    private static final int MAX_AREA = 6;
    private static final int MAX_YEAR = 2;

    private static final List<String> SORT_KEYS = Arrays.asList("by", "sort", "order", "orderby", "sort_by");
    private static final List<String> GENRE_KEYS = Arrays.asList("class", "type", "genre", "cateId", "cate", "tag", "tid");
    private static final Pattern HOT = Pattern.compile("人气|热|播放|点击|hits");
    private static final Pattern NEW = Pattern.compile("时间|最新|更新|上映|time");
    private static final Pattern SCORE = Pattern.compile("评分|豆瓣|好评|口碑|score");

    private LibraryPlanner() {
    }

    /** 各筛选条件的默认值（部分源不带会报错） */
    public static HashMap<String, String> defaults(List<Filter> filters) {
        HashMap<String, String> extend = new HashMap<>();
        if (filters == null) return extend;
        for (Filter filter : filters) if (!TextUtils.isEmpty(filter.getInit())) extend.put(filter.getKey(), filter.getInit());
        return extend;
    }

    /** 能不能拆成多行：至少要有排序或剧情 / 地区 / 年份其中之一 */
    public static boolean canPlan(List<Filter> filters) {
        return plan("", filters).size() > 1;
    }

    public static List<Row> plan(String typeName, List<Filter> filters) {
        List<Row> rows = new ArrayList<>();
        if (filters == null || filters.isEmpty()) return rows;
        HashMap<String, String> base = defaults(filters);
        Filter sort = find(filters, SORT_KEYS, "排序");
        Filter genre = find(filters, GENRE_KEYS, "类型|剧情|分类|题材");
        Filter area = find(filters, Arrays.asList("area", "region", "country"), "地区|国家|产地");
        Filter year = find(filters, Arrays.asList("year"), "年份|年代");
        Value hot = pick(sort, HOT);
        Value latest = pick(sort, NEW);
        Value score = pick(sort, SCORE);
        String suffix = typeName.length() <= 3 ? typeName : "";

        // 其余行都按人气排（有的话），行里先是大家在看的
        HashMap<String, String> hotBase = with(base, sort, hot);
        if (hot != null) rows.add(new Row("热门" + suffix, hotBase));
        if (latest != null) rows.add(new Row("最新" + suffix, with(base, sort, latest)));
        if (rows.isEmpty()) rows.add(new Row("最近更新", new HashMap<>(base)));

        List<Value> genres = values(genre, MAX_GENRE);
        for (int i = 0; i < Math.min(GENRE_HEAD, genres.size()); i++) rows.add(new Row(genres.get(i).getN(), with(hotBase, genre, genres.get(i))));
        if (score != null) rows.add(new Row("高分" + suffix, with(base, sort, score)));
        for (Value value : values(area, MAX_AREA)) rows.add(new Row(value.getN() + suffix, with(hotBase, area, value)));
        for (int i = GENRE_HEAD; i < genres.size(); i++) rows.add(new Row(genres.get(i).getN(), with(hotBase, genre, genres.get(i))));
        for (Value value : yearValues(year)) rows.add(new Row(value.getN() + " 年", with(hotBase, year, value)));
        return rows;
    }

    private static Filter find(List<Filter> filters, List<String> keys, String names) {
        Pattern pattern = Pattern.compile(names);
        for (Filter filter : filters) if (keys.contains(filter.getKey())) return filter;
        for (Filter filter : filters) if (pattern.matcher(filter.getName()).find()) return filter;
        return null;
    }

    private static Value pick(Filter filter, Pattern pattern) {
        if (filter == null) return null;
        for (Value value : filter.getValue()) if (value != null && pattern.matcher(value.getN()).find()) return value;
        return null;
    }

    /** 去掉「全部」「不限」和空值 */
    private static List<Value> values(Filter filter, int max) {
        List<Value> items = new ArrayList<>();
        if (filter == null) return items;
        for (Value value : filter.getValue()) {
            if (items.size() >= max) break;
            if (value == null || TextUtils.isEmpty(value.getV()) || TextUtils.isEmpty(value.getN())) continue;
            if (value.getN().contains("全部") || value.getN().contains("不限") || value.getN().contains("其它") || value.getN().contains("其他")) continue;
            items.add(value);
        }
        return items;
    }

    /** 年份只取最近两个具体年份（跳过「2010以前」「更早」这类） */
    private static List<Value> yearValues(Filter filter) {
        List<Value> items = new ArrayList<>();
        for (Value value : values(filter, 20)) {
            if (items.size() >= MAX_YEAR) break;
            if (value.getN().matches("(19|20)\\d{2}")) items.add(value);
        }
        return items;
    }

    private static HashMap<String, String> with(HashMap<String, String> base, Filter filter, Value value) {
        HashMap<String, String> extend = new HashMap<>(base);
        if (filter != null && value != null) extend.put(filter.getKey(), value.getV());
        return extend;
    }
}
