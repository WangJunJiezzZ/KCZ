package com.fongmi.android.tv.model;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.bean.Class;
import com.fongmi.android.tv.bean.Filter;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.utils.HomeRowPlanner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;

/** Mobile-only feed. Requests are serial, bounded and cancelled on source change. */
public class MobileHomeViewModel extends ViewModel {
    public record Row(String title, Class type, List<Vod> items) {}

    private final MutableLiveData<List<Row>> rows = new MutableLiveData<>();
    private enum TaskType { CATEGORY }
    private final ViewModelTaskRunner<TaskType> tasks = new ViewModelTaskRunner<>(TaskType.class);
    private volatile int generation;

    public LiveData<List<Row>> getRows() { return rows; }

    public void load(String key, Result home) {
        int token = ++generation;
        tasks.cancelAll();
        List<Row> initial = new ArrayList<>();
        if (!home.getList().isEmpty()) initial.add(new Row("为你推荐", null, new ArrayList<>(home.getList())));
        rows.setValue(initial);
        LinkedHashSet<Class> categories = new LinkedHashSet<>();
        for (HomeRowPlanner.Genre genre : HomeRowPlanner.Genre.values()) {
            Class type = HomeRowPlanner.pick(home.getTypes(), genre);
            if (type != null && !type.isFolder()) categories.add(type);
        }
        if (categories.isEmpty()) for (Class type : home.getTypes()) {
            if (!type.isFolder()) categories.add(type);
            if (categories.size() == 5) break;
        }
        loadNext(key, new ArrayList<>(categories), 0, new ArrayList<>(initial), token);
    }

    private void loadNext(String key, List<Class> categories, int index, List<Row> loaded, int token) {
        if (token != generation || index >= categories.size()) return;
        Class type = categories.get(index);
        HashMap<String, String> extend = new HashMap<>();
        for (Filter filter : type.getFilters()) if (filter.getInit() != null) extend.put(filter.getKey(), filter.getInit());
        tasks.execute(TaskType.CATEGORY, Constant.TIMEOUT_VOD,
                () -> SiteApi.categoryContent(key, type.getTypeId(), "1", true, extend),
                result -> App.post(() -> {
                    if (token != generation) return;
                    if (!result.getList().isEmpty()) {
                        loaded.add(new Row(type.getTypeName(), type, new ArrayList<>(result.getList().subList(0, Math.min(20, result.getList().size())))));
                        rows.setValue(new ArrayList<>(loaded));
                    }
                    loadNext(key, categories, index + 1, loaded, token);
                }), error -> App.post(() -> loadNext(key, categories, index + 1, loaded, token)));
    }

    @Override protected void onCleared() {
        generation++;
        tasks.cancelAll();
    }
}
