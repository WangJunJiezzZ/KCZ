package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.bean.Vod;
import java.util.HashMap;
import java.util.Locale;

/** Prefer an explicitly advertised same-device flow; do not invent an OAuth endpoint. */
public final class MobileLoginEntry {
    private MobileLoginEntry() {}

    public static NetdiskLogin.Entry preferred(NetdiskLogin.Entry qr) throws Exception {
        for (Vod item : SiteApi.categoryContent(qr.siteKey(), qr.typeId(), "1", false, new HashMap<>()).getList()) {
            String name = item.getName().toLowerCase(Locale.ROOT);
            if (name.contains("清除") || name.contains("退出") || name.contains("手动") || name.contains("扫码")) continue;
            if (name.contains("手机登录") || name.contains("本机登录") || name.contains("网页登录")
                    || name.contains("浏览器登录") || name.contains("授权登录")) {
                return new NetdiskLogin.Entry(qr.siteKey(), qr.typeId(), item.getId(), item.getAction(), item.getRemarks());
            }
        }
        return qr;
    }
}
