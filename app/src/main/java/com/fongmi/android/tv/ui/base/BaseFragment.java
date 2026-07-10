package com.fongmi.android.tv.ui.base;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewbinding.ViewBinding;

public abstract class BaseFragment extends Fragment {

    protected abstract ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container);

    private boolean init;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return getBinding(inflater, container).getRoot();
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
        onVisible();
    }
}
