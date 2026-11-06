package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.textview.MaterialTextView;

import java.util.function.Consumer;

/**
 * 二次开发：一排档位按钮（参考 B 站 / 奈飞的设置面板：固定档位、当前档高亮，代替没刻度的滑条）。
 * 样式和设置面板里其它按钮一致（shape_accent + selector_control）。
 */
public final class ChipRow {

    private ChipRow() {
    }

    /** 单选档位：values 和 labels 一一对应，current 最接近的一档高亮（差太多就都不高亮） */
    public static void single(LinearLayout row, String[] labels, float[] values, float current, Consumer<Float> onPick) {
        row.removeAllViews();
        int selected = nearest(values, current);
        for (int i = 0; i < labels.length; i++) {
            float value = values[i];
            TextView chip = chip(row.getContext(), labels[i]);
            chip.setSelected(i == selected);
            chip.setOnClickListener(view -> {
                for (int j = 0; j < row.getChildCount(); j++) row.getChildAt(j).setSelected(false);
                view.setSelected(true);
                onPick.accept(value);
            });
            row.addView(chip, params(row, i == labels.length - 1));
        }
    }

    /** 开关按钮：点一下切换选中状态 */
    public static TextView toggle(LinearLayout row, String label, boolean checked, Consumer<Boolean> onChange) {
        TextView chip = chip(row.getContext(), label);
        chip.setSelected(checked);
        chip.setOnClickListener(view -> {
            view.setSelected(!view.isSelected());
            onChange.accept(view.isSelected());
        });
        row.addView(chip, params(row, false));
        return chip;
    }

    private static int nearest(float[] values, float current) {
        int best = -1;
        float diff = Float.MAX_VALUE;
        for (int i = 0; i < values.length; i++) {
            float d = Math.abs(values[i] - current);
            if (d < diff) {
                diff = d;
                best = i;
            }
        }
        // 只有足够接近才算选中（比如长按临时调成 1.1x 时，不硬把 1.0x 点亮）
        float tolerance = Math.max(0.01f, Math.abs(current) * 0.02f);
        return diff <= tolerance ? best : -1;
    }

    private static TextView chip(Context context, String label) {
        TextView chip = new MaterialTextView(context);
        chip.setText(label);
        chip.setTextSize(14);
        chip.setBackgroundResource(R.drawable.shape_accent);
        chip.setTextColor(ContextCompat.getColorStateList(context, R.color.selector_control));
        chip.setMinWidth(ResUtil.dp2px(48));
        chip.setMaxLines(1);
        chip.setPadding(ResUtil.dp2px(4), ResUtil.dp2px(8), ResUtil.dp2px(4), ResUtil.dp2px(8));
        chip.setGravity(android.view.Gravity.CENTER);
        chip.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        return chip;
    }

    /** 行宽是 match_parent 时各档等分整行（不用横向滑动）；否则按内容宽度排列 */
    private static LinearLayout.LayoutParams params(LinearLayout row, boolean last) {
        boolean fill = row.getLayoutParams() != null && row.getLayoutParams().width == ViewGroup.LayoutParams.MATCH_PARENT;
        LinearLayout.LayoutParams params = fill ? new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) : new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        if (!(fill && last)) params.setMarginEnd(ResUtil.dp2px(fill ? 6 : 8));
        return params;
    }
}
