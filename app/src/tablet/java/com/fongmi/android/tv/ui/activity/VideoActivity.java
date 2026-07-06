package com.fongmi.android.tv.ui.activity;

import android.view.ViewGroup;
import android.widget.RelativeLayout;

import com.fongmi.android.tv.common.BaseVideoActivity;

public class VideoActivity extends BaseVideoActivity {

    /**
     * 平板端特有：创建匹配父布局类型的 MATCH_PARENT LayoutParams。
     * 平板布局可能使用 RelativeLayout、FrameLayout 或 LinearLayout 作为视频容器，
     * 需要根据实际父布局类型创建对应的 LayoutParams。
     */
    @Override
    protected ViewGroup.LayoutParams createMatchParentLayoutParams() {
        ViewGroup parent = (ViewGroup) mBinding.video.getParent();
        if (parent instanceof RelativeLayout) {
            return new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
        } else if (parent instanceof android.widget.FrameLayout) {
            return new android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT);
        } else if (parent instanceof android.widget.LinearLayout) {
            return new android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.MATCH_PARENT);
        } else {
            return new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }
}