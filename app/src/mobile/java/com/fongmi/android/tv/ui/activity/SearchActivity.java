package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivitySearchBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.fragment.SearchFragment;

public class SearchActivity extends BaseActivity {

    public static void start(Activity activity) {
        start(activity, "");
    }

    public static void start(Activity activity, String keyword) {
        Intent intent = new Intent(activity, SearchActivity.class);
        intent.putExtra("keyword", keyword);
        activity.startActivity(intent);
    }

    private String getKeyword() {
        return getIntent().getStringExtra("keyword");
    }

    @Override
    protected ViewBinding getBinding() {
        return ActivitySearchBinding.inflate(getLayoutInflater());
    }

    /** 二次开发：带关键词打开（长按影片、收藏、外部搜索）时，从结果页返回直接回到来源页，而不是露出搜索页 */
    private boolean direct;

    @Override
    protected void initView(Bundle savedInstanceState) {
        direct = savedInstanceState == null ? !TextUtils.isEmpty(getKeyword()) : savedInstanceState.getBoolean("direct");
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction().replace(R.id.container, SearchFragment.newInstance(getKeyword()), SearchFragment.class.getSimpleName()).commit();
        }
        // 返回键在 direct 模式下直接 finish 不弹栈；栈被弹空只可能是用户点了结果页标题去改关键词，之后按正常搜索页逻辑返回
        getSupportFragmentManager().addOnBackStackChangedListener(() -> {
            if (getSupportFragmentManager().getBackStackEntryCount() == 0) direct = false;
        });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("direct", direct);
    }

    @Override
    protected void onBackInvoked() {
        if (direct && getSupportFragmentManager().getBackStackEntryCount() > 0) {
            finish();
        } else if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            getSupportFragmentManager().popBackStack();
        } else {
            super.onBackInvoked();
        }
    }
}
