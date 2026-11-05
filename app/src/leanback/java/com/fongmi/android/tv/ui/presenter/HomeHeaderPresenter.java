package com.fongmi.android.tv.ui.presenter;

import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.leanback.widget.Presenter;

import com.fongmi.android.tv.utils.ResUtil;

/** 二次开发：奈飞风格首页的行标题（小号、加粗、浅灰） */
public class HomeHeaderPresenter extends Presenter {

    @NonNull
    @Override
    public Presenter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent) {
        TextView view = new TextView(parent.getContext());
        view.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setTextColor(0xFFE5E5E5);
        view.setPadding(0, ResUtil.dp2px(4), 0, 0);
        view.setFocusable(false);
        return new Presenter.ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Presenter.ViewHolder viewHolder, Object object) {
        ((TextView) viewHolder.view).setText(object instanceof Integer res ? ResUtil.getString(res) : object.toString());
    }

    @Override
    public void onUnbindViewHolder(@NonNull Presenter.ViewHolder viewHolder) {
    }
}
