package com.fongmi.android.tv.ui.base;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewbinding.ViewBinding;

public abstract class BaseFragment extends Fragment {

    protected ActivityResultLauncher<Intent> pickLauncher;

    protected abstract ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container);

    private boolean init;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return getBinding(inflater, container).getRoot();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pickLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) onPickFile(result.getData().getData());
        });
    }

    /** 子类覆写以接收文件选择结果（原 REQUEST_PICK_FILE 行为）。默认空实现。 */
    protected void onPickFile(Uri uri) {
    }

    public ActivityResultLauncher<Intent> getPickLauncher() {
        return pickLauncher;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        initView();
        initEvent();
    }

    protected void initView() {
    }

    protected void initEvent() {
    }

    protected void initData() {
    }

    /**
     * 触发懒加载初始化（只执行一次）。
     * 由 onResume 触发，替代已废弃的 setUserVisibleHint。
     * 用 isHidden() 排除 ViewPager/FragmentStateManager 中"非前台但仍 onResume"的 Fragment，
     * 避免后台 Fragment 提前加载数据浪费流量与线程。
     * 若需要更精细的延迟加载控制，可在子类重写并通过
     * FragmentTransaction.setMaxLifecycle 实现。
     */
    private void onVisible() {
        if (init) return;
        initData();
        init = true;
    }

    public boolean canBack() {
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
        // isHidden() 为 true 说明当前不是前台展示的 Fragment（被 FragmentStateManager.hide 了），不应触发展示型懒加载
        if (!isHidden()) onVisible();
    }
}
