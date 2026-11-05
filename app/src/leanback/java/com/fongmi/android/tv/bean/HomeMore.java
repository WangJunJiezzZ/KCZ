package com.fongmi.android.tv.bean;

/** 二次开发：首页分类行末尾的"查看全部"卡片，点击打开片库并定位到该分类 */
public class HomeMore {

    private final String typeId;
    private final String typeName;

    public HomeMore(String typeId, String typeName) {
        this.typeId = typeId;
        this.typeName = typeName;
    }

    public String getTypeId() {
        return typeId;
    }

    public String getTypeName() {
        return typeName;
    }
}
