package com.fongmi.android.tv.ui.activity;

import android.annotation.SuppressLint;
import android.app.SearchManager;
import android.content.Intent;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.splashscreen.SplashScreen;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.FocusHighlight;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.leanback.widget.VerticalGridView;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.transition.DrawableCrossFadeFactory;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Updater;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.bean.Cache;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Filter;
import com.fongmi.android.tv.bean.Func;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.HomeMore;
import com.fongmi.android.tv.bean.HomeRowTitle;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Style;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.ActivityHomeBinding;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.CastEvent;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.event.ServerEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.player.extractor.Source;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.service.DLNARendererService;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.ui.adapter.BaseDiffCallback;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.CustomRowPresenter;
import com.fongmi.android.tv.ui.custom.CustomSelector;
import com.fongmi.android.tv.ui.custom.CustomTitleView;
import com.fongmi.android.tv.ui.dialog.SiteDialog;
import com.fongmi.android.tv.ui.presenter.FuncPresenter;
import com.fongmi.android.tv.ui.presenter.HeaderPresenter;
import com.fongmi.android.tv.ui.presenter.HistoryPresenter;
import com.fongmi.android.tv.ui.presenter.HomeHeaderPresenter;
import com.fongmi.android.tv.ui.presenter.HomePosterPresenter;
import com.fongmi.android.tv.ui.presenter.HomeRailPresenter;
import com.fongmi.android.tv.ui.presenter.HomeResumePresenter;
import com.fongmi.android.tv.ui.presenter.ProgressPresenter;
import com.fongmi.android.tv.ui.presenter.VodPresenter;
import com.fongmi.android.tv.utils.Backdrop;
import com.fongmi.android.tv.utils.Clock;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PermissionUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.UrlUtil;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.net.OkHttp;
import com.google.common.collect.Lists;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.List;
import java.util.Optional;

public class HomeActivity extends BaseActivity implements CustomTitleView.Listener, HomePosterPresenter.Listener, FuncPresenter.OnClickListener, HomeResumePresenter.Listener {

    private ActivityHomeBinding mBinding;
    private ArrayObjectAdapter mHistoryAdapter;
    private ArrayObjectAdapter mFuncAdapter;
    private ArrayObjectAdapter mAdapter;
    private HomeResumePresenter mPresenter;
    private HomeRailPresenter mRailPresenter;
    private SiteViewModel mViewModel;
    private Runnable mBillboardTask;
    private String mBillboardName;
    private volatile int mRowsToken;
    private boolean mRailExpanded;
    private Result mResult;
    private Clock mClock;

    private Site getHome() {
        return VodConfig.get().getHome();
    }

    private Config getConfig() {
        return VodConfig.get().getConfig();
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityHomeBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        checkAction(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mResult = Result.empty();
        mClock = Clock.create(mBinding.clock);
        mBinding.progressLayout.showProgress();
        PermissionUtil.requestNotify(this);
        DLNARendererService.start(this);
        Updater.create().start(this);
        setBillboardEffect();
        setRecyclerView();
        setViewModel();
        setAdapter();
        initConfig();
        setTitle();
        setLogo();
    }

    @Override
    protected void initEvent() {
        mBinding.title.setListener(this);
        mBinding.recycler.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (mPresenter.isDelete()) setHistoryDelete(false);
            }
        });
        // 左侧导航获得焦点时展开（显示文字 + 深色面板），离开时收起
        mBinding.getRoot().getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> setRailExpanded(mBinding.rail.hasFocus()));
    }

    // ===== 二次开发：奈飞风格首页 =====

    @Override
    protected boolean customWall() {
        return false;
    }

    // 奈飞效果：选中影片的图片清晰铺满右侧，左侧和底部由遮罩渐隐到黑色（不做模糊）
    private void setBillboardEffect() {
        mBinding.backdrop.setAlpha(1f);
        mBinding.keyart.setVisibility(View.GONE);
    }

    private void setRailExpanded(boolean expanded) {
        if (mRailExpanded == expanded) return;
        mRailExpanded = expanded;
        mRailPresenter.setExpanded(expanded);
        mBinding.rail.setBackgroundColor(expanded ? 0xF20B0B0B : 0x00000000);
        mFuncAdapter.notifyArrayItemRangeChanged(0, mFuncAdapter.size());
    }

    @Override
    public void onFocus(Vod item) {
        String meta = joinMeta(item.getYear(), item.getArea(), item.getTypeName(), item.getRemarks());
        showBillboard(getHome().getName(), item.getName(), meta, item.getContent(), item.hasBackdrop() ? item.getBackdrop() : null, item.getPic());
    }

    @Override
    public void onFocus(History item) {
        String meta = joinMeta(item.getSiteName(), item.getVodRemarks());
        showBillboard(getString(R.string.home_nf_resume_kicker), item.getVodName(), meta, "", null, item.getVodPic());
    }

    /**
     * 焦点快速移动时只加载最后停留的那一张，避免频繁请求图片。
     * 大图优先级：源提供的横版剧照 hd → TMDB 高清剧照 → 海报 poster
     */
    private void showBillboard(String kicker, String name, String meta, String desc, String hd, String poster) {
        App.removeCallbacks(mBillboardTask);
        mBillboardTask = () -> {
            // 用立即更新的变量判断"当前影片"，不依赖带动画的片名文字（快速切换时动画会被打断）
            if (name.equals(mBillboardName)) return;
            mBillboardName = name;
            String text = desc == null ? "" : desc.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
            // 文字：淡出 + 左移 → 换内容 → 淡入归位（和 HTML 设计稿一致的过渡）
            View info = (View) mBinding.billTitle.getParent();
            info.animate().cancel();
            info.animate().alpha(0f).translationX(-ResUtil.dp2px(12)).setDuration(140).withEndAction(() -> {
                mBinding.kicker.setText(kicker);
                mBinding.billTitle.setText(name);
                mBinding.billMeta.setText(meta);
                mBinding.billMeta.setVisibility(TextUtils.isEmpty(meta) ? View.GONE : View.VISIBLE);
                mBinding.billDesc.setText(text);
                mBinding.billDesc.setVisibility(TextUtils.isEmpty(text) ? View.GONE : View.VISIBLE);
                info.setTranslationX(ResUtil.dp2px(12));
                info.animate().alpha(1f).translationX(0f).setDuration(260).start();
            }).start();
            // 背景剧照：新旧两张交叉淡化，而不是直接替换
            if (!TextUtils.isEmpty(hd)) loadBackdrop(hd);
            else if (!Backdrop.enabled()) loadBackdrop(poster);
            else Backdrop.find(name, url -> {
                if (name.equals(mBillboardName)) loadBackdrop(url.isEmpty() ? poster : url);
            });
        };
        App.post(mBillboardTask, 120);
    }

    private void loadBackdrop(String pic) {
        if (TextUtils.isEmpty(pic)) return;
        Glide.with(mBinding.backdrop)
                .load(ImgUtil.getUrl(pic))
                .placeholder(mBinding.backdrop.getDrawable())
                .transition(DrawableTransitionOptions.with(new DrawableCrossFadeFactory.Builder(450).setCrossFadeEnabled(true).build()))
                .centerCrop()
                .into(mBinding.backdrop);
    }

    private static boolean isFirstInRow(View focused) {
        if (focused == null) return false;
        View child = focused;
        ViewParent parent = focused.getParent();
        while (parent instanceof View && !(parent instanceof HorizontalGridView)) {
            child = (View) parent;
            parent = parent.getParent();
        }
        return parent instanceof HorizontalGridView grid && grid.getChildAdapterPosition(child) == 0;
    }

    private static String joinMeta(String... parts) {
        List<String> items = new ArrayList<>();
        for (String part : parts) if (!TextUtils.isEmpty(part) && !items.contains(part.trim())) items.add(part.trim());
        return TextUtils.join("  ·  ", items);
    }

    private void checkAction(Intent intent) {
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            VideoActivity.push(this, intent.getStringExtra(Intent.EXTRA_TEXT));
        } else if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            PermissionUtil.requestFile(this, allGranted -> checkType(intent));
        } else if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String keyword = intent.getStringExtra(SearchManager.QUERY);
            if (!TextUtils.isEmpty(keyword)) SearchActivity.start(this, keyword);
        }
    }

    private void checkType(Intent intent) {
        if ("text/plain".equals(intent.getType()) || UrlUtil.path(intent.getData()).endsWith(".m3u")) {
            loadLive("file:/" + FileChooser.getPathFromUri(intent.getData()));
        } else {
            VideoActivity.push(this, intent.getData().toString());
        }
    }

    @SuppressLint("RestrictedApi")
    private void setRecyclerView() {
        CustomSelector selector = new CustomSelector();
        selector.addPresenter(Integer.class, new HomeHeaderPresenter());
        selector.addPresenter(HomeRowTitle.class, new HomeHeaderPresenter());
        selector.addPresenter(String.class, new ProgressPresenter());
        selector.addPresenter(Vod.class, new HomePosterPresenter(this));
        selector.addPresenter(ListRow.class, new CustomRowPresenter(12, FocusHighlight.ZOOM_FACTOR_SMALL, HorizontalGridView.FOCUS_SCROLL_ALIGNED), HomePosterPresenter.class);
        selector.addPresenter(ListRow.class, new CustomRowPresenter(12, FocusHighlight.ZOOM_FACTOR_SMALL, HorizontalGridView.FOCUS_SCROLL_ALIGNED), HomeResumePresenter.class);
        mBinding.recycler.setAdapter(new ItemBridgeAdapter(mAdapter = new ArrayObjectAdapter(selector)));
        mBinding.recycler.setVerticalSpacing(ResUtil.dp2px(8));
        // 当前行贴顶：焦点行的顶部固定在内容区顶部下方 30dp（留给行标题），上一行完整移出画面
        mBinding.recycler.setWindowAlignment(VerticalGridView.WINDOW_ALIGN_NO_EDGE);
        mBinding.recycler.setWindowAlignmentOffsetPercent(VerticalGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED);
        mBinding.recycler.setWindowAlignmentOffset(ResUtil.dp2px(30));
        mBinding.recycler.setItemAlignmentOffsetPercent(0);
        mBinding.rail.setAdapter(new ItemBridgeAdapter(mFuncAdapter = new ArrayObjectAdapter(mRailPresenter = new HomeRailPresenter(this))));
        mBinding.rail.setVerticalSpacing(ResUtil.dp2px(6));
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.getResult().observe(this, result -> {
            mAdapter.remove("progress");
            addVideo(mResult = result);
            loadCategoryRows(result);
            Cache.clear().put(result);
        });
    }

    private void setAdapter() {
        mHistoryAdapter = new ArrayObjectAdapter(mPresenter = new HomeResumePresenter(this));
        mAdapter.add(R.string.home_nf_history);
        mAdapter.add(R.string.home_nf_recommend);
    }

    private void setTitle() {
        List<String> items = Arrays.asList(getHome().getName(), getConfig().getName(), getString(R.string.app_name));
        Optional<String> optional = items.stream().filter(s -> !TextUtils.isEmpty(s)).findFirst();
        optional.ifPresent(s -> mBinding.title.setText(s));
    }

    private void initConfig() {
        VodConfig.get().init().load(getCallback());
        LiveConfig.get().init().load();
        WallConfig.get().init();
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void success() {
                showContent();
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
                showContent();
            }
        };
    }

    private void showContent() {
        mBinding.progressLayout.showContent();
        checkAction(getIntent());
        setFocus();
    }

    private void loadLive(String url) {
        LiveConfig.load(Config.find(url, 1), new Callback() {
            @Override
            public void success() {
                LiveActivity.start(getActivity());
            }
        });
    }

    private void setFocus() {
        mBinding.title.setSelected(true);
        App.post(() -> mBinding.title.setFocusable(true), 500);
        if (!mBinding.title.hasFocus()) mBinding.recycler.requestFocus();
        // 二次开发：首页没有任何海报（还没填配置 / 加载失败）时，焦点交给左侧导航，避免遥控器无响应
        App.post(() -> {
            if (getCurrentFocus() == null) mBinding.rail.requestFocus();
        }, 300);
    }

    private void getVideo() {
        mRowsToken++;
        mResult = Result.empty();
        int index = getRecommendIndex();
        boolean gone = mAdapter.indexOf("progress") == -1;
        boolean hasItem = gone && mAdapter.size() > index;
        if (hasItem) mAdapter.removeItems(index, mAdapter.size() - index);
        if (gone) mAdapter.add("progress");
        mViewModel.homeContent();
    }

    // ===== 二次开发：首页按分类分行（电影 / 剧集 / 动漫 …）=====
    // 后台按顺序逐个取每个分类的第一页，取到一个就加一行；刷新或切换源时 token 变化，作废旧任务

    private static final int MAX_CATEGORY_ROWS = 10;
    /** 分类行用独立单线程加载：慢站点每个分类可能要等十几秒，不能占着 App 公共任务池 */
    private static final ExecutorService ROWS_EXECUTOR = Executors.newSingleThreadExecutor();

    private void loadCategoryRows(Result home) {
        int token = ++mRowsToken;
        Site site = getHome();
        List<com.fongmi.android.tv.bean.Class> types = new ArrayList<>(home.getTypes());
        LinkedHashMap<String, List<Filter>> filters = home.getFilters();
        if (types.isEmpty() || site.isIndex()) return;
        ROWS_EXECUTOR.submit(() -> {
            int count = 0;
            for (com.fongmi.android.tv.bean.Class type : types) {
                if (token != mRowsToken || count >= MAX_CATEGORY_ROWS) return;
                try {
                    // 和原版点播页一样，带上该分类每个筛选条件的默认值（部分源不带会报错）
                    HashMap<String, String> extend = getDefaultExtend(filters.get(type.getTypeId()));
                    List<Vod> items = SiteApi.categoryContent(site.getKey(), type.getTypeId(), "1", !extend.isEmpty(), extend).getList();
                    if (items.isEmpty()) continue;
                    count++;
                    App.post(() -> addCategoryRow(token, type, items));
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private static HashMap<String, String> getDefaultExtend(List<Filter> filters) {
        HashMap<String, String> extend = new HashMap<>();
        if (filters == null) return extend;
        for (Filter filter : filters) if (!TextUtils.isEmpty(filter.getInit())) extend.put(filter.getKey(), filter.getInit());
        return extend;
    }

    private void addCategoryRow(int token, com.fongmi.android.tv.bean.Class type, List<Vod> items) {
        if (token != mRowsToken) return;
        ArrayObjectAdapter adapter = new ArrayObjectAdapter(new HomePosterPresenter(this));
        adapter.addAll(0, items);
        adapter.add(new HomeMore(type.getTypeId(), type.getTypeName()));
        mAdapter.add(new HomeRowTitle(type.getTypeName()));
        mAdapter.add(new ListRow(adapter));
    }

    @Override
    public void onMore(HomeMore item) {
        VodActivity.start(this, getHome().getKey(), mResult, item.getTypeId());
    }

    // 二次开发：推荐内容统一放进一行横向滑动的海报（不再按网格分多行）
    private void addVideo(Result result) {
        List<Vod> items = result.getList();
        if (items.isEmpty()) return;
        ArrayObjectAdapter adapter = new ArrayObjectAdapter(new HomePosterPresenter(this));
        adapter.addAll(0, items);
        mAdapter.add(mAdapter.size(), new ListRow(adapter));
        onFocus(items.get(0));
    }

    private void addGrid(List<Vod> items, Style style) {
        List<ListRow> rows = new ArrayList<>();
        VodPresenter presenter = new VodPresenter(this, style);
        for (List<Vod> part : Lists.partition(items, Product.getColumn(style))) {
            ArrayObjectAdapter adapter = new ArrayObjectAdapter(presenter);
            adapter.addAll(0, part);
            rows.add(new ListRow(adapter));
        }
        mAdapter.addAll(mAdapter.size(), rows);
    }

    private void setFunc() {
        List<Func> items = new ArrayList<>();
        items.add(Func.create(R.string.home_vod));
        if (LiveConfig.hasUrl()) items.add(Func.create(R.string.home_live));
        items.add(Func.create(R.string.home_search));
        items.add(Func.create(R.string.home_keep));
        items.add(Func.create(R.string.home_push));
        items.add(Func.create(R.string.home_setting));
        mFuncAdapter.setItems(items, new BaseDiffCallback<Func>());
    }

    private void getHistory() {
        getHistory(false);
    }

    private void getHistory(boolean renew) {
        List<History> items = History.get();
        int historyIndex = getHistoryIndex();
        int recommendIndex = getRecommendIndex();
        boolean exist = recommendIndex - historyIndex == 2;
        if (renew) mHistoryAdapter = new ArrayObjectAdapter(mPresenter = new HomeResumePresenter(this));
        if ((items.isEmpty() && exist) || (renew && exist)) mAdapter.removeItems(historyIndex, 1);
        if ((!items.isEmpty() && !exist) || (renew && exist)) mAdapter.add(historyIndex, new ListRow(mHistoryAdapter));
        mHistoryAdapter.setItems(items, new BaseDiffCallback<History>());
    }

    private void setHistoryDelete(boolean delete) {
        mPresenter.setDelete(delete);
        mHistoryAdapter.notifyArrayItemRangeChanged(0, mHistoryAdapter.size());
    }

    private void clearHistory() {
        mAdapter.removeItems(getHistoryIndex(), 1);
        History.clear(VodConfig.getCid());
        mPresenter.setDelete(false);
        mHistoryAdapter.clear();
    }

    private int getHistoryIndex() {
        return mAdapter.indexOf(R.string.home_nf_history) + 1;
    }

    private int getRecommendIndex() {
        return mAdapter.indexOf(R.string.home_nf_recommend) + 1;
    }

    private void setLogo() {
        ImgUtil.logo(mBinding.logo);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        switch (event.type()) {
            case VOD:
                RefreshEvent.history();
                RefreshEvent.home();
                setLogo();
                break;
            case COMMON:
                setFunc();
                break;
            case BOOT:
                LiveActivity.start(this);
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        switch (event.getType()) {
            case HOME:
                getVideo();
                setTitle();
                break;
            case HISTORY:
                getHistory();
                break;
            case SIZE:
                getVideo();
                getHistory(true);
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onServerEvent(ServerEvent event) {
        switch (event.type()) {
            case SEARCH:
                SearchActivity.start(this, event.text());
                break;
            case PUSH:
                VideoActivity.push(this, event.text());
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onCastEvent(CastEvent event) {
        if (VodConfig.get().getConfig().equals(event.config())) {
            VideoActivity.cast(this, event.history());
        } else {
            VodConfig.load(event.config(), getCallback(event));
        }
    }

    private Callback getCallback(CastEvent event) {
        return new Callback() {
            @Override
            public void success() {
                onCastEvent(event);
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
            }
        };
    }

    @Override
    public void onItemClick(Func item) {
        if (item.getResId() == R.string.home_vod) VodActivity.start(this, mResult);
        else if (item.getResId() == R.string.home_live) LiveActivity.start(this);
        else if (item.getResId() == R.string.home_keep) KeepActivity.start(this);
        else if (item.getResId() == R.string.home_push) PushActivity.start(this);
        else if (item.getResId() == R.string.home_search) SearchActivity.start(this);
        else if (item.getResId() == R.string.home_setting) SettingActivity.start(this);
    }

    @Override
    public void onItemClick(Vod item) {
        if (item.isAction()) mViewModel.action(getHome().getKey(), item.getAction());
        else if (getHome().isIndex()) CollectActivity.start(this, item.getName());
        else VideoActivity.start(this, getHome().getKey(), item.getId(), item.getName(), item.getPic());
    }

    @Override
    public boolean onLongClick(Vod item) {
        if (item.isAction()) return false;
        CollectActivity.start(this, item.getName());
        return true;
    }

    @Override
    public void onItemClick(History item) {
        VideoActivity.start(this, item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
    }

    @Override
    public void onItemDelete(History item) {
        mHistoryAdapter.remove(item.delete());
        if (mHistoryAdapter.size() > 0) return;
        mAdapter.removeItems(getHistoryIndex(), 1);
        mPresenter.setDelete(false);
    }

    @Override
    public boolean onLongClick() {
        if (mPresenter.isDelete()) clearHistory();
        else setHistoryDelete(true);
        return true;
    }

    @Override
    public void showDialog() {
        SiteDialog.create().show(this);
    }

    @Override
    public void onRefresh() {
        getVideo();
    }

    @Override
    public void setSite(Site item) {
        VodConfig.get().setHome(item);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (KeyUtil.isMenuKey(event)) showDialog();
        // 二次开发：在一行的第一张海报上按 ←，焦点进入左侧导航
        if (KeyUtil.isActionDown(event) && event.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT && mBinding.recycler.hasFocus() && isFirstInRow(getCurrentFocus())) return mBinding.rail.requestFocus();
        if (KeyUtil.isActionDown(event) & KeyUtil.isDownKey(event) && getCurrentFocus() == mBinding.title) return mBinding.recycler.getChildAt(0).requestFocus();
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mClock.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mClock.stop();
    }

    @Override
    protected void onBackInvoked() {
        if (mBinding.progressLayout.isProgress()) {
            showContent();
        } else if (mPresenter.isDelete()) {
            setHistoryDelete(false);
        } else if (mBinding.recycler.getSelectedPosition() != 0) {
            mBinding.recycler.scrollToPosition(0);
        } else {
            if (PlaybackService.isRunning()) Util.moveToBackground(this);
            else super.onBackInvoked();
        }
    }

    @Override
    protected void onDestroy() {
        DLNARendererService.stop(this);
        LiveConfig.get().clear();
        VodConfig.get().clear();
        AppDatabase.backup();
        OkHttp.get().clear();
        Source.get().exit();
        Server.get().stop();
        super.onDestroy();
    }
}
