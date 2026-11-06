package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.databinding.DialogDanmakuQuickBinding;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.setting.DanmakuSetting;
import com.fongmi.android.tv.ui.custom.ChipRow;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;

/**
 * 二次开发：手机端简洁弹幕设置（参考 B 站）。
 * 只留最常用的几项，每项是固定档位；粗体、描边、投影、各类行数等高级项点「更多设置」进原来的完整面板。
 */
public final class DanmakuQuickDialog {

    private static final float[] OPACITY = {0.3f, 0.5f, 0.7f, 1.0f};
    private static final String[] OPACITY_LABEL = {"30%", "50%", "70%", "100%"};
    private static final float[] SIZE = {0.8f, 1.0f, 1.25f, 1.5f};
    private static final String[] SIZE_LABEL = {"小", "标准", "大", "特大"};
    /** 弹幕在屏幕上停留的时间（毫秒），越短越快 */
    private static final float[] SPEED = {12000f, 10000f, 8000f, 6000f, 4500f};
    private static final String[] SPEED_LABEL = {"极慢", "慢", "适中", "快", "极快"};
    private static final float[] AREA = {0.25f, 0.5f, 0.75f, 1.0f};
    private static final String[] AREA_LABEL = {"1/4 屏", "半屏", "3/4 屏", "全屏"};

    private DanmakuQuickDialog() {
    }

    /** 由 DanmakuSettingDialog 的简洁入口调用（手机版播放页注册） */
    public static void open(FragmentActivity activity, PlayerManager player) {
        FragmentManager manager = activity.getSupportFragmentManager();
        for (Fragment fragment : manager.getFragments()) if (fragment instanceof Bottom || fragment instanceof Side) return;
        if (Util.isFullscreenLand(activity)) new Side(player).show(manager, null);
        else new Bottom(player).show(manager, null);
    }

    private static void bind(DialogDanmakuQuickBinding binding, PlayerManager player, DialogFragment dialog) {
        ChipRow.single(binding.opacity, OPACITY_LABEL, OPACITY, 1f - DanmakuSetting.getTransparency(), value -> {
            DanmakuSetting.putTransparency(1f - value);
            apply(player);
        });
        ChipRow.single(binding.size, SIZE_LABEL, SIZE, DanmakuSetting.getTextScale(), value -> {
            DanmakuSetting.putTextScale(value);
            apply(player);
        });
        ChipRow.single(binding.speed, SPEED_LABEL, SPEED, DanmakuSetting.getDurationMs(), value -> {
            DanmakuSetting.putDurationMs(value.longValue());
            apply(player);
        });
        ChipRow.single(binding.area, AREA_LABEL, AREA, DanmakuSetting.getScrollAreaRatio(), value -> {
            DanmakuSetting.putScrollAreaRatio(value);
            apply(player);
        });
        // 屏蔽类型：点亮 = 不显示这一类
        binding.block.removeAllViews();
        ChipRow.toggle(binding.block, "滚动", !DanmakuSetting.isShowScroll(), blocked -> {
            DanmakuSetting.putShowScroll(!blocked);
            apply(player);
        });
        ChipRow.toggle(binding.block, "顶部", !DanmakuSetting.isShowTop(), blocked -> {
            DanmakuSetting.putShowTop(!blocked);
            apply(player);
        });
        ChipRow.toggle(binding.block, "底部", !DanmakuSetting.isShowBottom(), blocked -> {
            DanmakuSetting.putShowBottom(!blocked);
            apply(player);
        });
        binding.reset.setOnClickListener(view -> {
            DanmakuSetting.resetAppearance();
            DanmakuSetting.resetTiming();
            DanmakuSetting.resetDensity();
            DanmakuSetting.resetDisplay();
            apply(player);
            bind(binding, player, dialog);
        });
        binding.more.setOnClickListener(view -> {
            FragmentActivity activity = dialog.requireActivity();
            dialog.dismiss();
            DanmakuSettingDialog.create().player(player).advanced().show(activity);
        });
    }

    private static void apply(PlayerManager player) {
        if (player != null) player.setDanmakuConfig(DanmakuSetting.getConfig());
    }

    public static final class Bottom extends BaseBottomSheetDialog {

        private final PlayerManager player;
        private DialogDanmakuQuickBinding binding;

        Bottom(PlayerManager player) {
            this.player = player;
        }

        @Override
        protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
            return binding = DialogDanmakuQuickBinding.inflate(inflater, container, false);
        }

        @Override
        protected void initView() {
            bind(binding, player, this);
        }
    }

    public static final class Side extends BaseSideSheetDialog {

        private final PlayerManager player;
        private DialogDanmakuQuickBinding binding;

        Side(PlayerManager player) {
            this.player = player;
        }

        @Override
        protected int getWidth() {
            int screen = requireActivity().getWindow().getDecorView().getWidth();
            return Math.min(ResUtil.dp2px(420), Math.max(ResUtil.dp2px(320), screen / 2));
        }

        @Override
        protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
            return binding = DialogDanmakuQuickBinding.inflate(inflater, container, false);
        }

        @Override
        protected void initView() {
            bind(binding, player, this);
        }
    }
}
