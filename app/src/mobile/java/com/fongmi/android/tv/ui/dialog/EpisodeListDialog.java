package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.databinding.DialogEpisodeListBinding;
import com.fongmi.android.tv.ui.adapter.EpisodeAdapter;
import com.fongmi.android.tv.ui.base.ViewType;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.List;

public class EpisodeListDialog extends BaseSideSheetDialog implements EpisodeAdapter.OnClickListener {

    private DialogEpisodeListBinding binding;
    private EpisodeAdapter adapter;
    private List<Episode> episodes;

    public static EpisodeListDialog create() {
        return new EpisodeListDialog();
    }

    public EpisodeListDialog episodes(List<Episode> episodes) {
        this.episodes = episodes;
        return this;
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof EpisodeListDialog) return;
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogEpisodeListBinding.inflate(inflater, container, false);
    }

    @Override
    protected int getWidth() {
        // 二次开发：原来上限是 ResUtil.getScreenWidth()/3，全屏横屏时它可能拿到竖屏宽度（1440/3=480px），抽屉被压得很窄；
        // 改按当前窗口实际宽度算，上限放宽到一半，剧集名（常带文件大小、清晰度）能显示完整些
        int screen = requireActivity().getWindow().getDecorView().getWidth();
        if (screen <= 0) screen = ResUtil.getScreenWidth(requireActivity());
        int width = ResUtil.dp2px(280);
        int maxWidth = Math.max(width, screen / 2);
        for (Episode item : episodes) width = Math.max(width, ResUtil.getTextWidth(item.getName(), 14) + ResUtil.dp2px(48));
        return Math.min(width, maxWidth);
    }

    @Override
    protected void initView() {
        setRecyclerView();
        adapter.addAll(episodes);
        binding.recycler.scrollToPosition(adapter.getPosition());
    }

    private void setRecyclerView() {
        binding.recycler.setHasFixedSize(true);
        binding.recycler.setItemAnimator(null);
        binding.recycler.setAdapter(adapter = new EpisodeAdapter(this, ViewType.GRID));
    }

    @Override
    public void onItemClick(Episode item) {
        ((EpisodeAdapter.OnClickListener) requireActivity()).onItemClick(item);
        dismiss();
    }
}
