package com.fongmi.android.tv.ui.presenter;

import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.widget.Presenter;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.AdapterLandscapeCardBinding;
import com.fongmi.android.tv.utils.Backdrop;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.LibraryPlanner;
import com.fongmi.android.tv.utils.ResUtil;

/**
 * 二次开发：奈飞风格片库的横版卡片（16:9）。
 * 剧照优先级和大图区一致：源提供的横版剧照 → TMDB 剧照（w780，只查绑定到屏幕上的卡片）→ 竖版海报垫底。
 * 行尾的「查看全部」卡片是 LibraryPlanner.Row。
 */
public class LandscapeCardPresenter extends Presenter {

    private static final int BLUR_WIDTH = 24;
    private static final int BLUR_HEIGHT = 36;

    private final Listener listener;

    public LandscapeCardPresenter(Listener listener) {
        this.listener = listener;
    }

    public interface Listener extends VodPresenter.OnClickListener {

        void onFocus(Vod item);

        void onMore(LibraryPlanner.Row row);
    }

    @NonNull
    @Override
    public Presenter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent) {
        ViewHolder holder = new ViewHolder(AdapterLandscapeCardBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        holder.view.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        holder.view.setClipToOutline(true);
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull Presenter.ViewHolder viewHolder, Object object) {
        ViewHolder holder = (ViewHolder) viewHolder;
        if (object instanceof LibraryPlanner.Row row) {
            bindMore(holder, row);
            return;
        }
        Vod item = (Vod) object;
        holder.item = item;
        holder.binding.more.setVisibility(View.GONE);
        holder.binding.name.setText(item.getName());
        String badge = item.getRemarks();
        holder.binding.badge.setText(badge);
        holder.binding.badge.setVisibility(TextUtils.isEmpty(badge) ? View.GONE : View.VISIBLE);
        clear(holder);
        // 卡片会被复用：先恢复成干净状态（上一部片子可能是海报样式）
        holder.binding.blur.setVisibility(View.GONE);
        holder.binding.poster.setVisibility(View.GONE);
        holder.binding.shade.setVisibility(View.GONE);
        placeName(holder, false);
        String cached = item.hasBackdrop() ? item.getBackdrop() : Backdrop.cached(item.getName(), item.getYear());
        if (cached != null) {
            if (cached.isEmpty()) showPoster(holder, item);
            else showImage(holder, item, Backdrop.sized(cached, "w780"));
        } else {
            // 查剧照期间先按海报样式显示（不留一块空白），查到后剧照淡入盖上
            showPoster(holder, item);
            Backdrop.findForCard(item.getName(), item.getYear(), url -> {
                if (holder.item != item || url.isEmpty()) return;
                showImage(holder, item, Backdrop.sized(url, "w780"));
            });
        }
        holder.view.setOnClickListener(view -> listener.onItemClick(item));
        holder.view.setOnLongClickListener(view -> listener.onLongClick(item));
        holder.view.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) listener.onFocus(item);
        });
    }

    /** 剧照下载完成后再切换成剧照样式（下载期间保持海报样式，不闪空白） */
    private void showImage(ViewHolder holder, Vod item, String url) {
        Glide.with(holder.binding.image).load(ImgUtil.getUrl(url)).centerCrop().transition(DrawableTransitionOptions.withCrossFade(200)).listener(new RequestListener<>() {
            @Override
            public boolean onLoadFailed(@Nullable GlideException e, Object model, @NonNull Target<Drawable> target, boolean isFirstResource) {
                if (holder.item == item && holder.binding.poster.getVisibility() != View.VISIBLE) showPoster(holder, item);
                return false;
            }

            @Override
            public boolean onResourceReady(@NonNull Drawable resource, @NonNull Object model, Target<Drawable> target, @NonNull DataSource dataSource, boolean isFirstResource) {
                if (holder.item != item) return false;
                holder.binding.blur.setVisibility(View.GONE);
                holder.binding.poster.setVisibility(View.GONE);
                holder.binding.shade.setVisibility(View.VISIBLE);
                placeName(holder, true);
                return false;
            }
        }).into(holder.binding.image);
    }

    /** 没有横版剧照：模糊海报底 + 左侧清晰小海报 + 右侧片名（不把竖海报裁成横图，人脸会被切掉） */
    private void showPoster(ViewHolder holder, Vod item) {
        holder.binding.shade.setVisibility(View.GONE);
        boolean hasPic = !TextUtils.isEmpty(item.getPic());
        placeName(holder, false, hasPic);
        if (!hasPic) return;
        holder.binding.blur.setVisibility(View.VISIBLE);
        holder.binding.poster.setVisibility(View.VISIBLE);
        Glide.with(holder.binding.blur).load(ImgUtil.getUrl(item.getPic())).override(BLUR_WIDTH, BLUR_HEIGHT).centerCrop().into(holder.binding.blur);
        Glide.with(holder.binding.poster).load(ImgUtil.getUrl(item.getPic())).centerCrop().transition(DrawableTransitionOptions.withCrossFade(200)).into(holder.binding.poster);
    }

    private void placeName(ViewHolder holder, boolean bottom) {
        placeName(holder, bottom, false);
    }

    /** bottom：剧照模式片名在左下角一行；否则垂直居中、最多三行（withPoster 时让出左侧海报的位置） */
    private void placeName(ViewHolder holder, boolean bottom, boolean withPoster) {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) holder.binding.name.getLayoutParams();
        params.gravity = bottom ? Gravity.BOTTOM | Gravity.START : Gravity.CENTER_VERTICAL | Gravity.START;
        params.setMarginStart(ResUtil.dp2px(withPoster ? 60 : 10));
        holder.binding.name.setLayoutParams(params);
        holder.binding.name.setSingleLine(bottom);
        holder.binding.name.setMaxLines(bottom ? 1 : 3);
    }

    private void bindMore(ViewHolder holder, LibraryPlanner.Row row) {
        holder.item = null;
        clear(holder);
        holder.binding.blur.setVisibility(View.GONE);
        holder.binding.poster.setVisibility(View.GONE);
        holder.binding.shade.setVisibility(View.GONE);
        holder.binding.badge.setVisibility(View.GONE);
        holder.binding.name.setText("");
        holder.binding.more.setVisibility(View.VISIBLE);
        holder.binding.more.setText("查看全部\n" + row.title + " ›");
        holder.view.setOnClickListener(view -> listener.onMore(row));
        holder.view.setOnLongClickListener(null);
        holder.view.setOnFocusChangeListener(null);
    }

    private void clear(ViewHolder holder) {
        Glide.with(holder.binding.image).clear(holder.binding.image);
        Glide.with(holder.binding.blur).clear(holder.binding.blur);
        Glide.with(holder.binding.poster).clear(holder.binding.poster);
        holder.binding.image.setImageDrawable(null);
    }

    @Override
    public void onUnbindViewHolder(@NonNull Presenter.ViewHolder viewHolder) {
        ViewHolder holder = (ViewHolder) viewHolder;
        holder.item = null;
        holder.view.setOnFocusChangeListener(null);
        clear(holder);
    }

    public static class ViewHolder extends Presenter.ViewHolder {

        private final AdapterLandscapeCardBinding binding;
        /** 当前绑定的片子：剧照查询回来时用来判断卡片有没有被复用给别的片子 */
        private Vod item;

        public ViewHolder(@NonNull AdapterLandscapeCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
