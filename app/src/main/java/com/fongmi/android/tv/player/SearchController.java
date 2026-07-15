package com.fongmi.android.tv.player;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.ActivityVideoBinding;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.ui.adapter.QuickAdapter;
import com.fongmi.android.tv.utils.ThreadPools;
import com.fongmi.android.tv.utils.Notify;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SearchController {

    private final Context mContext;
    private final ActivityVideoBinding mBinding;
    private final SiteViewModel mViewModel;
    private final QuickAdapter mQuickAdapter;
    private final Callback mCallback;
    private kotlinx.coroutines.Job mSearchJob;
    private final List<String> mBroken = new ArrayList<>();
    private boolean initAuto;
    private boolean autoMode;

    public interface Callback {
        void onSiteSwitched(Vod item);
    }

    public SearchController(Context context, ActivityVideoBinding binding, SiteViewModel viewModel, QuickAdapter quickAdapter, Callback callback) {
        this.mContext = context;
        this.mBinding = binding;
        this.mViewModel = viewModel;
        this.mQuickAdapter = quickAdapter;
        this.mCallback = callback;
    }

    public void setAutoMode(boolean autoMode) {
        this.autoMode = autoMode;
    }

    public boolean isAutoMode() {
        return autoMode;
    }

    public void setInitAuto(boolean initAuto) {
        this.initAuto = initAuto;
    }

    public boolean isInitAuto() {
        return initAuto;
    }

    public void checkSearch(boolean force) {
        if (mQuickAdapter.isEmpty()) {
            String keyword = mBinding.name.getText().toString();
            if (!TextUtils.isEmpty(keyword)) initSearch(keyword, true);
        } else if (isAutoMode() || force) {
            nextSite();
        }
    }

    public void initSearch(String keyword, boolean auto) {
        stopSearch();
        setAutoMode(auto);
        setInitAuto(auto);
        startSearch(keyword);
    }

    private boolean isPass(Site item) {
        if (isAutoMode() && !item.isChangeable()) return false;
        return item.isSearchable();
    }

    public void startSearch(String keyword) {
        stopSearch();
        mQuickAdapter.clear();
        List<Site> sites = new ArrayList<>();
        for (Site item : VodConfig.get().getSites()) if (isPass(item)) sites.add(item);
        List<Runnable> tasks = new ArrayList<>();
        for (Site site : sites) tasks.add(() -> search(site, keyword));
        ThreadPools.SearchController.get().start(20, tasks);
    }

    public void stopSearch() {
        ThreadPools.SearchController.get().cancel();
    }

    private void search(Site site, String keyword) {
        if (Thread.currentThread().isInterrupted()) return;
        try {
            mViewModel.searchContent(site, keyword, true);
        } catch (Throwable e) {
        }
    }

    public void onSearchResult(Result result) {
        String keyword = mBinding.name.getText().toString();
        List<Vod> items = result.getList();
        Iterator<Vod> iterator = items.iterator();
        while (iterator.hasNext()) if (mismatch(iterator.next(), keyword)) iterator.remove();
        mBinding.quick.setVisibility(View.VISIBLE);
        mQuickAdapter.addAll(items);
        if (isInitAuto()) nextSite();
    }

    private boolean mismatch(Vod item, String keyword) {
        if (keyword.equals(item.getVodId())) return true;
        if (mBroken.contains(item.getVodId())) return true;
        if (isAutoMode()) return !item.getVodName().equals(keyword);
        else return !item.getVodName().contains(keyword);
    }

    public void nextSite() {
        if (mQuickAdapter.isEmpty()) return;
        Vod item = mQuickAdapter.get(0);
        Notify.show(mContext.getString(R.string.play_switch_site, item.getSiteName()));
        mQuickAdapter.remove(0);
        mBroken.add(item.getVodId());
        setInitAuto(false);
        if (mCallback != null) mCallback.onSiteSwitched(item);
    }
}
