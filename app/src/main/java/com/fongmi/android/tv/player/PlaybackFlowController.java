package com.fongmi.android.tv.player;

import android.content.Context;
import android.view.View;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.databinding.ActivityVideoBinding;
import com.fongmi.android.tv.ui.adapter.FlagAdapter;
import com.fongmi.android.tv.ui.adapter.ParseAdapter;
import com.fongmi.android.tv.utils.Notify;

import androidx.recyclerview.widget.RecyclerView;

public class PlaybackFlowController {

    private final Context mContext;
    private final ActivityVideoBinding mBinding;
    private final ParseAdapter mParseAdapter;
    private final FlagAdapter mFlagAdapter;
    private final SearchController mSearchController;
    private final FlowCallback callback;

    public interface FlowCallback {
        boolean isUseParse();
        boolean isChangeable();
        void onParseItemClick(Parse item);
        void onFlagItemClick(Flag item);
    }

    public PlaybackFlowController(Context context, ActivityVideoBinding binding, ParseAdapter parseAdapter, FlagAdapter flagAdapter, SearchController searchController, FlowCallback callback) {
        this.mContext = context;
        this.mBinding = binding;
        this.mParseAdapter = parseAdapter;
        this.mFlagAdapter = flagAdapter;
        this.mSearchController = searchController;
        this.callback = callback;
    }

    public void startFlow() {
        if (!callback.isChangeable()) return;
        if (callback.isUseParse()) checkParse();
        else checkFlag();
    }

    private void checkParse() {
        int position = mParseAdapter.getPosition();
        boolean last = position == mParseAdapter.getItemCount() - 1;
        boolean pass = position == 0 || last;
        if (last) initParse();
        if (pass) checkFlag();
        else nextParse(position);
    }

    private void initParse() {
        if (mParseAdapter.isEmpty()) return;
        setParse(mParseAdapter.first());
    }

    private void checkFlag() {
        int position = isGone(mBinding.flag) ? -1 : mFlagAdapter.getPosition();
        if (position == mFlagAdapter.getItemCount() - 1) {
            if (mSearchController != null) mSearchController.checkSearch(false);
        } else nextFlag(position);
    }

    private void nextParse(int position) {
        Parse parse = mParseAdapter.get(position + 1);
        Notify.show(mContext.getString(R.string.play_switch_parse, parse.getName()));
        callback.onParseItemClick(parse);
    }

    private void nextFlag(int position) {
        Flag flag = mFlagAdapter.get(position + 1);
        Notify.show(mContext.getString(R.string.play_switch_flag, flag.getFlag()));
        callback.onFlagItemClick(flag);
    }

    private void setParse(Parse item) {
        VodConfig.get().setParse(item);
        notifyItemChanged(mParseAdapter);
    }

    private static boolean isGone(View view) {
        return view.getVisibility() == View.GONE;
    }

    private static void notifyItemChanged(RecyclerView.Adapter<?> adapter) {
        adapter.notifyItemRangeChanged(0, adapter.getItemCount());
    }
}
