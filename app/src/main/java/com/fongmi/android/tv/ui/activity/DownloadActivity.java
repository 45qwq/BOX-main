package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Download;
import com.fongmi.android.tv.databinding.ActivityDownloadBinding;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.service.DownloadService;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.fongmi.android.tv.ui.adapter.DownloadAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.utils.FileUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.List;

public class DownloadActivity extends BaseActivity implements DownloadAdapter.OnClickListener {

    private ActivityDownloadBinding mBinding;
    private DownloadAdapter mAdapter;
    private final Handler mRefreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable mRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            getDownloads();
            mRefreshHandler.postDelayed(this, 2000);
        }
    };

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
        getDownloads();
    }

    @Override
    protected void initEvent() {
        mBinding.back.setOnClickListener(v -> finish());
        mBinding.delete.setOnClickListener(this::onDelete);
    }

    @Override
    protected void onResume() {
        super.onResume();
        getDownloads();
        mRefreshHandler.postDelayed(mRefreshRunnable, 2000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mRefreshHandler.removeCallbacks(mRefreshRunnable);
    }

    private void setRecyclerView() {
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.setLayoutManager(new LinearLayoutManager(this));
        mBinding.recycler.setAdapter(mAdapter = new DownloadAdapter(this));
    }

    /**
     * 刷新列表（只更新有变化的项，避免闪烁）
     */
    private void getDownloads() {
        App.execute(() -> {
            List<Download> downloads = Download.getMergedDownloads();
            App.post(() -> {
                mAdapter.smartUpdate(downloads);
                updateEmptyState();
            });
        });
    }

    private void updateEmptyState() {
        boolean isEmpty = mAdapter.isEmpty();
        mBinding.emptyLayout.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        mBinding.recycler.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        mBinding.delete.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void onDelete(View view) {
        if (mAdapter.isEmpty()) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle("清空下载列表")
                .setMessage("当前共有 " + mAdapter.getItemCount() + " 个下载项\n\n请选择操作方式：")
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton("仅删除记录", (dialog, which) -> {
                    App.execute(() -> {
                        Download.clear();
                        App.post(this::getDownloads);
                    });
                })
                .setNeutralButton("删除记录和文件", (dialog, which) -> {
                    App.execute(() -> {
                        Download.clearWithFiles();
                        App.post(this::getDownloads);
                    });
                })
                .show();
    }

    @Override
    public void onItemClick(Download item) {
        String status = item.getStatus();
        if ("completed".equals(status) || item.getProgress() >= 100) {
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
        } else if ("failed".equals(status)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("下载失败")
                    .setMessage(item.getVodName() + "\n是否重新下载？")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("重新下载", (dialog, which) -> {
                        Intent intent = new Intent(this, DownloadService.class);
                        intent.setAction("ACTION_RETRY_DOWNLOAD");
                        intent.putExtra("download_id", item.getId());
                        startService(intent);
                    })
                    .show();
        } else {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("下载中")
                    .setMessage(item.getVodName() + "\n进度: " + item.getProgress() + "%\n是否取消下载？")
                    .setNegativeButton("继续下载", null)
                    .setPositiveButton("取消下载", (dialog, which) -> {
                        Intent intent = new Intent(this, DownloadService.class);
                        intent.setAction(DownloadService.ACTION_STOP);
                        intent.putExtra("download_id", item.getId());
                        startService(intent);
                        final Download deleteItem = item;
                        App.execute(() -> {
                            deleteItem.delete();
                            App.post(() -> {
                                mAdapter.remove(deleteItem);
                                updateEmptyState();
                            });
                        });
                    })
                    .show();
        }
    }

    @Override
    public void onActionClick(Download item) {
        String status = item.getStatus();
        boolean isInProgress = "downloading".equals(status) || "pending".equals(status) || "merging".equals(status);

        String title = isInProgress ? "取消下载" : "删除下载";
        String message = item.getVodName();
        if (isInProgress) {
            message += "\n\n该任务正在下载中，确定要取消吗？";
        } else if ("completed".equals(status)) {
            message += "\n\n确定删除下载记录？";
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(isInProgress ? "取消下载" : "仅删除记录", (dialog, which) -> {
                    Intent intent = new Intent(this, DownloadService.class);
                    intent.setAction(DownloadService.ACTION_STOP);
                    intent.putExtra("download_id", item.getId());
                    startService(intent);

                    final Download deleteItem = item;
                    App.execute(() -> {
                        if (isInProgress) deleteItem.deleteWithFile();
                        else deleteItem.delete();
                        App.post(() -> {
                            mAdapter.remove(deleteItem);
                            updateEmptyState();
                        });
                    });
                })
                .setNeutralButton("删除记录和文件", (dialog, which) -> {
                    Intent intent = new Intent(this, DownloadService.class);
                    intent.setAction(DownloadService.ACTION_STOP);
                    intent.putExtra("download_id", item.getId());
                    startService(intent);

                    final Download deleteItem = item;
                    App.execute(() -> {
                        deleteItem.deleteWithFile();
                        App.post(() -> {
                            mAdapter.remove(deleteItem);
                            updateEmptyState();
                        });
                    });
                })
                .show();
    }

    @Override
    public void onItemLongClick(Download item) {
        String status = item.getStatus();
        boolean isInProgress = "downloading".equals(status) || "pending".equals(status) || "merging".equals(status);

        String title = "删除下载";
        String message = "视频名称：" + item.getVodName();
        if (item.getFilePath() != null && !item.getFilePath().isEmpty()) {
            message += "\n文件路径：" + item.getFilePath();
        }
        message += "\n状态：" + status;
        if (isInProgress) {
            message += "\n\n该任务正在下载中，确定要取消并删除吗？";
        } else {
            message += "\n\n确定要删除该下载项吗？";
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(isInProgress ? "取消下载并删除" : "仅删除记录", (dialog, which) -> {
                    Intent intent = new Intent(this, DownloadService.class);
                    intent.setAction(DownloadService.ACTION_STOP);
                    intent.putExtra("download_id", item.getId());
                    startService(intent);

                    final Download deleteItem = item;
                    App.execute(() -> {
                        if (isInProgress) deleteItem.deleteWithFile();
                        else deleteItem.delete();
                        App.post(() -> {
                            mAdapter.remove(deleteItem);
                            updateEmptyState();
                        });
                    });
                })
                .setNeutralButton("删除记录和文件", (dialog, which) -> {
                    Intent intent = new Intent(this, DownloadService.class);
                    intent.setAction(DownloadService.ACTION_STOP);
                    intent.putExtra("download_id", item.getId());
                    startService(intent);

                    final Download deleteItem = item;
                    App.execute(() -> {
                        deleteItem.deleteWithFile();
                        App.post(() -> {
                            mAdapter.remove(deleteItem);
                            updateEmptyState();
                        });
                    });
                })
                .show();
    }

    /**
     * 接收下载刷新事件（EventBus，替代轮询）
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (event.getType() == RefreshEvent.Type.DOWNLOAD) {
            getDownloads();
        }
    }
}