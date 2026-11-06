package com.fongmi.android.tv;

import android.content.Context;
import android.os.Build;
import android.view.DisplayCutout;

import com.fongmi.android.tv.bean.Style;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.ui.activity.HomeActivity;
import com.fongmi.android.tv.utils.ResUtil;

public class Product {

    public static int getDeviceType() {
        return 1;
    }

    public static int getColumn(Context context) {
        return getColumn(context, Style.rect());
    }

    /** Use the current window, including split screen, rather than a device label. */
    public static int getColumn(Context context, Style style) {
        int width = context.getResources().getConfiguration().screenWidthDp;
        int available = width - (hasRail(context) ? 88 : 0);
        int target = style.isLand() ? 200 : width >= 600 ? 140 : 112;
        int base = Math.max(2, available / target);
        return Math.clamp(base + 2 - PlayerSetting.getSize(), 2, 10);
    }

    public static int[] getSpec(Context context) {
        return getSpec(context, Style.rect());
    }

    public static int[] getSpec(Context context, Style style) {
        int column = getColumn(context, style);
        int space = ResUtil.dp2px(32) + ResUtil.dp2px(16 * (column - 1)) + getCutout(context);
        if (style.isOval()) space += ResUtil.dp2px(column * 16);
        return getSpec(context, space, column, style);
    }

    private static int[] getSpec(Context context, int space, int column, Style style) {
        int window = Math.round(context.getResources().getConfiguration().screenWidthDp * context.getResources().getDisplayMetrics().density);
        int rail = hasRail(context) ? ResUtil.dp2px(88) : 0;
        int base = Math.max(column, window - rail - space);
        int width = base / column;
        int height = (int) (width / style.getRatio());
        return new int[]{width, height};
    }

    private static int getCutout(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0;
        DisplayCutout cutout = ResUtil.getDisplay(context).getCutout();
        if (cutout == null) return 0;
        int left = cutout.getSafeInsetLeft();
        int right = cutout.getSafeInsetRight();
        return left | right;
    }

    private static boolean hasRail(Context context) {
        return context instanceof HomeActivity && context.getResources().getConfiguration().screenWidthDp >= 600;
    }
}
