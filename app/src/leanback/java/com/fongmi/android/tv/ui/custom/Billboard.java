package com.fongmi.android.tv.ui.custom;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.DrawableCrossFadeFactory;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.utils.Backdrop;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 二次开发：奈飞风格的顶部大图区（首页、片库共用）。
 * 焦点停在哪部片子，就把它的片名 / 信息 / 简介和全屏剧照显示出来：
 * - 剧照优先级：源提供的横版剧照 → TMDB 剧照 → 竖版海报（右侧清晰海报卡片 + 模糊海报底色）
 * - 新旧剧照交叉淡化 + 轻微推镜；焦点快速移动时只加载最后停留的那一部
 * - 二次开发：剧照不再铺满全屏，只占右上角一条横幅（stage）：宽度为屏幕的 85%，高度只到卡片行上方，
 *   左边、底部渐隐到黑色；卡片行底下是黑底，卡片不再和背景剧照叠在一起。
 *   （保持 16:9 时 85% 宽 = 85% 高，几乎铺满全屏，所以改成横幅、裁掉剧照上下）
 */
public class Billboard {

    /** 等待剧照时背景的透明度 */
    private static final float DIM_ALPHA = 0.6f;
    /** 海报垫底时背景的透明度（模糊海报再压暗，保证文字和海报卡片突出） */
    private static final float POSTER_BG_ALPHA = 0.55f;
    /** 把海报缩到这么小再放大铺满，系统缩放的平滑效果就是柔和的模糊（API 24 也能用，不依赖 RenderEffect） */
    private static final int BLUR_WIDTH = 24;
    private static final int BLUR_HEIGHT = 36;

    /** 剧照区宽度占屏幕的比例（高度按 16:9 算，不裁切、不放大） */
    private static final float STAGE_WIDTH = 0.85f;
    /** 剧照区高度：内容行从 236dp 开始，第一行标题约 30dp，剧照底边落在第一行卡片顶上 */
    private static final int STAGE_HEIGHT_DP = 276;
    /** 剧照最多被裁掉的高度比例（上下各一半） */
    private static final float STAGE_MAX_CROP = 0.2f;
    /** 往下翻到后面几行时剧照区的透明度（注意力放在卡片上） */
    private static final float STAGE_DIM_ALPHA = 0.75f;

    private final Activity activity;
    private final View stage;
    private final ImageView backdrop;
    private final ImageView keyart;
    private final View info;
    private final TextView kicker;
    private final TextView title;
    private final TextView meta;
    private final TextView desc;

    private Runnable task;
    private String current;
    /** 背景剧照请求编号（换片 +1，作废过期回调） */
    private int token;
    private boolean dimmed;

    public Billboard(Activity activity, ImageView backdrop, ImageView keyart, View info, @Nullable TextView kicker, TextView title, TextView meta, TextView desc) {
        this.activity = activity;
        this.backdrop = backdrop;
        this.keyart = keyart;
        this.info = info;
        this.kicker = kicker;
        this.title = title;
        this.meta = meta;
        this.desc = desc;
        this.stage = (View) backdrop.getParent();
        this.backdrop.setAlpha(1f);
        this.keyart.setVisibility(View.GONE);
        fitStage();
    }

    /** 剧照区：屏幕宽度的 85% × 到卡片行上方；左边渐隐占 45% 宽度（片名、简介压在上面），底部渐隐占 40% 高度（太高会把人脸也压暗） */
    private void fitStage() {
        int height = ResUtil.dp2px(STAGE_HEIGHT_DP);
        // 高度被卡片行限死，宽度太大时 16:9 剧照会被 centerCrop 裁掉上下（人头被切）：
        // 宽度最多到「裁掉 20% 高度」为止，再不超过屏幕的 85%
        int maxWidth = Math.round(height * 16f / 9f / (1f - STAGE_MAX_CROP));
        int width = Math.min(Math.round(ResUtil.getScreenWidth() * STAGE_WIDTH), maxWidth);
        height = Math.min(height, width * 9 / 16);
        setSize(stage, width, height);
        setSize(stage.findViewById(com.fongmi.android.tv.R.id.stageFadeLeft), Math.round(width * 0.45f), height);
        setSize(stage.findViewById(com.fongmi.android.tv.R.id.stageFadeBottom), width, Math.round(height * 0.4f));
    }

    private static void setSize(View view, int width, int height) {
        if (view == null) return;
        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.width = width;
        params.height = height;
        view.setLayoutParams(params);
    }

    /**
     * TMDB 剧照用多大的图：剧照区不到 1600 像素宽用 w1280（几百 KB），更宽的屏幕（4K 电视）才用原图。
     * 不是 TMDB 的地址（源自带的剧照）原样返回
     */
    private String stageUrl(String url) {
        if (stage.getLayoutParams().width > 1600) return url;
        return Backdrop.sized(url, "w1280");
    }

    /** 往下翻到后面几行时把剧照区压暗，回到前面两行恢复 */
    public void setDimmed(boolean dim) {
        if (dimmed == dim) return;
        dimmed = dim;
        stage.animate().cancel();
        stage.animate().alpha(dim ? STAGE_DIM_ALPHA : 1f).setDuration(300).start();
    }

    public static String joinMeta(String... parts) {
        List<String> items = new ArrayList<>();
        for (String part : parts) if (!TextUtils.isEmpty(part) && !items.contains(part.trim())) items.add(part.trim());
        return TextUtils.join("  ·  ", items);
    }

    /** 片子的常用显示：年份 · 地区 · 类型 · 备注 */
    public void show(String kickerText, Vod item) {
        String metaText = joinMeta(item.getYear(), item.getArea(), item.getTypeName(), item.getRemarks());
        show(kickerText, item.getName(), metaText, item.getContent(), item.hasBackdrop() ? item.getBackdrop() : null, item.getPic(), item.getYear());
    }

    /**
     * 焦点快速移动时只加载最后停留的那一张，避免频繁请求图片。
     * 大图优先级：源提供的横版剧照 hd → TMDB 高清剧照 → 海报 poster
     */
    public void show(String kickerText, String name, String metaText, String descText, String hd, String poster, String year) {
        App.removeCallbacks(task);
        task = () -> {
            // 用立即更新的变量判断"当前影片"，不依赖带动画的片名文字（快速切换时动画会被打断）
            if (name.equals(current)) return;
            current = name;
            String text = descText == null ? "" : descText.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
            // 文字：淡出 + 左移 → 换内容 → 淡入归位
            info.animate().cancel();
            info.animate().alpha(0f).translationX(-ResUtil.dp2px(12)).setDuration(140).withEndAction(() -> {
                if (kicker != null) kicker.setText(kickerText);
                title.setText(name);
                meta.setText(metaText);
                meta.setVisibility(TextUtils.isEmpty(metaText) ? View.GONE : View.VISIBLE);
                desc.setText(text);
                desc.setVisibility(TextUtils.isEmpty(text) ? View.GONE : View.VISIBLE);
                info.setTranslationX(ResUtil.dp2px(12));
                info.animate().alpha(1f).translationX(0f).setDuration(260).start();
            }).start();
            // 背景剧照：新旧两张交叉淡化，而不是直接替换
            int token = ++this.token; // 换片就作废上一部还没回来的剧照查询，旧图晚到不会盖住新图
            if (!TextUtils.isEmpty(hd)) showBackdrop(hd);
            else if (!Backdrop.enabled()) showPosterFallback(poster);
            else {
                // 等 TMDB 结果时只把当前背景压暗（不先换成放大的糊海报，也不让上一部的剧照亮着）；
                // 缓存命中时回调是同步的，压暗动画会被立即取消，不会闪
                hideKeyart();
                backdrop.animate().cancel();
                backdrop.animate().alpha(DIM_ALPHA).setDuration(200).start();
                Backdrop.find(name, year, url -> {
                    if (token != this.token) return;
                    if (url.isEmpty()) showPosterFallback(poster);
                    else showBackdrop(url);
                });
            }
        };
        App.post(task, 120);
    }

    /** 清空当前片子记录：下次 show 同一部片子也会重新显示（切换分类后用） */
    public void reset() {
        current = null;
    }

    public void cancel() {
        App.removeCallbacks(task);
        token++;
    }

    /** 预加载一部片子的剧照（焦点旁边的片子），移过去时直接从内存显示 */
    public void prefetch(Vod vod) {
        if (vod.hasBackdrop()) preload(vod.getBackdrop());
        else Backdrop.prefetch(vod.getName(), vod.getYear(), this::preload);
    }

    /** 按背景控件同样的尺寸和裁切预下载解码进内存缓存，之后 loadBackdrop 命中同一个缓存键 */
    public void preload(String url) {
        int width = backdrop.getWidth();
        int height = backdrop.getHeight();
        if (TextUtils.isEmpty(url) || width <= 0 || height <= 0 || activity.isFinishing() || activity.isDestroyed()) return;
        Glide.with(activity).load(ImgUtil.getUrl(stageUrl(url))).centerCrop().preload(width, height);
    }

    /** 有横版高清剧照：清晰铺满，不显示海报卡片 */
    private void showBackdrop(String url) {
        hideKeyart();
        setBackdropAlpha(1f);
        loadBackdrop(url, false);
    }

    /** 只有竖版海报：右侧显示清晰海报卡片，背景用同一张海报模糊 + 压暗，不把小海报放大铺满（会糊） */
    private void showPosterFallback(String poster) {
        if (TextUtils.isEmpty(poster)) {
            hideKeyart();
            setBackdropAlpha(DIM_ALPHA);
            return;
        }
        setBackdropAlpha(POSTER_BG_ALPHA);
        loadBackdrop(poster, true);
        Glide.with(keyart).load(ImgUtil.getUrl(poster)).centerCrop().into(keyart);
        keyart.animate().cancel();
        if (keyart.getVisibility() != View.VISIBLE) {
            keyart.setAlpha(0f);
            keyart.setVisibility(View.VISIBLE);
        }
        keyart.animate().alpha(1f).setDuration(260).start();
    }

    private void hideKeyart() {
        keyart.animate().cancel();
        keyart.setVisibility(View.GONE);
    }

    private void setBackdropAlpha(float alpha) {
        backdrop.animate().cancel();
        backdrop.animate().alpha(alpha).setDuration(260).start();
    }

    private void loadBackdrop(String pic, boolean blur) {
        if (TextUtils.isEmpty(pic)) return;
        String source = pic;
        if (!blur) pic = stageUrl(pic);
        RequestBuilder<Drawable> builder = Glide.with(backdrop)
                .load(ImgUtil.getUrl(pic))
                .placeholder(snapshotBackdrop())
                .transition(DrawableTransitionOptions.with(new DrawableCrossFadeFactory.Builder(600).setCrossFadeEnabled(true).build()));
        if (blur) {
            builder = builder.override(BLUR_WIDTH, BLUR_HEIGHT);
        } else {
            // 推镜只做一次：小图先到时从小图开始，原图接着换上不再重来
            boolean[] pushed = {false};
            RequestListener<Drawable> push = new RequestListener<>() {
                @Override
                public boolean onLoadFailed(@Nullable GlideException e, Object model, @NonNull Target<Drawable> target, boolean isFirstResource) {
                    return false;
                }

                @Override
                public boolean onResourceReady(@NonNull Drawable resource, @NonNull Object model, Target<Drawable> target, @NonNull DataSource dataSource, boolean isFirstResource) {
                    if (!pushed[0]) startPushIn();
                    pushed[0] = true;
                    return false;
                }
            };
            builder = builder.listener(push);
            // 先显示 w780 小图（片库卡片已经下载过，直接命中缓存），大图到了再换上
            String small = Backdrop.sized(source, "w780");
            if (!pic.equals(small)) builder = builder.thumbnail(Glide.with(backdrop).load(ImgUtil.getUrl(small)).centerCrop().listener(push));
        }
        builder.centerCrop().into(backdrop);
    }

    /**
     * 新剧照出现时从 1.04 倍缓慢缩回原大小（配合交叉淡化，奈飞式的轻微推镜感）。
     * 只用于清晰剧照（目标透明度就是 1）：cancel 会打断 setBackdropAlpha 的恢复动画，所以透明度在这里一起带到 1，
     * 否则预加载命中内存、图片同步到达时，背景会停在压暗状态
     */
    private void startPushIn() {
        backdrop.animate().cancel();
        backdrop.setScaleX(1.04f);
        backdrop.setScaleY(1.04f);
        backdrop.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(1600).setInterpolator(new DecelerateInterpolator()).start();
    }

    /**
     * 把当前背景画面拷贝成一张独立的图，作为新图加载期间的占位（交叉淡化的起点）。
     * 不能直接用 getDrawable()：Glide 开始新请求时会释放旧图，旧 Bitmap 可能被回收/复用，
     * 占位还引用它就会崩（Canvas: trying to use a recycled bitmap）。
     */
    private Drawable snapshotBackdrop() {
        Drawable drawable = backdrop.getDrawable();
        int width = backdrop.getWidth();
        int height = backdrop.getHeight();
        if (drawable == null || width <= 0 || height <= 0) return null;
        try {
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.concat(backdrop.getImageMatrix());
            drawable.draw(canvas);
            return new BitmapDrawable(activity.getResources(), bitmap);
        } catch (Throwable e) {
            return null;
        }
    }
}
