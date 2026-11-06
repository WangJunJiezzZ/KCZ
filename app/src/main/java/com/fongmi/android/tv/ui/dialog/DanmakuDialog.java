package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.databinding.DialogDanmakuBinding;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.setting.DanmakuSetting;
import com.fongmi.android.tv.ui.adapter.DanmakuAdapter;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.FileChooser;

public final class DanmakuDialog extends BaseBottomSheetDialog implements DanmakuAdapter.OnClickListener {

    private final DanmakuAdapter adapter;
    private DialogDanmakuBinding binding;
    private PlayerManager player;
    private Runnable onShowChanged;

    public DanmakuDialog() {
        this.adapter = new DanmakuAdapter(this);
    }

    public static DanmakuDialog create() {
        return new DanmakuDialog();
    }

    public DanmakuDialog player(PlayerManager player) {
        this.player = player;
        return this;
    }

    /** 弹幕开关变化后通知页面（手机版用来刷新控制栏图标）。 */
    public DanmakuDialog onShowChanged(Runnable onShowChanged) {
        this.onShowChanged = onShowChanged;
        return this;
    }

    /** 打开 / 关闭弹幕显示：记住设置并立即作用到播放器。 */
    static void setShow(PlayerManager player, boolean show) {
        DanmakuSetting.putShow(show);
        player.setDanmakuEnabled(show);
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof DanmakuDialog) return;
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogDanmakuBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        binding.recycler.setItemAnimator(null);
        binding.recycler.setHasFixedSize(true);
        binding.recycler.setAdapter(adapter.addAll(player.getDanmakus()));
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 16));
        binding.recycler.post(() -> binding.recycler.scrollToPosition(adapter.getSelected()));
        binding.recycler.setVisibility(adapter.getItemCount() == 0 ? View.GONE : View.VISIBLE);
        binding.search.setVisibility(player.getMetadata() == null || DanmakuSetting.getEffectiveApiUrl().isEmpty() ? View.GONE : View.VISIBLE);
        updateToggle();
        binding.toggle.requestFocus();
    }

    private void updateToggle() {
        binding.toggle.setText(DanmakuSetting.isShow() ? R.string.danmaku_on : R.string.danmaku_off);
    }

    private void onToggle(View view) {
        setShow(player, !DanmakuSetting.isShow());
        updateToggle();
        if (onShowChanged != null) onShowChanged.run();
    }

    @Override
    protected void initEvent() {
        binding.toggle.setOnClickListener(this::onToggle);
        binding.search.setOnClickListener(this::onSearch);
        binding.choose.setOnClickListener(this::onChoose);
        binding.setting.setOnClickListener(this::onSetting);
    }

    private void onSearch(View view) {
        DanmakuSearchDialog.create().player(player).show(getActivity());
        dismiss();
    }

    private void onChoose(View view) {
        FileChooser.from(launcher).show(new String[]{"text/*"});
        player.pause();
    }

    private void onSetting(View view) {
        DanmakuSettingDialog.create().player(player).show(getActivity());
        dismiss();
    }

    @Override
    public void onItemClick(Danmaku item) {
        player.toggleDanmaku(item);
        // 弹幕关着时手动选了一条，说明想看：顺便打开
        if (player.haveDanmaku() && !DanmakuSetting.isShow()) {
            setShow(player, true);
            if (onShowChanged != null) onShowChanged.run();
        }
        dismiss();
    }

    private final ActivityResultLauncher<Intent> launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
        player.setDanmaku(Danmaku.from(FileChooser.getPathFromUri(result.getData().getData())));
        dismiss();
    });
}
