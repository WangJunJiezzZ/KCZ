package com.fongmi.android.tv.ui.presenter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.leanback.widget.Presenter;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.bean.HomeMore;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.AdapterHomePosterBinding;
import com.fongmi.android.tv.utils.ImgUtil;

/**
 * 二次开发：奈飞风格首页的海报卡片（竖版 2:3）。
 * 选中时通知首页更新顶部大图区；点击 / 长按沿用原首页的 VodPresenter 逻辑。
 */
public class HomePosterPresenter extends Presenter {

    private final Listener listener;

    public HomePosterPresenter(Listener listener) {
        this.listener = listener;
    }

    public interface Listener extends VodPresenter.OnClickListener {

        void onFocus(Vod item);

        void onMore(HomeMore item);
    }

    @NonNull
    @Override
    public Presenter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent) {
        return new ViewHolder(AdapterHomePosterBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Presenter.ViewHolder viewHolder, Object object) {
        ViewHolder holder = (ViewHolder) viewHolder;
        if (object instanceof HomeMore more) {
            bindMore(holder, more);
            return;
        }
        Vod item = (Vod) object;
        holder.binding.more.setVisibility(View.GONE);
        String badge = item.getRemarks();
        holder.binding.badge.setText(badge);
        holder.binding.badge.setVisibility(TextUtils.isEmpty(badge) ? View.GONE : View.VISIBLE);
        ImgUtil.load(item.getName(), item.getPic(), holder.binding.image);
        holder.view.setOnClickListener(view -> listener.onItemClick(item));
        holder.view.setOnLongClickListener(view -> listener.onLongClick(item));
        holder.view.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) listener.onFocus(item);
        });
    }

    private void bindMore(ViewHolder holder, HomeMore more) {
        Glide.with(holder.binding.image).clear(holder.binding.image);
        holder.binding.image.setImageDrawable(null);
        holder.binding.badge.setVisibility(View.GONE);
        holder.binding.progress.setVisibility(View.GONE);
        holder.binding.more.setVisibility(View.VISIBLE);
        holder.binding.more.setText(more.getLabel());
        holder.view.setOnClickListener(view -> listener.onMore(more));
        holder.view.setOnLongClickListener(null);
        holder.view.setOnFocusChangeListener(null);
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
