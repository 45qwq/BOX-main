package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Download;
import com.fongmi.android.tv.bean.DownloadGroup;
import com.fongmi.android.tv.databinding.ActivityDownloadBinding;
import com.fongmi.android.tv.download.DownloadStateMachine;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.service.DownloadService;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.fongmi.android.tv.ui.adapter.DownloadAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

public class DownloadActivity extends BaseActivity implements DownloadAdapter.OnClickListener {

    private static final long POLL_INTERVAL_MS = 5000;

    private ActivityDownloadBinding mBinding;
    private DownloadAdapter mAdapter;
    private final Handler mRefreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable mRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            getDownloads();
            mRefreshHandler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    // 缓存当前标签页的所有下载数据（后台线程获取后缓存，避免重复查询）
    private List<Download> mCachedActiveList = new ArrayList<>();
    private List<Download> mCachedCompletedList = new ArrayList<>();

    private boolean isActiveTab = true; // true=下载中, false=已完成
    private boolean mInitialLoad = true; // 首次加载标记，只在首次加载时自动切换Tab

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, DownloadActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityDownloadBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        setRecyclerView();
        updateTabStyle();
        getDownloads();
    }

    @Override
    protected void initEvent() {
        mBinding.back.setOnClickListener(v -> finish());
        mBinding.delete.setOnClickListener(this::onDelete);
        mBinding.sort.setOnClickListener(this::onSortClick);
        mBinding.tabActive.setOnClickListener(v -> switchTab(true));
        mBinding.tabCompleted.setOnClickListener(v -> switchTab(false));
    }

    @Override
    protected void onResume() {
        super.onResume();
        getDownloads();
        mRefreshHandler.postDelayed(mRefreshRunnable, POLL_INTERVAL_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mRefreshHandler.removeCallbacks(mRefreshRunnable);
    }

    /**
     * 切换Tab
     */
    private void switchTab(boolean isActive) {
        if (this.isActiveTab == isActive) return;
        this.isActiveTab = isActive;
        updateTabStyle();
        // 切换Tab时更新适配器模式并刷新
        mAdapter.setTabMode(isActiveTab
                ? DownloadAdapter.TabMode.ACTIVE
                : DownloadAdapter.TabMode.COMPLETED);
        refreshCurrentTab();
    }

    /**
     * 更新Tab样式
     */
    private void updateTabStyle() {
        if (isActiveTab) {
            mBinding.tabActive.setBackgroundResource(R.drawable.bg_tab_left_selected);
            mBinding.tabActive.setTextColor(ContextCompat.getColor(this, R.color.white));
            mBinding.tabCompleted.setBackgroundResource(R.drawable.bg_tab_right_unselected);
            mBinding.tabCompleted.setTextColor(ContextCompat.getColor(this, R.color.grey_500));
        } else {
            mBinding.tabActive.setBackgroundResource(R.drawable.bg_tab_left_unselected);
            mBinding.tabActive.setTextColor(ContextCompat.getColor(this, R.color.grey_500));
            mBinding.tabCompleted.setBackgroundResource(R.drawable.bg_tab_right_selected);
            mBinding.tabCompleted.setTextColor(ContextCompat.getColor(this, R.color.white));
        }
    }

    private void setRecyclerView() {
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.setItemAnimator(null);
        mBinding.recycler.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new DownloadAdapter(this);
        mAdapter.setTabMode(DownloadAdapter.TabMode.ACTIVE);
        mBinding.recycler.setAdapter(mAdapter);
    }

    /**
     * 获取所有下载并分类
     */
    private void getDownloads() {
        App.execute(() -> {
            List<Download> all = Download.getMergedDownloads();
            List<Download> active = new ArrayList<>();
            List<Download> completed = new ArrayList<>();
            for (Download d : all) {
                if (DownloadStateMachine.STATUS_COMPLETED.equals(d.getStatus())
                        || d.getStatusInt() == Download.STATUS_COMPLETED) {
                    completed.add(d);
                } else {
                    active.add(d);
                }
            }
            // 缓存数据
            mCachedActiveList = active;
            mCachedCompletedList = completed;

            App.post(() -> {
                // 首次加载时自动切换到有内容的Tab；之后用户手动切换不受干扰
                if (mInitialLoad) {
                    mInitialLoad = false;
                    if (isActiveTab && active.isEmpty() && !completed.isEmpty()) {
                        switchTab(false);
                        return;
                    } else if (!isActiveTab && completed.isEmpty() && !active.isEmpty()) {
                        switchTab(true);
                        return;
                    }
                }
                refreshCurrentTab();
            });
        });
    }

    /**
     * 刷新当前标签页
     */
    private void refreshCurrentTab() {
        List<Download> activeList = sortDownloads(new ArrayList<>(mCachedActiveList));
        List<Download> completedList = sortDownloads(new ArrayList<>(mCachedCompletedList));

        mAdapter.smartUpdate(activeList, completedList);
        updateEmptyState();
        updateTabVisibility();
    }

    /**
     * 排序下载列表
     */
    private List<Download> sortDownloads(List<Download> list) {
        if (list == null || list.isEmpty()) return list;
        // 已完成列表按时间降序
        if (!isActiveTab) {
            java.util.Collections.sort(list, (a, b) ->
                    Long.compare(b.getCreateTime(), a.getCreateTime()));
        }
        return list;
    }

    /**
     * 更新Tab可见性
     */
    private void updateTabVisibility() {
        boolean hasActive = !mCachedActiveList.isEmpty();
        boolean hasCompleted = !mCachedCompletedList.isEmpty();
        // 只要有任何数据就显示Tab栏，允许用户切换
        mBinding.tabBar.setVisibility((hasActive || hasCompleted) ? View.VISIBLE : View.GONE);
    }

    private void updateEmptyState() {
        boolean isEmpty = mAdapter.isEmpty();
        mBinding.emptyLayout.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        mBinding.recycler.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        mBinding.delete.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        mBinding.sort.setVisibility(isEmpty ? View.GONE : View.VISIBLE);

        // 更新空状态文案
        if (isEmpty) {
            String tabName = isActiveTab ? "下载中" : "已完成";
            mBinding.emptyText.setText("暂无" + tabName + "的下载项");
        }
    }

    private void onDelete(View view) {
        if (mAdapter.isEmpty()) return;

        List<Download> items = mAdapter.getItems();
        String tabName = isActiveTab ? "下载中" : "已完成";

        new MaterialAlertDialogBuilder(this)
                .setTitle("清空" + tabName + "列表")
                .setMessage("当前 " + tabName + " 共 " + items.size() + " 个下载项\n\n是否删除所有文件？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除文件和记录", (dialog, which) -> {
                    for (Download d : items) DownloadService.cancelDownload(d.getId());
                    App.execute(() -> {
                        for (Download d : items) d.deleteWithFile();
                        App.post(this::getDownloads);
                    });
                })
                .show();
    }

    private void onSortClick(View view) {
        // 下载中标签页支持排序
        if (!isActiveTab) return;

        String[] options = {"默认顺序", "按时间升序", "按速度排序"};
        new MaterialAlertDialogBuilder(this)
                .setTitle("排序方式")
                .setItems(options, (dialog, which) -> {
                    List<Download> active = new ArrayList<>(mCachedActiveList);
                    switch (which) {
                        case 1:
                            java.util.Collections.sort(active, (a, b) ->
                                    Long.compare(a.getCreateTime(), b.getCreateTime()));
                            break;
                        case 2:
                            java.util.Collections.sort(active, (a, b) ->
                                    Long.compare(b.getSpeed(), a.getSpeed()));
                            break;
                        default:
                            java.util.Collections.sort(active, (a, b) ->
                                    Long.compare(b.getCreateTime(), a.getCreateTime()));
                            break;
                    }
                    mCachedActiveList = active;
                    mAdapter.smartUpdate(active, mCachedCompletedList);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    public void onItemClick(Download item) {
        String status = item.getStatus();
        if (DownloadStateMachine.STATUS_COMPLETED.equals(status) || item.getProgress() >= 100) {
            String filePath = item.getFilePath();
            if (filePath != null && !filePath.isEmpty()) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle("播放视频")
                        .setMessage("是否播放 " + item.getVodName() + "？")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("播放", (dialog, which) -> {
                            VideoActivity.file(DownloadActivity.this, filePath);
                        })
                        .show();
            } else {
                com.fongmi.android.tv.utils.Notify.show("找不到视频文件");
            }
        } else if (DownloadStateMachine.STATUS_FAILED.equals(status)) {
            String errorMsg = item.getErrorMsg();
            String message = item.getVodName() + "\n失败原因: " +
                    (errorMsg != null && !errorMsg.isEmpty() ? errorMsg : "未知错误") +
                    "\n是否重新下载？";
            new MaterialAlertDialogBuilder(this)
                    .setTitle("下载失败")
                    .setMessage(message)
                    .setNegativeButton("取消", null)
                    .setPositiveButton("重新下载", (dialog, which) -> {
                        DownloadService.retryDownload(item.getId());
                    })
                    .show();
        } else {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("下载中")
                    .setMessage(item.getVodName() + "\n进度: " + item.getProgress() + "%\n是否取消下载？")
                    .setNegativeButton("继续下载", null)
                    .setPositiveButton("取消下载", (dialog, which) -> {
                        DownloadService.cancelDownload(item.getId());
                        App.execute(() -> {
                            item.deleteWithFile();
                            App.post(this::getDownloads);
                        });
                    })
                    .show();
        }
    }

    @Override
    public void onActionClick(Download item) {
        String status = item.getStatus();
        boolean isInProgress = DownloadStateMachine.isActive(status);

        String title = isInProgress ? "取消下载" : "删除下载";
        String message = item.getVodName();

        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("取消", null)
                .setPositiveButton(isInProgress ? "取消下载并删除文件" : "删除文件和记录", (dialog, which) -> {
                    DownloadService.cancelDownload(item.getId());
                    App.execute(() -> {
                        item.deleteWithFile();
                        App.post(this::getDownloads);
                    });
                })
                .show();
    }

    @Override
    public void onItemLongClick(Download item) {
        String status = item.getStatus();
        boolean isInProgress = DownloadStateMachine.isActive(status);

        String message = "视频名称：" + item.getVodName();
        if (item.getFilePath() != null && !item.getFilePath().isEmpty()) {
            message += "\n文件路径：" + item.getFilePath();
        }
        message += "\n状态：" + DownloadStateMachine.getStatusDisplay(status);
        if (DownloadStateMachine.STATUS_FAILED.equals(status)) {
            String errorMsg = item.getErrorMsg();
            if (errorMsg != null && !errorMsg.isEmpty()) {
                message += "\n失败原因：" + errorMsg;
            }
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("下载详情")
                .setMessage(message)
                .setNegativeButton("取消", null)
                .setPositiveButton(isInProgress ? "取消下载并删除文件" : "删除文件和记录", (dialog, which) -> {
                    DownloadService.cancelDownload(item.getId());
                    App.execute(() -> {
                        item.deleteWithFile();
                        App.post(this::getDownloads);
                    });
                })
                .show();
    }

    @Override
    public void onGroupClick(DownloadGroup group) {
        // 长按分组弹出批量操作菜单
        String message = "剧集：" + group.getGroupName() + "\n"
                + "集数：" + group.getChildCount() + "集\n"
                + "总大小：" + getGroupTotalSize(group);

        new MaterialAlertDialogBuilder(this)
                .setTitle("批量操作")
                .setMessage(message)
                .setNegativeButton("取消", null)
                .setPositiveButton("删除所有文件", (dialog, which) -> {
                    App.execute(() -> {
                        for (Download d : group.getChildren()) {
                            d.deleteWithFile();
                        }
                        App.post(this::getDownloads);
                    });
                })
                .setNeutralButton("仅删除记录", (dialog, which) -> {
                    App.execute(() -> {
                        for (Download d : group.getChildren()) {
                            d.delete();
                        }
                        App.post(this::getDownloads);
                    });
                })
                .show();
    }

    /**
     * 计算分组总大小
     */
    private String getGroupTotalSize(DownloadGroup group) {
        long total = 0;
        for (Download d : group.getChildren()) {
            if (d.getFilePath() != null && !d.getFilePath().isEmpty()) {
                java.io.File f = new java.io.File(d.getFilePath());
                if (f.exists()) total += f.length();
            }
        }
        if (total < 1024) return total + "B";
        if (total < 1024 * 1024) return String.format("%.1fKB", total / 1024.0);
        if (total < 1024 * 1024 * 1024) return String.format("%.1fMB", total / (1024.0 * 1024.0));
        return String.format("%.1fGB", total / (1024.0 * 1024.0 * 1024.0));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (event.getType() == RefreshEvent.Type.DOWNLOAD) {
            getDownloads();
        }
    }
}