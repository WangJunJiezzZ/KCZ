package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.databinding.DialogDanmakuSettingBinding;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;

import java.util.function.BiConsumer;

public final class DanmakuSettingDialog {

    /**
     * 二次开发：简洁版弹幕设置的入口（手机版播放页注册，见 mobile 的 DanmakuQuickDialog）。
     * 电视版不注册，仍直接打开下面的完整面板；简洁面板里的「更多设置」用 advanced() 打开完整面板。
     */
    private static BiConsumer<FragmentActivity, PlayerManager> simpleOpener;

    private PlayerManager player;
    private boolean advanced;

    public static DanmakuSettingDialog create() {
        return new DanmakuSettingDialog();
    }

    public static void setSimpleOpener(BiConsumer<FragmentActivity, PlayerManager> opener) {
        simpleOpener = opener;
    }

    /** 跳过简洁面板，直接打开完整设置 */
    public DanmakuSettingDialog advanced() {
        this.advanced = true;
        return this;
    }

    private static DialogDanmakuSettingBinding inflate(LayoutInflater inflater, ViewGroup container) {
        return DialogDanmakuSettingBinding.inflate(inflater, container, false);
    }

    public DanmakuSettingDialog player(PlayerManager player) {
        this.player = player;
        return this;
    }

    public void show(FragmentActivity activity) {
        if (!advanced && simpleOpener != null && !Util.isLeanback()) {
            simpleOpener.accept(activity, player);
            return;
        }
        FragmentManager manager = activity.getSupportFragmentManager();
        for (Fragment fragment : manager.getFragments()) if (fragment instanceof BottomSheet || fragment instanceof SideSheet) return;
        if (Util.isFullscreenLand(activity) || Util.isLeanback()) new SideSheet(player).show(manager, null);
        else new BottomSheet(player).show(manager, null);
    }

    public static final class BottomSheet extends BaseBottomSheetDialog {

        private final PlayerManager player;
        private DialogDanmakuSettingBinding binding;
        private DanmakuSettingPanel panel;

        BottomSheet(PlayerManager player) {
            this.player = player;
        }

        @Override
        protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
            return binding = DanmakuSettingDialog.inflate(inflater, container);
        }

        @Override
        protected int getMaxHeight() {
            return ResUtil.getScreenHeight() / 2;
        }

        @Override
        protected void initView() {
            panel = new DanmakuSettingPanel(binding, player);
            panel.bind();
        }

        @Override
        public void onDestroyView() {
            if (panel != null) panel.release();
            panel = null;
            binding = null;
            super.onDestroyView();
        }
    }

    public static final class SideSheet extends BaseSideSheetDialog {

        private final PlayerManager player;
        private DialogDanmakuSettingBinding binding;
        private DanmakuSettingPanel panel;

        SideSheet(PlayerManager player) {
            this.player = player;
        }

        @Override
        protected int getWidth() {
            return Math.min(ResUtil.dp2px(420), ResUtil.getScreenWidth() / 2);
        }

        @Override
        protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
            return binding = DanmakuSettingDialog.inflate(inflater, container);
        }

        @Override
        protected void initView() {
            panel = new DanmakuSettingPanel(binding, player);
            panel.bind();
        }

        @Override
        public void onDestroyView() {
            if (panel != null) panel.release();
            panel = null;
            binding = null;
            super.onDestroyView();
        }
    }
}
