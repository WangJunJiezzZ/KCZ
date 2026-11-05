package com.fongmi.android.tv.ui.presenter;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.leanback.widget.Presenter;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Func;
import com.fongmi.android.tv.databinding.AdapterHomeRailBinding;

/**
 * 二次开发：奈飞风格首页的左侧导航项。
 * 导航收起时只显示图标；导航获得焦点时（expanded）展开显示文字，当前项高亮为白色。
 */
public class HomeRailPresenter extends Presenter {

    private final FuncPresenter.OnClickListener listener;
    private boolean expanded;

    public HomeRailPresenter(FuncPresenter.OnClickListener listener) {
        this.listener = listener;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    @NonNull
    @Override
    public Presenter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent) {
        ViewHolder holder = new ViewHolder(AdapterHomeRailBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        holder.view.setOnFocusChangeListener((view, hasFocus) -> holder.render(expanded || hasFocus, hasFocus));
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull Presenter.ViewHolder viewHolder, Object object) {
        Func item = (Func) object;
        ViewHolder holder = (ViewHolder) viewHolder;
        holder.binding.text.setText(item.getText());
        holder.binding.icon.setImageResource(item.getDrawable());
        holder.render(expanded, holder.view.hasFocus());
        setOnClickListener(holder, view -> listener.onItemClick(item));
    }

    @Override
    public void onUnbindViewHolder(@NonNull Presenter.ViewHolder viewHolder) {
    }

    public static class ViewHolder extends Presenter.ViewHolder {

        private final AdapterHomeRailBinding binding;

        public ViewHolder(@NonNull AdapterHomeRailBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        /** showText：是否显示文字；active：是否为当前焦点项（白色高亮） */
        public void render(boolean showText, boolean active) {
            int color = ContextCompat.getColor(view.getContext(), active ? R.color.white : R.color.home_rail_idle);
            ImageViewCompat.setImageTintList(binding.icon, ColorStateList.valueOf(color));
            binding.text.setTextColor(color);
            binding.text.setVisibility(showText ? View.VISIBLE : View.GONE);
        }
    }
}
