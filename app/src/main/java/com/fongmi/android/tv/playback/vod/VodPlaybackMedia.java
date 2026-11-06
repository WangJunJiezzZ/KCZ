package com.fongmi.android.tv.playback.vod;

import androidx.media3.common.MediaMetadata;

import com.fongmi.android.tv.api.DanmakuApi;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.player.media.MediaItemFactory;
import com.fongmi.android.tv.setting.DanmakuSetting;

import java.util.function.Consumer;

public final class VodPlaybackMedia {

    public static MediaMetadata metadata(History history, Episode episode) {
        String title = history.getVodName();
        String name = episode.getName();
        if (name.equals(title)) name = "";
        return MediaItemFactory.buildMetadata(title, name, history.getVodPic(), name);
    }

    public static void searchDanmaku(Result result, History history, Episode episode, Consumer<Danmaku> set, Consumer<Danmaku> add) {
        if (!DanmakuApi.canSearch()) return;
        if (VodConfig.get().getSite(result.getKey()).getDanmaku() == 0) return;
        // 第一条直接加载，其余放进弹幕列表，弹窗里可以直接换，不用再手动搜
        DanmakuApi.search(history.getVodName(), episode.getName(), items -> {
            if (items.isEmpty()) return;
            boolean keepSpider = DanmakuSetting.isSpiderFirst() && !result.getDanmaku().isEmpty();
            for (int i = 0; i < items.size(); i++) {
                if (i == 0 && !keepSpider) set.accept(items.get(i));
                else add.accept(items.get(i));
            }
        }, error -> {});
    }
}
