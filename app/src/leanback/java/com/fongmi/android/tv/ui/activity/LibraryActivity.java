package com.fongmi.android.tv.ui.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.view.ViewGroup;
import android.view.Gravity;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.FocusHighlight;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ObjectAdapter;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.VerticalGridView;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Cache;
import com.fongmi.android.tv.bean.Class;
import com.fongmi.android.tv.bean.Filter;
import com.fongmi.android.tv.bean.Func;
import com.fongmi.android.tv.bean.HomeRowTitle;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Value;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.ActivityLibraryBinding;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.ui.adapter.BaseDiffCallback;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.Billboard;
import com.fongmi.android.tv.ui.custom.CustomRowPresenter;
import com.fongmi.android.tv.ui.custom.CustomSelector;
import com.fongmi.android.tv.ui.presenter.FilterPresenter;
import com.fongmi.android.tv.ui.presenter.FuncPresenter;
import com.fongmi.android.tv.ui.presenter.HomeHeaderPresenter;
import com.fongmi.android.tv.ui.presenter.HomeRailPresenter;
import com.fongmi.android.tv.ui.presenter.LandscapeCardPresenter;
import com.fongmi.android.tv.ui.presenter.LibraryTabPresenter;
import com.fongmi.android.tv.ui.presenter.ProgressPresenter;
import com.fongmi.android.tv.utils.Clock;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.LibraryPlanner;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 二次开发：奈飞风格片库。
 * - 左侧导航和首页一样；顶部一排文字分类，焦点移到哪个分类就切过去
 * - 每个分类按它的筛选条件拆成多行（规则见 LibraryPlanner），往下滚到了才加载下一行，切回来不重新请求
 * - 点顶部分类（或按菜单键）在分类下面展开筛选，选了条件就显示筛选结果；再点一次 / 返回键收起
 * - 行尾「查看全部」、筛选结果、没有筛选条件的分类：都进入网格（一行 5 张横版卡片，滚到底加载下一页）
 * - 原来的 VodActivity 保留给网盘目录（文件夹）浏览用
 */
public class LibraryActivity extends BaseActivity implements LandscapeCardPresenter.Listener, FuncPresenter.OnClickListener {

    /** 网格每行几张卡片 */
    private static final int COLUMNS = 5;
    /** 去重后少于这么多部就不成行 */
    private static final int MIN_ROW_ITEMS = 4;
    /** 打开分类时先加载几行，之后每次往下多加载几行 */
    private static final int FIRST_ROWS = 4;
    private static final int MORE_ROWS = 3;
    /** 焦点在分类标签上停多久才切换（快速划过不切，免得每个分类都发请求） */
    private static final int TAB_DELAY = 350;
    /** 和首页分类行一样用独立单线程：慢站点一个请求可能要十几秒，py / js 站本来也只能一次处理一个 */
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    /**
     * 首页结果直接交给片库：Result 走 Intent 时只带分类、不带筛选条件，
     * 动漫 / 短剧行来自别的站时 Cache 里也没有它的筛选条件。进程被回收重建时退回 Intent + Cache。
     */
    private static Result sHandoff;

    private ActivityLibraryBinding mBinding;
    private ArrayObjectAdapter mTabAdapter;
    private ArrayObjectAdapter mFuncAdapter;
    private HomeRailPresenter mRailPresenter;
    private LandscapeCardPresenter mCardPresenter;
    private CustomSelector mSelector;
    private SiteViewModel mViewModel;
    private Billboard mBillboard;
    private Clock mClock;
    private Result mResult;
    private String mKey;

    private final Map<String, Tab> mTabs = new HashMap<>();
    private Tab mTab;
    private Grid mGrid;
    private Runnable mTabTask;
    /** 切换分类 / 进出网格时 +1，作废后台还没开始的请求 */
    private volatile int mToken;
    private boolean mRailExpanded;
    /** 刚打开时，第一行内容出来后焦点交给内容区 */
    private boolean mAutoFocus = true;

    /** 一个分类的状态（切走再切回来直接用，不重新请求） */
    private static class Tab {

        Class type;
        List<Filter> filters;
        List<LibraryPlanner.Row> plan;
        ArrayObjectAdapter adapter;
        final Set<String> seen = new HashSet<>();
        /** 筛选面板里用户选的条件 */
        final HashMap<String, String> picked = new HashMap<>();
        /** 拆不成多行（没有筛选条件 / 网盘目录 / 每行都取不到），直接显示网格 */
        boolean gridOnly;
        boolean loading;
        int next;
        int selected = -1;
    }

    /** 网格：行尾「查看全部」、筛选结果、没有筛选条件的分类 */
    private static class Grid {

        String title;
        HashMap<String, String> extend;
        ArrayObjectAdapter adapter;
        ArrayObjectAdapter last;
        /** 分类本身就是网格（返回键直接退出片库，而不是回到行） */
        boolean root;
        boolean loading;
        boolean more = true;
        int page;
        int back;
    }

    public static void start(Activity activity, String key, Result result, String typeId) {
        if (result == null || result.getTypes().isEmpty()) return;
        sHandoff = result;
        Intent intent = new Intent(activity, LibraryActivity.class);
        intent.putExtra("key", key);
        intent.putExtra("result", result);
        intent.putExtra("typeId", typeId);
        activity.startActivity(intent);
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityLibraryBinding.inflate(getLayoutInflater());
    }

    @Override
    protected boolean customWall() {
        return false;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        Result handoff = sHandoff;
        sHandoff = null;
        mKey = getIntent().getStringExtra("key");
        mResult = handoff != null ? handoff : getIntent().getParcelableExtra("result");
        if (mResult == null) mResult = Result.empty();
        mClock = Clock.create(mBinding.clock);
        mBillboard = new Billboard(this, mBinding.backdrop, mBinding.keyart, mBinding.info, null, mBinding.billTitle, mBinding.billMeta, mBinding.billDesc);
        setRail();
        setRecyclerView();
        setTabs();
        setViewModel();
        selectInitialTab();
    }

    @Override
    protected void initEvent() {
        mBinding.tabs.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                onTabSelected(position);
            }
        });
        mBinding.recycler.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                checkMore();
                mBillboard.setDimmed(rowsBefore(getContent(), position) >= 2);
            }
        });
        mBinding.getRoot().getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> setRailExpanded(mBinding.rail.hasFocus()));
    }

    // ===== 初始化 =====

    private void setRail() {
        mBinding.rail.setAdapter(new ItemBridgeAdapter(mFuncAdapter = new ArrayObjectAdapter(mRailPresenter = new HomeRailPresenter(this))));
        mBinding.rail.setVerticalSpacing(ResUtil.dp2px(6));
        mRailPresenter.setCurrent(R.string.home_vod);
        List<Func> items = new ArrayList<>();
        items.add(Func.create(R.string.home_nf_home));
        items.add(Func.create(R.string.home_vod));
        if (LiveConfig.hasUrl()) items.add(Func.create(R.string.home_live));
        items.add(Func.create(R.string.home_search));
        items.add(Func.create(R.string.home_keep));
        items.add(Func.create(R.string.home_push));
        items.add(Func.create(R.string.home_setting));
        mFuncAdapter.setItems(items, new BaseDiffCallback<Func>());
    }

    @SuppressLint("RestrictedApi")
    private void setRecyclerView() {
        mCardPresenter = new LandscapeCardPresenter(this);
        mSelector = new CustomSelector();
        mSelector.addPresenter(HomeRowTitle.class, new HomeHeaderPresenter());
        mSelector.addPresenter(String.class, new ProgressPresenter());
        mSelector.addPresenter(ListRow.class, new CustomRowPresenter(12, FocusHighlight.ZOOM_FACTOR_SMALL, HorizontalGridView.FOCUS_SCROLL_ALIGNED), LandscapeCardPresenter.class);
        mBinding.recycler.setVerticalSpacing(ResUtil.dp2px(8));
        // 和首页一样：当前行贴顶，焦点行的顶部固定在内容区顶部下方 30dp（留给行标题）
        mBinding.recycler.setWindowAlignment(VerticalGridView.WINDOW_ALIGN_NO_EDGE);
        mBinding.recycler.setWindowAlignmentOffsetPercent(VerticalGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED);
        mBinding.recycler.setWindowAlignmentOffset(ResUtil.dp2px(30));
        mBinding.recycler.setItemAlignmentOffsetPercent(0);
    }

    private void setTabs() {
        mBinding.tabs.setHorizontalSpacing(ResUtil.dp2px(4));
        mBinding.tabs.setAdapter(new ItemBridgeAdapter(mTabAdapter = new ArrayObjectAdapter(new LibraryTabPresenter(type -> mTab != null && mTab.type == type, this::onTabClick))));
        mTabAdapter.addAll(0, mResult.getTypes());
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.getAction().observe(this, result -> Notify.show(result.getMsg()));
    }

    private void selectInitialTab() {
        String typeId = getIntent().getStringExtra("typeId");
        int index = 0;
        List<Class> types = mResult.getTypes();
        for (int i = 0; typeId != null && i < types.size(); i++) {
            if (typeId.equals(types.get(i).getTypeId())) index = i;
        }
        mBinding.tabs.setSelectedPosition(index);
        showTab(index);
        // 先把焦点放在分类上，第一行内容出来后交给内容区
        mBinding.tabs.requestFocus();
    }

    private Site getSite() {
        return VodConfig.get().getSite(mKey);
    }

    private List<Filter> getFilters(Class type) {
        List<Filter> filters = mResult.getFilters().get(type.getTypeId());
        if (filters == null || filters.isEmpty()) filters = type.getFilters();
        if (filters == null || filters.isEmpty()) filters = Cache.get(type);
        return filters == null ? new ArrayList<>() : filters;
    }

    // ===== 分类切换 =====

    private void onTabSelected(int position) {
        if (mTab != null && position >= 0 && position < mTabAdapter.size() && mTabAdapter.get(position) == mTab.type) return;
        App.removeCallbacks(mTabTask);
        mTabTask = () -> showTab(position);
        App.post(mTabTask, TAB_DELAY);
    }

    private void showTab(int position) {
        if (position < 0 || position >= mTabAdapter.size()) return;
        Class type = (Class) mTabAdapter.get(position);
        if (mTab != null) mTab.selected = mBinding.recycler.getSelectedPosition();
        mTab = mTabs.computeIfAbsent(type.getTypeId(), id -> createTab(type));
        mToken++;
        mGrid = null;
        hideFilter(false);
        updateTabs();
        if (mTab.gridOnly) {
            openGrid(mTab.type.getTypeName(), LibraryPlanner.defaults(mTab.filters), true);
            return;
        }
        setContent(mTab.adapter, false);
        if (mTab.adapter.size() == 0) mBinding.progressLayout.showProgress();
        else mBinding.progressLayout.showContent();
        if (mTab.selected > 0 && mTab.selected < mTab.adapter.size()) mBinding.recycler.setSelectedPosition(mTab.selected);
        showFirst(mTab.adapter);
        ensureRows(mTab, mTab.adapter.size() == 0 ? FIRST_ROWS : MORE_ROWS);
    }

    private Tab createTab(Class type) {
        Tab tab = new Tab();
        tab.type = type;
        tab.filters = getFilters(type);
        tab.plan = type.isFolder() ? new ArrayList<>() : LibraryPlanner.plan(type.getTypeName(), tab.filters);
        tab.gridOnly = tab.plan.size() <= 1;
        tab.adapter = new ArrayObjectAdapter(mSelector);
        return tab;
    }

    /** 当前分类的标签画底部短线（直接改看得见的标签，不刷新列表，免得打断焦点） */
    private void updateTabs() {
        for (int i = 0; i < mBinding.tabs.getChildCount(); i++) {
            View child = mBinding.tabs.getChildAt(i);
            child.setSelected(mTab != null && child.getTag() == mTab.type);
        }
    }

    /**
     * grid：网格行之间没有行标题，焦点行贴着顶部（只留一点边距），不然上一行会露出半截；
     * 刚进网格时靠顶部对齐（LOW_EDGE），网格标题还在
     */
    private void setContent(ArrayObjectAdapter adapter, boolean grid) {
        mBinding.recycler.setWindowAlignment(grid ? VerticalGridView.WINDOW_ALIGN_LOW_EDGE : VerticalGridView.WINDOW_ALIGN_NO_EDGE);
        mBinding.recycler.setWindowAlignmentOffset(ResUtil.dp2px(grid ? 8 : 30));
        mBinding.recycler.setAdapter(new ItemBridgeAdapter(adapter));
    }

    // ===== 分类行：按计划逐行加载 =====

    private void ensureRows(Tab tab, int count) {
        if (tab.loading || tab.gridOnly || tab.next >= tab.plan.size()) return;
        tab.loading = true;
        int token = mToken;
        EXECUTOR.submit(() -> {
            int added = 0;
            while (added < count && tab.next < tab.plan.size()) {
                if (token != mToken) break;
                LibraryPlanner.Row row = tab.plan.get(tab.next++);
                List<Vod> items = fetchRow(tab, row);
                if (items.isEmpty()) continue;
                added++;
                App.post(() -> addRow(tab, row, items));
            }
            App.post(() -> onRowsLoaded(tab));
        });
    }

    /** 取某行第一页、去重（后台线程）；同一部片子在一个分类里只出现一次，去重后太少就整行不要 */
    private List<Vod> fetchRow(Tab tab, LibraryPlanner.Row row) {
        try {
            List<Vod> list = SiteApi.categoryContent(mKey, tab.type.getTypeId(), "1", true, new HashMap<>(row.extend)).getList();
            List<Vod> items = new ArrayList<>();
            for (Vod vod : list) if (tab.seen.add(vod.getName())) items.add(vod);
            if (items.size() >= MIN_ROW_ITEMS) return items;
            return new ArrayList<>();
        } catch (Throwable e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    private void addRow(Tab tab, LibraryPlanner.Row row, List<Vod> items) {
        boolean first = tab.adapter.size() == 0;
        ArrayObjectAdapter adapter = new ArrayObjectAdapter(mCardPresenter);
        adapter.addAll(0, items);
        adapter.add(row);
        tab.adapter.add(new HomeRowTitle(row.title));
        tab.adapter.add(new ListRow(adapter));
        if (tab != mTab || mGrid != null) return;
        mBinding.progressLayout.showContent();
        if (first) {
            onFocus(items.get(0));
            takeFocus();
        }
    }

    private void onRowsLoaded(Tab tab) {
        tab.loading = false;
        // 一行都没取到（筛选值对这个站无效等）：退回网格
        if (tab.adapter.size() == 0 && tab.next >= tab.plan.size()) {
            tab.gridOnly = true;
            if (tab == mTab && mGrid == null) openGrid(tab.type.getTypeName(), LibraryPlanner.defaults(tab.filters), true);
            return;
        }
        if (tab == mTab && mGrid == null) checkMore();
    }

    /** 焦点接近底部时多加载几行 / 下一页 */
    private void checkMore() {
        ObjectAdapter adapter = mGrid != null ? mGrid.adapter : mTab != null ? mTab.adapter : null;
        if (adapter == null) return;
        int position = mBinding.recycler.getSelectedPosition();
        if (mGrid != null) {
            if (position >= adapter.size() - 3) loadGridPage();
        } else if (position >= adapter.size() - 4) {
            ensureRows(mTab, MORE_ROWS);
        }
    }

    // ===== 网格 =====

    private void openGrid(String title, HashMap<String, String> extend, boolean root) {
        int back = mGrid != null ? mGrid.back : mBinding.recycler.getSelectedPosition();
        mToken++;
        mGrid = new Grid();
        mGrid.title = title;
        mGrid.extend = extend;
        mGrid.root = root;
        mGrid.back = back;
        mGrid.adapter = new ArrayObjectAdapter(mSelector);
        mGrid.adapter.add(new HomeRowTitle(title));
        setContent(mGrid.adapter, true);
        mBinding.progressLayout.showProgress();
        loadGridPage();
    }

    private void loadGridPage() {
        Grid grid = mGrid;
        Tab tab = mTab;
        if (grid == null || tab == null || grid.loading || !grid.more) return;
        grid.loading = true;
        int page = grid.page + 1;
        int token = mToken;
        if (page > 1) grid.adapter.add("progress");
        EXECUTOR.submit(() -> {
            Result result;
            try {
                result = token != mToken ? null : SiteApi.categoryContent(mKey, tab.type.getTypeId(), String.valueOf(page), !tab.filters.isEmpty(), new HashMap<>(grid.extend));
            } catch (Throwable e) {
                e.printStackTrace();
                result = Result.empty();
            }
            Result finalResult = result;
            App.post(() -> onGridPage(grid, page, finalResult));
        });
    }

    private void onGridPage(Grid grid, int page, @Nullable Result result) {
        grid.loading = false;
        grid.adapter.remove("progress");
        if (result == null) return; // 请求前就被作废了
        List<Vod> items = result.getList();
        grid.page = page;
        int pageCount = result.getPageCount() == null ? 0 : result.getPageCount();
        grid.more = !items.isEmpty() && (pageCount == 0 || page < pageCount);
        if (grid != mGrid) return;
        if (page == 1 && items.isEmpty()) {
            mBinding.progressLayout.showEmpty();
            return;
        }
        addGrid(grid, items);
        mBinding.progressLayout.showContent();
        if (page == 1) {
            onFocus(items.get(0));
            takeFocus();
        } else {
            checkMore();
        }
    }

    private void addGrid(Grid grid, List<Vod> items) {
        int index = 0;
        if (grid.last != null && grid.last.size() < COLUMNS) {
            int size = Math.min(COLUMNS - grid.last.size(), items.size());
            grid.last.addAll(grid.last.size(), items.subList(0, size));
            index = size;
        }
        while (index < items.size()) {
            int end = Math.min(index + COLUMNS, items.size());
            grid.last = new ArrayObjectAdapter(mCardPresenter);
            grid.last.addAll(0, new ArrayList<>(items.subList(index, end)));
            grid.adapter.add(new ListRow(grid.last));
            index = end;
        }
    }

    /** 从网格回到分类行；分类本身就是网格时返回 false（交给返回键退出） */
    private boolean closeGrid(boolean focus) {
        if (mGrid == null || mGrid.root || mTab == null) return false;
        int back = mGrid.back;
        mToken++;
        mGrid = null;
        setContent(mTab.adapter, false);
        mBinding.progressLayout.showContent();
        if (back > 0 && back < mTab.adapter.size()) mBinding.recycler.setSelectedPosition(back);
        if (focus) mBinding.recycler.requestFocus();
        ensureRows(mTab, MORE_ROWS);
        return true;
    }

    // ===== 筛选：点分类在分类下面展开 =====

    private boolean hasFilter(Tab tab) {
        return tab != null && !tab.filters.isEmpty() && !tab.type.isFolder();
    }

    /** 点顶部分类：不是当前分类就立即切过去并展开筛选；是当前分类就展开 / 收起筛选 */
    private void onTabClick(Class type) {
        if (mTab == null || mTab.type != type) {
            App.removeCallbacks(mTabTask);
            showTab(mTabAdapter.indexOf(type));
            if (hasFilter(mTab)) showFilter();
        } else if (isFilterVisible()) {
            hideFilter(false);
        } else if (hasFilter(mTab)) {
            showFilter();
        } else {
            Notify.show(R.string.library_no_filter);
        }
    }

    /** 每个筛选条件一行：左边条件名，右边可左右滑动的选项；选中的选项高亮 */
    private void showFilter() {
        if (!hasFilter(mTab)) return;
        mBinding.filterRows.removeAllViews();
        for (Filter source : mTab.filters) {
            Filter filter = source.copy();
            if (filter.getValue().isEmpty()) continue;
            String picked = mTab.picked.get(filter.getKey());
            if (picked != null) filter.setSelected(picked);
            mBinding.filterRows.addView(createFilterRow(filter));
        }
        mBinding.info.setVisibility(View.INVISIBLE);
        mBinding.filterArea.scrollTo(0, 0);
        mBinding.filterArea.setVisibility(View.VISIBLE);
        mBinding.filterArea.setAlpha(0f);
        mBinding.filterArea.animate().alpha(1f).setDuration(180).start();
    }

    private View createFilterRow(Filter filter) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(42)));
        TextView label = new TextView(this);
        label.setText(filter.getName());
        label.setTextColor(0xFFB3B3B3);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        label.setSingleLine(true);
        label.setShadowLayer(ResUtil.dp2px(4), 0, ResUtil.dp2px(1), 0xCC000000);
        row.addView(label, new LinearLayout.LayoutParams(ResUtil.dp2px(56), ViewGroup.LayoutParams.WRAP_CONTENT));
        HorizontalGridView grid = new HorizontalGridView(this);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        grid.setHorizontalSpacing(ResUtil.dp2px(8));
        grid.setFocusScrollStrategy(HorizontalGridView.FOCUS_SCROLL_ALIGNED);
        CompactFilterPresenter presenter = new CompactFilterPresenter(filter.getKey());
        ArrayObjectAdapter values = new ArrayObjectAdapter(presenter);
        values.setItems(filter.getValue(), null);
        presenter.setOnClickListener((key, item) -> onFilterClick(values, key, item));
        grid.setAdapter(new ItemBridgeAdapter(values));
        // 已选的选项放在焦点进来的位置
        for (int i = 0; i < values.size(); i++) if (((Value) values.get(i)).isSelected()) grid.setSelectedPosition(i);
        row.addView(grid, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        return row;
    }

    /** 筛选选项（比原版小一号，一屏多放几行） */
    private static class CompactFilterPresenter extends FilterPresenter {

        CompactFilterPresenter(String key) {
            super(key);
        }

        @NonNull
        @Override
        public Presenter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent) {
            Presenter.ViewHolder holder = super.onCreateViewHolder(parent);
            TextView text = (TextView) holder.view;
            text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            text.setPadding(ResUtil.dp2px(14), ResUtil.dp2px(5), ResUtil.dp2px(14), ResUtil.dp2px(5));
            return holder;
        }
    }

    private void onFilterClick(ArrayObjectAdapter values, String key, Value item) {
        for (int i = 0; i < values.size(); i++) ((Value) values.get(i)).setSelected(item);
        values.notifyArrayItemRangeChanged(0, values.size());
        if (item.isSelected() && !TextUtils.isEmpty(item.getV())) mTab.picked.put(key, item.getV());
        else mTab.picked.remove(key);
        applyFilter();
    }

    /** 按选的条件显示网格；全部取消时回到分类行（分类本身是网格时显示默认网格） */
    private void applyFilter() {
        HashMap<String, String> extend = LibraryPlanner.defaults(mTab.filters);
        extend.putAll(mTab.picked);
        if (mTab.picked.isEmpty()) {
            if (mTab.gridOnly) openGrid(mTab.type.getTypeName(), extend, true);
            else closeGrid(false);
            return;
        }
        List<String> names = new ArrayList<>();
        for (Filter filter : mTab.filters) {
            String v = mTab.picked.get(filter.getKey());
            if (v == null) continue;
            for (Value value : filter.getValue()) if (value != null && v.equals(value.getV())) names.add(value.getN());
        }
        String title = names.isEmpty() ? getString(R.string.library_filter_result) : mTab.type.getTypeName() + " · " + TextUtils.join(" · ", names);
        openGrid(title, extend, mTab.gridOnly);
    }

    private boolean isFilterVisible() {
        return mBinding.filterArea.getVisibility() == View.VISIBLE;
    }

    /** 收起筛选；焦点在筛选里（或 focus）时交回分类标签 */
    private void hideFilter(boolean focus) {
        if (!isFilterVisible()) return;
        boolean inside = mBinding.filterArea.hasFocus();
        mBinding.filterArea.animate().cancel();
        mBinding.filterArea.setVisibility(View.GONE);
        mBinding.info.setVisibility(View.VISIBLE);
        if (focus || inside) mBinding.tabs.requestFocus();
    }

    /** 筛选区第一行 / 最后一行的选项条 */
    private View getFilterRow(boolean last) {
        int count = mBinding.filterRows.getChildCount();
        if (count == 0) return null;
        ViewGroup row = (ViewGroup) mBinding.filterRows.getChildAt(last ? count - 1 : 0);
        return row.getChildAt(1);
    }

    /** focus 在筛选区的第几行（-1：不在筛选区） */
    private int getFilterRowIndex(View focus) {
        for (int i = 0; i < mBinding.filterRows.getChildCount(); i++) if (isInside(focus, mBinding.filterRows.getChildAt(i))) return i;
        return -1;
    }

    // ===== 焦点 =====

    private void takeFocus() {
        takeFocus(false);
    }

    /** 把焦点交给内容区：刚打开时（mAutoFocus）或 force；内容区没东西时交给分类标签 */
    private void takeFocus(boolean force) {
        if (!force && !mAutoFocus) return;
        mAutoFocus = false;
        if (getFirstRowIndex() >= 0) mBinding.recycler.requestFocus();
        else mBinding.tabs.requestFocus();
    }

    private ObjectAdapter getContent() {
        return mGrid != null ? mGrid.adapter : mTab != null ? mTab.adapter : null;
    }

    private int getFirstRowIndex() {
        ObjectAdapter adapter = getContent();
        if (adapter == null) return -1;
        for (int i = 0; i < adapter.size(); i++) if (adapter.get(i) instanceof ListRow) return i;
        return -1;
    }

    private void setRailExpanded(boolean expanded) {
        if (mRailExpanded == expanded) return;
        mRailExpanded = expanded;
        mRailPresenter.setExpanded(expanded);
        mBinding.rail.setBackgroundColor(expanded ? 0xF20B0B0B : 0x00000000);
        mFuncAdapter.notifyArrayItemRangeChanged(0, mFuncAdapter.size());
    }

    /** 二次开发：焦点所在行前面有几行卡片（行标题不算）；翻到第三行起把大图区压暗 */
    private static int rowsBefore(ObjectAdapter adapter, int position) {
        int count = 0;
        for (int i = 0; adapter != null && i < Math.min(position, adapter.size()); i++) if (adapter.get(i) instanceof ListRow) count++;
        return count;
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

    private boolean isInside(View view, View parent) {
        for (View v = view; v != null; v = v.getParent() instanceof View p ? p : null) if (v == parent) return true;
        return false;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (KeyUtil.isActionDown(event)) {
            View focus = getCurrentFocus();
            if (KeyUtil.isMenuKey(event)) {
                if (isFilterVisible()) hideFilter(true);
                else showFilter();
                return true;
            }
            boolean inContent = mBinding.recycler.hasFocus();
            boolean hasRows = getFirstRowIndex() >= 0;
            // 筛选区：第一行按 ↑ 回分类，最后一行按 ↓ 进内容区，每行第一个按 ← 不跑到左侧导航
            int filterRow = getFilterRowIndex(focus);
            if (filterRow >= 0) {
                if (KeyUtil.isLeftKey(event) && isFirstInRow(focus)) return true;
                if (KeyUtil.isUpKey(event) && filterRow == 0) return mBinding.tabs.requestFocus();
                if (KeyUtil.isDownKey(event) && filterRow == mBinding.filterRows.getChildCount() - 1) return !hasRows || mBinding.recycler.requestFocus();
                return super.dispatchKeyEvent(event);
            }
            // 一行的第一张卡片上按 ← 进左侧导航；导航里按 → 回内容区
            if (KeyUtil.isLeftKey(event) && inContent && isFirstInRow(focus)) return mBinding.rail.requestFocus();
            if (KeyUtil.isRightKey(event) && mBinding.rail.hasFocus()) return hasRows ? mBinding.recycler.requestFocus() : mBinding.tabs.requestFocus();
            // 内容区第一行按 ↑：筛选展开时进筛选最后一行，否则回分类标签
            if (KeyUtil.isUpKey(event) && inContent && mBinding.recycler.getSelectedPosition() <= getFirstRowIndex()) {
                View row = isFilterVisible() ? getFilterRow(true) : null;
                return row != null ? row.requestFocus() : mBinding.tabs.requestFocus();
            }
            // 分类标签按 ↓：筛选展开时进筛选第一行，否则进内容区
            if (KeyUtil.isDownKey(event) && mBinding.tabs.hasFocus()) {
                View row = isFilterVisible() ? getFilterRow(false) : null;
                if (row != null) return row.requestFocus();
                if (hasRows) return mBinding.recycler.requestFocus();
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onBackInvoked() {
        if (isFilterVisible() && (mBinding.filterArea.hasFocus() || mBinding.tabs.hasFocus())) {
            hideFilter(true);
        } else if (closeGrid(true)) {
            // 回到分类行
        } else if (isFilterVisible()) {
            hideFilter(false);
        } else if (!mBinding.tabs.hasFocus() && !mBinding.rail.hasFocus() && mBinding.recycler.getSelectedPosition() > getFirstRowIndex()) {
            mBinding.recycler.setSelectedPositionSmooth(Math.max(getFirstRowIndex(), 0));
        } else {
            super.onBackInvoked();
        }
    }

    // ===== 卡片回调 =====

    @Override
    public void onFocus(Vod item) {
        mBillboard.show(null, item);
        prefetchNeighbors(item);
    }

    /** 预加载焦点右边两部的剧照（大图区），移过去时直接从内存显示 */
    private void prefetchNeighbors(Vod item) {
        ObjectAdapter content = getContent();
        int pos = mBinding.recycler.getSelectedPosition();
        if (content == null || pos < 0 || pos >= content.size() || !(content.get(pos) instanceof ListRow row)) return;
        ObjectAdapter items = row.getAdapter();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) != item) continue;
            for (int j = i + 1; j <= i + 2 && j < items.size(); j++) if (items.get(j) instanceof Vod vod) mBillboard.prefetch(vod);
            return;
        }
    }

    private void showFirst(ObjectAdapter adapter) {
        for (int i = 0; i < adapter.size(); i++) {
            if (adapter.get(i) instanceof ListRow row && row.getAdapter().size() > 0 && row.getAdapter().get(0) instanceof Vod vod) {
                onFocus(vod);
                return;
            }
        }
    }

    @Override
    public void onMore(LibraryPlanner.Row row) {
        openGrid(row.title, row.extend, false);
    }

    @Override
    public void onItemClick(Vod item) {
        Site site = getSite();
        if (item.isAction()) mViewModel.action(mKey, item.getAction());
        else if (item.isFolder()) VodActivity.start(this, mKey, Result.folder(item));
        else if (site.isIndex()) CollectActivity.start(this, item.getName());
        else VideoActivity.start(this, mKey, item.getId(), item.getName(), item.getPic(), mTab != null && mTab.type.isFolder() ? item.getName() : null);
    }

    @Override
    public boolean onLongClick(Vod item) {
        if (item.isAction() || item.isFolder()) return false;
        CollectActivity.start(this, item.getName());
        return true;
    }

    @Override
    public void onItemClick(Func item) {
        int res = item.getResId();
        if (res == R.string.home_nf_home) finish();
        else if (res == R.string.home_vod) takeFocus(true);
        else if (res == R.string.home_live) LiveActivity.start(this);
        else if (res == R.string.home_keep) KeepActivity.start(this);
        else if (res == R.string.home_push) PushActivity.start(this);
        else if (res == R.string.home_search) SearchActivity.start(this);
        else if (res == R.string.home_setting) SettingActivity.start(this);
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
    protected void onDestroy() {
        mToken++;
        App.removeCallbacks(mTabTask);
        mBillboard.cancel();
        super.onDestroy();
    }
}
