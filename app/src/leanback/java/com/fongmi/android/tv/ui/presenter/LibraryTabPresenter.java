package com.fongmi.android.tv.ui.presenter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.leanback.widget.Presenter;

import com.fongmi.android.tv.bean.Class;
import com.fongmi.android.tv.databinding.AdapterLibraryTabBinding;

import java.util.function.Consumer;
import java.util.function.Predicate;

/** 二次开发：奈飞风格片库的顶部分类标签。焦点移到哪个标签就切到哪个分类（由页面延迟切换）；点击展开 / 收起该分类的筛选 */
public class LibraryTabPresenter extends Presenter {

    private final Predicate<Class> current;
    private final Consumer<Class> click;

    /** current：判断某个分类是不是当前显示的分类（画底部短线）；click：点击分类 */
    public LibraryTabPresenter(Predicate<Class> current, Consumer<Class> click) {
        this.current = current;
        this.click = click;
    }

    @NonNull
    @Override
    public Presenter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent) {
        return new Presenter.ViewHolder(AdapterLibraryTabBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false).getRoot());
    }

    @Override
    public void onBindViewHolder(@NonNull Presenter.ViewHolder viewHolder, Object object) {
        Class item = (Class) object;
        android.widget.TextView view = (android.widget.TextView) viewHolder.view;
        view.setText(item.getTypeName());
        view.setSelected(current.test(item));
        view.setTag(item);
        setOnClickListener(viewHolder, v -> click.accept(item));
    }

    @Override
    public void onUnbindViewHolder(@NonNull Presenter.ViewHolder viewHolder) {
    }
}
