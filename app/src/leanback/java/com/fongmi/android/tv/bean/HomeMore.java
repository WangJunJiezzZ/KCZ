package com.fongmi.android.tv.bean;

/**
 * 二次开发：首页分类行末尾的"查看全部"卡片，点击打开片库并定位到该分类。
 * 分类可能来自首页站以外的站（如动漫、短剧行），所以带上站点 key 和该站的首页结果（片库要用它的分类列表）。
 */
public class HomeMore {

    private final String siteKey;
    private final Result result;
    private final String typeId;
    private final String typeName;
    /** 卡片上显示的文字；行尾卡片是「查看全部 分类名 ›」，首页底部「浏览全部」行是「分类名 ›」 */
    private final String label;

    public HomeMore(String siteKey, Result result, String typeId, String typeName) {
        this(siteKey, result, typeId, typeName, "查看全部\n" + typeName + " ›");
    }

    public HomeMore(String siteKey, Result result, String typeId, String typeName, String label) {
        this.siteKey = siteKey;
        this.result = result;
        this.typeId = typeId;
        this.typeName = typeName;
        this.label = label;
    }

    /** 首页底部「浏览全部」行的卡片 */
    public HomeMore asBrowse() {
        return new HomeMore(siteKey, result, typeId, typeName, typeName + " ›");
    }

    public String getLabel() {
        return label;
    }

    public String getSiteKey() {
        return siteKey;
    }

    public Result getResult() {
        return result;
    }

    public String getTypeId() {
        return typeId;
    }

    public String getTypeName() {
        return typeName;
    }
}
