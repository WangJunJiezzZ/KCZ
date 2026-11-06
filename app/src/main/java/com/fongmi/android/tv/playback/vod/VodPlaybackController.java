package com.fongmi.android.tv.playback.vod;

import androidx.media3.common.C;
import androidx.media3.common.MediaMetadata;

import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.playback.PlaybackResult;
import com.fongmi.android.tv.utils.NetdiskLogin;
import com.fongmi.android.tv.utils.NetdiskRank;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class VodPlaybackController {

    private static final String PUSH_PREFIX = "push://";
    private static final String SEARCH_PREFIX = "msearch:";

    private final VodPlaybackHost host;
    private final VodPlaybackState state;
    private final VodHistoryPolicy historyPolicy;
    private final VodFallbackPolicy fallbackPolicy;
    private History lastHistory;

    public VodPlaybackController(VodPlaybackHost host, VodPlaybackState state) {
        this.host = host;
        this.state = state;
        this.historyPolicy = new VodHistoryPolicy();
        this.fallbackPolicy = new VodFallbackPolicy(this, state, host);
    }

    public void reset() {
        clearPreload();
        state.reset();
    }

    public void checkId() {
        String id = resolveVodId();
        if (id.isEmpty() || id.startsWith(SEARCH_PREFIX)) detailEmpty(false);
        else if (!state.isDetailRequested(host.getVodKey(), id)) requestDetail();
    }

    private String resolveVodId() {
        String id = host.getVodId();
        if (!id.startsWith(PUSH_PREFIX)) return id;
        host.usePushId(id.substring(PUSH_PREFIX.length()));
        return host.getVodId();
    }

    public void requestDetail() {
        String key = host.getVodKey();
        String id = host.getVodId();
        state.setDetailRequest(key, id);
        host.requestDetail(key, id);
    }

    public void onDetailResult(VodDetailResult detail) {
        if (detail == null || !detail.matches(host.getVodKey(), host.getVodId())) return;
        Result result = detail.result();
        if (result.getList().isEmpty()) detailEmpty(result.hasMsg());
        else detailLoaded(result.getVod());
        host.showDetailMessage(result.getMsg());
    }

    public void updateVod(Vod item) {
        History history = state.getHistory();
        replaceVodId(history, item.getId());
        mergeFlags(item.getFlags());
        updateHistory(history, item);
        host.renderVodUpdate(item);
        publishActivePlaybackMetadata();
    }

    private void updateHistory(History history, Vod item) {
        String pic = item.getPic();
        String name = item.getName();
        boolean hasPic = !pic.isEmpty();
        boolean hasName = !name.isEmpty();
        if (hasPic) history.setVodPic(pic);
        if (hasName) history.setVodName(name);
        if (hasName || hasPic) historyPolicy.saveCurrent(history);
    }

    private void publishActivePlaybackMetadata() {
        Episode episode = findEpisode(state.getActiveRequest());
        if (episode != null) publishPlaybackMetadata(episode);
    }

    private void replaceVodId(History history, String id) {
        if (id.isEmpty() || id.equals(host.getVodId())) return;
        String oldKey = host.getHistoryKey();
        host.setVodId(id);
        String newKey = host.getHistoryKey();
        history.replace(newKey);
        Keep.replace(oldKey, newKey);
    }

    public void onPlaybackResult(PlaybackResult<VodPlayRequest> playback) {
        if (playback == null || cannotApply(playback)) return;
        applyPlaybackResult(playback.result(), playback.request());
    }

    private void applyPlaybackResult(Result result, VodPlayRequest request) {
        Episode episode = findEpisode(request);
        if (episode == null) return;
        applyPlaybackState(result, request);
        markNetdiskOk(result);
        renderPlaybackResult(result);
        updatePlaybackPosition(result);
        host.loadDanmaku(result, state.getHistory(), episode);
        startPlayback(result, startPositionMs(), episode);
        updatePreload(result);
    }

    /** 二次开发：网盘线路拿到了真实播放地址 → 记住这个网盘能用，下次排前面 */
    private void markNetdiskOk(Result result) {
        if (!state.hasFlags() || result.hasMsg() || result.getRealUrl().isEmpty()) return;
        NetdiskRank.markOk(NetdiskLogin.diskOfFlag(state.getFlag().getFlag()));
    }

    private void applyPlaybackState(Result result, VodPlayRequest request) {
        state.setQuality(result);
        state.setPlayingRequest(request);
        state.setUseParse(result.isUseParse());
        result.getUrl().set(state.getQualityPosition());
    }

    private void renderPlaybackResult(Result result) {
        host.renderUseParse(state.isUseParse());
        host.renderQuality(result, result.getUrl().isMulti());
        if (result.hasDesc()) host.renderDescription(result.getDesc());
        if (result.hasArtwork()) host.renderArtwork(result.getArtwork());
    }

    private void updatePlaybackPosition(Result result) {
        if (result.hasPosition()) state.getHistory().setPosition(result.getPosition());
    }

    private void updatePreload(Result result) {
        if (result.needParse() || state.isUseParse()) clearPreload();
        else preloadNextEpisode();
    }

    private void startPlayback(Result result, long startPositionMs, Episode episode) {
        MediaMetadata metadata = publishPlaybackMetadata(episode);
        host.startPlayback(result, state.isUseParse(), startPositionMs, metadata);
    }

    public void onSearchResult(Result result) {
        fallbackPolicy.onSearchResult(result);
    }

    public void selectFlag(Flag item) {
        // 电视版焦点落到哪条线路就会回调这里，程序设置选中位置也会触发；
        // 落在已选中的线路上什么都不做，也不算用户手动选择（否则测速期间会被当成用户点了预选线路而直接开播）
        Flag target = findFlag(item);
        if (target != null && target.isSelected()) return;
        stopWaitingProbe();
        state.setUserPickedFlag(true);
        selectFlag(item, false);
    }

    private void selectFlag(Flag item, boolean force) {
        if (!state.hasFlags()) return;
        Flag selected = resolveFlag(item);
        if (!force && selected.isSelected()) return;
        clearPreload();
        for (Flag flag : state.getFlags()) flag.setSelected(selected);
        host.renderFlagSelection(selected);
        host.renderEpisodes(selected.getEpisodes());
        host.renderQualityVisible(false);
        seamless(selected);
    }

    public void selectEpisode(Episode item) {
        if (!state.hasFlags()) return;
        stopWaitingProbe();
        saveCurrentHistory();
        Flag selected = state.getFlag();
        for (Flag flag : state.getFlags()) flag.toggle(flag == selected, item);
        historyPolicy.updateEpisode(state.getHistory(), state.getFlag(), item);
        host.renderEpisodeSelection(item);
        if (host.isFullscreenForPlayback()) host.showEpisodeReady(item);
        playEpisode(item);
    }

    private void playEpisode(Episode item) {
        Result result = state.consumePreload(host.getVodKey(), state.getFlag(), item);
        if (result == null) {
            clearPreload();
            host.stopPlaybackForRefresh();
            requestSelectedEpisode();
        } else {
            host.stopPlaybackForRefresh();
            applyPlaybackResult(result, VodPlayRequest.create(host.getVodKey(), state.getFlag(), item));
        }
    }

    public void selectQuality(Result result) {
        if (!state.hasEpisode()) return;
        state.setQuality(result);
        state.setQualityPosition(result.getUrl().getPosition());
        clearPreload();
        startPlayback(result, host.getPlayerPosition(), state.getEpisode());
        preloadNextEpisode();
    }

    public void selectParse(Parse item) {
        VodConfig.get().setParse(item);
        refresh();
    }

    private void mergeFlags(List<Flag> items) {
        if (items.isEmpty()) return;
        if (state.hasFlags()) {
            Flag activated = state.getFlag();
            for (Flag item : items) mergeFlag(activated, item);
            host.renderFlags(state.getFlags());
        } else {
            state.setFlags(items);
            host.renderFlags(state.getFlags());
        }
    }

    public void selectSource(Vod item) {
        switchSource(item, false);
    }

    void fallbackSource(Vod item) {
        switchSource(item, true);
    }

    private void switchSource(Vod item, boolean autoFallback) {
        stopWaitingProbe();
        state.cancelProbe();
        state.setAutoFallback(autoFallback);
        saveCurrentHistory();
        clearPreload();
        state.clearPlayRequest();
        host.prepareSource(item);
        requestDetail();
    }

    public void search(String keyword) {
        fallbackPolicy.search(keyword, false);
    }

    public void manualSwitchSource() {
        fallbackPolicy.manualSwitchSource();
    }

    public void playbackError(String msg) {
        clearPreload();
        host.resetPlaybackForError(msg);
        // 二次开发：网盘线路失败不自动换线，交给界面弹对应网盘的扫码登录（没登录就不该切走）
        String disk = NetdiskLogin.detect(msg);
        if (disk != null) NetdiskRank.markBad(disk); // 明确报没登录 / Cookie 失效：这个网盘暂时不能用
        if (disk == null && state.hasFlags()) disk = NetdiskLogin.diskOfFlag(state.getFlag().getFlag());
        if (disk != null && host.handleNetdiskError(disk, msg)) return;
        fallbackPolicy.playbackError();
    }

    public void playbackEnded() {
        nextEpisode(true);
    }

    public void replay() {
        if (state.getHistory() != null) state.getHistory().setPosition(C.TIME_UNSET);
        if (host.isPlayerEmpty()) refresh();
        else host.replay(startPositionMs());
    }

    public void refresh() {
        saveCurrentHistory();
        clearPreload();
        host.stopPlaybackForRefresh();
        restorePlaybackSelection(state.getPlayingRequest());
        requestSelectedEpisode();
    }

    public void onPlaybackServiceReady() {
        syncPlaybackMetadata();
        VodPlayRequest request = state.getPlayingRequest();
        if (request == null || host.hasPlaybackSession()) return;
        restorePlaybackSelection(request);
        requestSelectedEpisode();
    }

    private void syncPlaybackMetadata() {
        MediaMetadata metadata = state.getPlaybackMetadata();
        if (metadata != null) host.renderPlaybackMetadata(metadata);
    }

    private void requestSelectedEpisode() {
        if (state.hasEpisode()) requestPlayer(state.getFlag(), state.getEpisode());
    }

    public void nextEpisode(boolean notify) {
        if (state.getHistory() != null && state.getHistory().isRevPlay()) prevEpisode(notify, true);
        else nextEpisode(notify, false);
    }

    public void prevEpisode(boolean notify) {
        if (state.getHistory() != null && state.getHistory().isRevPlay()) nextEpisode(notify, true);
        else prevEpisode(notify, false);
    }

    private void nextEpisode(boolean notify, boolean reversed) {
        if (!state.hasEpisode()) return;
        Episode item = getRelativeEpisode(1);
        if (!item.isSelected()) selectEpisode(item);
        else if (notify) host.showNoNext(reversed);
    }

    private void prevEpisode(boolean notify, boolean reversed) {
        if (!state.hasEpisode()) return;
        Episode item = getRelativeEpisode(-1);
        if (!item.isSelected()) selectEpisode(item);
        else if (notify) host.showNoPrev(reversed);
    }

    public void reverseEpisode(boolean scroll) {
        if (!state.hasFlags()) return;
        for (Flag flag : state.getFlags()) reverseEpisodes(flag);
        host.renderReverseEpisodes(state.getFlag().getEpisodes(), scroll);
    }

    private void reverseEpisodes(Flag flag) {
        List<Episode> episodes = flag.getEpisodes();
        int position = flag.getPosition();
        Collections.reverse(episodes);
        if (position >= 0 && position < episodes.size()) flag.setPosition(episodes.size() - position - 1);
    }

    private void saveCurrentHistory() {
        if (state.getPlayingRequest() == null || !host.canTrackPlaybackProgress()) historyPolicy.save(currentHistory());
        else saveHistory(false, System.currentTimeMillis(), host.getPlayerPosition(), host.getPlayerDuration());
    }

    public void saveHistory(boolean exit, long time, long position, long duration) {
        History history = exit ? historyForExit() : currentHistory();
        if (host.isLivePlayback()) historyPolicy.saveVisit(history, exit, time);
        else historyPolicy.saveProgress(history, exit, time, position, duration);
    }

    public void onTimeChanged(long time, long position, long duration) {
        History history = currentHistory();
        historyPolicy.updateProgress(history, time, position, duration);
        if (history != null && history.getEnding() > 0 && history.getEnding() + position >= duration) nextEpisode(false);
    }

    public long startPositionMs() {
        return historyPolicy.startPositionMs(state.getHistory());
    }

    private History currentHistory() {
        History history = state.getHistory();
        if (history != null) lastHistory = history;
        return history;
    }

    private History historyForExit() {
        History history = currentHistory();
        return history == null ? lastHistory : history;
    }

    public void onPreloadResult(PlaybackResult<VodPlayRequest> preload) {
        if (!isPendingPreload(preload)) return;
        applyPreload(preload.request(), preload.result());
    }

    private boolean isPendingPreload(PlaybackResult<VodPlayRequest> preload) {
        return preload != null && isPreloading(preload.request()) && state.getPreloadResult() == null;
    }

    private void applyPreload(VodPlayRequest request, Result result) {
        Episode episode = findEpisode(request);
        if (isPreloadable(request, result, episode)) startPreload(result, episode);
        else clearPreload();
    }

    private void startPreload(Result result, Episode episode) {
        result.getUrl().set(state.getQualityPosition());
        long startPositionMs = getPreloadStartPositionMs(result);
        MediaMetadata metadata = VodPlaybackMedia.metadata(state.getHistory(), episode);
        if (host.preloadPlayback(result, startPositionMs, metadata)) state.completePreload(result);
        else clearPreload();
    }

    public void setOpening(long opening) {
        if (state.getHistory() != null) state.getHistory().setOpening(opening);
    }

    public void setEnding(long ending) {
        if (state.getHistory() != null) state.getHistory().setEnding(ending);
    }

    public void setScale(int scale) {
        if (state.getHistory() != null) state.getHistory().setScale(scale);
    }

    public void setRevSort(boolean revSort) {
        if (state.getHistory() != null) state.getHistory().setRevSort(revSort);
    }

    public void setRevPlay(boolean revPlay) {
        if (state.getHistory() != null) state.getHistory().setRevPlay(revPlay);
    }

    private void detailEmpty(boolean shouldFinish) {
        if (host.isFromCollect() || shouldFinish) host.finishVod();
        else showDetailFallback();
    }

    private void showDetailFallback() {
        String name = host.getVodName();
        if (name.isEmpty()) host.renderEmptyDetail();
        else fallbackDetail(name);
    }

    private void fallbackDetail(String name) {
        host.renderFallbackName(name);
        host.onDetailFallbackScheduled();
        fallbackPolicy.emptyDetail();
    }

    private void detailLoaded(Vod item) {
        VodPlayRequest activeRequest = state.getActiveRequest();
        boolean fresh = History.find(host.getHistoryKey()) == null;
        // 二次开发：先按网盘规则排线路（能用的网盘原画在前），要在生成观看记录之前，新片的默认线路才是排第一的
        NetdiskRank.sort(item.getFlags());
        History history = applyDetail(item);
        preferUsableNetdisk(history);
        if (!state.hasFlags()) fallbackPolicy.emptyFlag();
        else if (!startLineProbe(activeRequest, history, fresh)) restoreDetail(activeRequest, history);
    }

    /**
     * 二次开发：线路测速。
     * 没看过的片子：先测速，等结果出来再播排第一的线路（返回 true，调用方暂不播放）。
     * 看过的片子：照常按历史线路播，测速在后台进行，结果只用来排序和加标签（返回 false）。
     */
    private boolean startLineProbe(VodPlayRequest activeRequest, History history, boolean fresh) {
        String probeKey = host.getVodKey() + "\n" + host.getVodId();
        if (activeRequest != null || state.isProbed(probeKey)) return false;
        List<LineProbe.Target> targets = LineProbe.targets(state.getFlags(), history);
        if (targets.size() < 2) return false;
        int token = state.beginProbe(probeKey, fresh);
        if (!host.requestLineProbe(new LineProbe.Request(token, host.getVodKey(), targets))) {
            state.setWaitingProbe(false);
            return false;
        }
        if (!fresh) return false;
        previewFlag(history.getFlag());
        host.showLineProbing(targets.size());
        return true;
    }

    /**
     * 二次开发：上次看的线路是没登录过的网盘（如百度），而现在有能用的网盘线路（如已登录的夸克）时，改用能用的那条，
     * 否则「继续观看」一打开就去弹百度扫码。线路已按 NetdiskRank 排过序，第一条就是最好的
     */
    private void preferUsableNetdisk(History history) {
        if (history == null || !state.hasFlags()) return;
        Flag top = state.getFlags().get(0);
        Flag last = resolveFlag(history.getFlag());
        if (NetdiskRank.rank(last) == 2 && NetdiskRank.rank(top) == 0) history.setVodFlag(top.getFlag());
    }

    /** 测速期间先把历史线路的选集列出来（不播放），用户可以直接点 */
    private void previewFlag(Flag item) {
        Flag flag = resolveFlag(item);
        for (Flag f : state.getFlags()) f.setSelected(flag);
        host.renderFlagSelection(flag);
        host.renderEpisodes(flag.getEpisodes());
        host.renderQualityVisible(false);
    }

    public void onLineProbeResult(LineProbe.Report report) {
        if (report == null || !state.isCurrentProbe(report.token()) || !state.hasFlags()) return;
        Map<String, LineProbe.Result> results = report.results();
        for (Flag flag : state.getFlags()) flag.setBadge(LineRank.badge(results.get(flag.getFlag())));
        boolean waiting = state.isWaitingProbe();
        state.setWaitingProbe(false);
        // 用户已经手动选过线路就不重排，免得按钮位置在眼前跳动
        if (!state.isUserPickedFlag()) LineRank.sort(state.getFlags(), results);
        host.renderFlags(state.getFlags());
        if (waiting) {
            host.hideLineProbing();
            History history = state.getHistory();
            Flag best = findPlayableFlag(history);
            if (best != null) history.setVodFlag(best.getFlag());
            restoreDetail(null, history);
        } else {
            host.renderFlagSelection(state.getFlag());
        }
    }

    /** 排序后第一条能找到「要看的那一集」的线路（不同线路剧集命名可能不一致，找不到就会播不出来） */
    private Flag findPlayableFlag(History history) {
        boolean strict = host.getVodMark().isEmpty();
        for (Flag flag : state.getFlags()) if (flag.find(history.getVodRemarks(), strict) != null) return flag;
        return null;
    }

    private boolean stopWaitingProbe() {
        if (!state.isWaitingProbe()) return false;
        state.setWaitingProbe(false);
        host.hideLineProbing();
        return true;
    }

    private History applyDetail(Vod item) {
        item.checkPic(host.getVodPic());
        item.checkName(host.getVodName());
        List<Flag> flags = item.getFlags();
        state.setFlags(flags);
        History history = historyPolicy.findOrCreate(host.getHistoryKey(), host.getVodMark(), item);
        state.setHistory(history);
        lastHistory = history;
        host.renderDetail(item, history);
        host.renderFlags(flags);
        host.renderHistory(history);
        host.onDetailFallbackCancelled();
        return history;
    }

    private void restoreDetail(VodPlayRequest activeRequest, History history) {
        restoreDetailSelection(activeRequest, history);
        restoreEpisodeOrder(history);
        resumePlaybackIfNeeded();
    }

    private void restoreDetailSelection(VodPlayRequest activeRequest, History history) {
        if (restorePlaybackSelection(activeRequest)) {
            publishPlaybackMetadata(state.getEpisode());
        } else {
            selectFlag(history.getFlag(), true);
        }
    }

    private void restoreEpisodeOrder(History history) {
        if (history.isRevSort()) reverseEpisode(true);
    }

    private void resumePlaybackIfNeeded() {
        if (state.getPlayingRequest() != null && !host.hasPlaybackSession()) requestSelectedEpisode();
    }

    private boolean restorePlaybackSelection(VodPlayRequest request) {
        Flag flag = findFlag(request);
        Episode episode = findEpisode(flag, request);
        if (flag == null || episode == null) return false;
        for (Flag item : state.getFlags()) item.setSelected(flag);
        for (Flag item : state.getFlags()) item.toggle(item == flag, episode);
        historyPolicy.updateEpisode(state.getHistory(), flag, episode);
        host.renderFlagSelection(flag);
        host.renderEpisodes(flag.getEpisodes());
        host.renderEpisodeSelection(episode);
        host.renderQualityVisible(state.getQuality().getUrl().isMulti());
        return true;
    }

    private void requestPlayer(Flag flag, Episode episode) {
        historyPolicy.updateEpisode(state.getHistory(), flag, episode);
        VodPlayRequest request = VodPlayRequest.create(host.getVodKey(), flag, episode);
        state.setPendingRequest(request);
        publishPlaybackMetadata(episode);
        host.requestPlayer(request);
    }

    private void preloadNextEpisode() {
        Episode episode = findNextEpisodeToPreload();
        if (episode == null) clearPreload();
        else requestPreload(episode);
    }

    private void requestPreload(Episode episode) {
        VodPlayRequest request = VodPlayRequest.create(host.getVodKey(), state.getFlag(), episode);
        state.beginPreload(request);
        host.requestPreload(request);
    }

    private Episode findNextEpisodeToPreload() {
        if (!state.hasEpisode() || !host.canPreloadNext()) return null;
        History history = state.getHistory();
        int offset = history != null && history.isRevPlay() ? -1 : 1;
        Episode episode = getRelativeEpisode(offset);
        return episode.isSelected() ? null : episode;
    }

    private boolean isPreloading(VodPlayRequest request) {
        VodPlayRequest pending = state.getPreloadRequest();
        return pending != null && pending.matches(request);
    }

    private boolean isPreloadable(VodPlayRequest request, Result result, Episode episode) {
        return episode != null && host.canPreloadNext() && request.accepts(result) && !result.hasMsg() && !result.needParse() && !result.isUseParse() && result.getDrm() == null && !result.getRealUrl().isEmpty();
    }

    private long getPreloadStartPositionMs(Result result) {
        History history = state.getHistory();
        long opening = history == null ? C.TIME_UNSET : history.getOpening();
        long position = result.hasPosition() ? result.getPosition() : C.TIME_UNSET;
        return Math.max(0, Math.max(opening, position));
    }

    private Episode findEpisode(VodPlayRequest request) {
        if (request == null) return null;
        for (Flag flag : state.getFlags()) {
            Episode episode = findEpisode(flag, request);
            if (episode != null) return episode;
        }
        return null;
    }

    private Episode findEpisode(Flag flag, VodPlayRequest request) {
        if (flag == null || request == null) return null;
        for (Episode episode : flag.getEpisodes()) if (request.matches(host.getVodKey(), flag, episode)) return episode;
        return null;
    }

    private Flag findFlag(VodPlayRequest request) {
        if (request == null) return null;
        for (Flag flag : state.getFlags()) if (findEpisode(flag, request) != null) return flag;
        return null;
    }

    private void clearPreload() {
        state.clearPreload();
        host.clearPreload();
    }

    private MediaMetadata publishPlaybackMetadata(Episode episode) {
        MediaMetadata metadata = VodPlaybackMedia.metadata(state.getHistory(), episode);
        state.setPlaybackMetadata(metadata);
        host.renderPlaybackMetadata(metadata);
        return metadata;
    }

    private void seamless(Flag flag) {
        History history = state.getHistory();
        Episode episode = history == null ? null : flag.find(history.getVodRemarks(), host.getVodMark().isEmpty());
        host.renderQualityVisible(episode != null && episode.isSelected() && state.getQuality().getUrl().isMulti());
        if (episode == null || episode.isSelected()) return;
        history.setVodRemarks(episode.getName());
        selectEpisode(episode);
    }

    private void mergeFlag(Flag activated, Flag item) {
        Flag target = findFlag(item);
        if (target == null) {
            state.getFlags().add(item);
        } else {
            target.mergeEpisodes(item.getEpisodes(), state.getHistory() != null && state.getHistory().isRevSort());
            if (target.equals(activated)) host.renderEpisodes(target.getEpisodes());
        }
    }

    private Flag resolveFlag(Flag item) {
        Flag flag = findFlag(item);
        if (flag != null) return flag;
        return state.getFlags().get(0);
    }

    private Flag findFlag(Flag item) {
        if (item != null) for (Flag flag : state.getFlags()) if (flag.equals(item)) return flag;
        return null;
    }

    private boolean cannotApply(PlaybackResult<VodPlayRequest> playback) {
        VodPlayRequest pending = state.getPendingRequest();
        VodPlayRequest request = playback.request();
        if (host.isHostFinishing() || pending == null || request == null) return true;
        return !pending.matches(request) || findEpisode(request) == null || !request.accepts(playback.result());
    }

    private Episode getRelativeEpisode(int offset) {
        List<Episode> episodes = state.getFlag().getEpisodes();
        int current = state.getFlag().getPosition();
        int position = Math.clamp(current + offset, 0, episodes.size() - 1);
        return episodes.get(position);
    }
}
