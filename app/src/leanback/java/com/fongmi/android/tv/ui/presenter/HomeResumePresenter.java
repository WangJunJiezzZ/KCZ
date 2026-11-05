package com.fongmi.android.tv.ui.presenter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.leanback.widget.Presenter;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.databinding.AdapterHomePosterBinding;
import com.fongmi.android.tv.utils.ImgUtil;

/**
 * 二次开发：奈飞风格首页的"继续观看"卡片，带观看进度条。
 * 长按进入删除模式（再长按清空），逻辑与原 HistoryPresenter 一致。
 */
public class HomeResumePresenter extends Presenter {

    private final Listener listener;
    private boolean delete;

    public HomeResumePresenter(Listener listener) {
        this.listener = listener;
    }

    public interface Listener extends HistoryPresenter.OnClickListener {

        void onFocus(History item);
    }

    public boolean isDelete() {
        return delete;
    }

    public void setDelete(boolean delete) {
        this.delete = delete;
    }

    @NonNull
    @Override
    public Presenter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent) {
        return new ViewHolder(AdapterHomePosterBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Presenter.ViewHolder viewHolder, Object object) {
        History item = (History) object;
        ViewHolder holder = (ViewHolder) viewHolder;
        String badge = item.getVodRemarks();
        boolean showBadge = !delete && !TextUtils.isEmpty(badge) && !badge.equals(item.getVodName());
        holder.binding.badge.setText(badge);
        holder.binding.badge.setVisibility(showBadge ? View.VISIBLE : View.GONE);
        holder.binding.delete.setVisibility(delete ? View.VISIBLE : View.GONE);
        holder.binding.progress.setVisibility(item.getDuration() > 0 && !delete ? View.VISIBLE : View.GONE);
        holder.binding.progress.setProgress(item.getDuration() > 0 ? (int) (item.getPosition() * 1000 / item.getDuration()) : 0);
        ImgUtil.load(item.getVodName(), item.getVodPic(), holder.binding.image);
        holder.view.setOnLongClickListener(view -> listener.onLongClick());
        holder.view.setOnClickListener(view -> {
            if (isDelete()) listener.onItemDelete(item);
            else listener.onItemClick(item);
        });
        holder.view.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) listener.onFocus(item);
        });
    }

    @Override
    public void onUnbindViewHolder(@NonNull Presenter.ViewHolder viewHolder) {
        ViewHolder holder = (ViewHolder) viewHolder;
        holder.view.setOnFocusChangeListener(null);
        Glide.with(holder.binding.image).clear(holder.binding.image);
    }

    public static class ViewHolder extends Presenter.ViewHolder {

        private final AdapterHomePosterBinding binding;

        public ViewHolder(@NonNull AdapterHomePosterBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
