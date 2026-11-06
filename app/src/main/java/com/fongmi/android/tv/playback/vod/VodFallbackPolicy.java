package com.fongmi.android.tv.playback.vod;

import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;

import java.util.ArrayList;
import java.util.List;

class VodFallbackPolicy {

    private final VodPlaybackController controller;
    private final VodPlaybackState state;
    private final VodPlaybackHost host;

    VodFallbackPolicy(VodPlaybackController controller, VodPlaybackState state, VodPlaybackHost host) {
        this.controller = controller;
        this.state = state;
        this.host = host;
    }

    void playbackError() {
        fallbackToNextLineOrSource();
    }

    void emptyFlag() {
        fallbackToNextLineOrSource();
    }

    void emptyDetail() {
        fallbackToNextSource(false);
    }

    void manualSwitchSource() {
        fallbackToNextSource(true);
    }

    void search(String keyword, boolean autoFallback) {
        state.getSources().clear();
        state.setSearchKeyword(keyword);
        state.setAutoFallback(autoFallback);
        state.setSelectFirstSource(autoFallback);
        host.onSearchStarted(keyword);
        host.requestSearch(getSearchableSites(), keyword);
    }

    void onSearchResult(Result result) {
        List<Vod> items = new ArrayList<>(result.getList());
        items.removeIf(this::mismatch);
        // 二次开发：各站结果分批到达，要累积（原来每批覆盖，界面却是追加，换源列表会丢项、重复）
        state.addSources(items);
        host.renderSources(state.getSources());
        if (state.isSelectFirstSource()) nextSource();
        if (items.isEmpty()) return;
        host.onSearchResult();
    }

    private void fallbackToNextLineOrSource() {
        if (!host.isSiteChangeable()) return;
        if (fallbackToNextLine()) return;
        fallbackToNextSource(false);
    }

    private boolean fallbackToNextLine() {
        // 二次开发：记下失败的线路，只往后找没失败过的（原版只取下一条，坏线路会被焦点回调选回来，造成来回打转）
        state.addFailedFlag(state.getFlag().getFlag());
        List<Flag> flags = state.getFlags();
        for (int i = state.getFlagPosition() + 1; i < flags.size(); i++) {
            Flag flag = flags.get(i);
            if (state.isFailedFlag(flag.getFlag())) continue;
            host.showSwitchLine(flag);
            controller.selectFlag(flag);
            return true;
        }
        return false;
    }

    private void fallbackToNextSource(boolean force) {
        if (!state.hasSources()) search(host.getVodName(), true);
        else if (state.isAutoFallback() || force) nextSource();
    }

    private void nextSource() {
        if (!state.hasSources()) return;
        Vod item = state.removeFirstSource();
        host.renderSources(state.getSources());
        host.showSwitchSource(item);
        state.addFailedId(host.getVodId());
        state.setSelectFirstSource(false);
        controller.fallbackSource(item);
    }

    private List<Site> getSearchableSites() {
        List<Site> sites = new ArrayList<>();
        for (Site site : VodConfig.get().getSites()) if (isPass(site)) sites.add(site);
        return sites;
    }

    private boolean isPass(Site item) {
        // 二次开发：从首页索引站点进来（msearch:）本来就要全站找片，不受 changeable 限制；
        // 很多配置把所有站都设成 changeable=0，不放开的话一个站都搜不到。播放失败的自动换源仍然遵守 changeable
        boolean fromIndex = host.getVodId().startsWith(VodPlaybackController.SEARCH_PREFIX);
        if (state.isAutoFallback() && !fromIndex && !item.isChangeable()) return false;
        return item.isSearchable();
    }

    private boolean mismatch(Vod item) {
        if (host.getVodId().equals(item.getId())) return true;
        if (state.hasFailedId(item.getId())) return true;
        if (state.isAutoFallback()) return !item.getName().equals(state.getSearchKeyword());
        return !item.getName().contains(state.getSearchKeyword());
    }
}
