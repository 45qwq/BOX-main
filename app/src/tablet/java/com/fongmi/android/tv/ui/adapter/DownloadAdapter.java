package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Download;
import com.fongmi.android.tv.databinding.AdapterDownloadBinding;
import com.fongmi.android.tv.utils.UrlUtil;

import java.util.ArrayList;
import java.util.List;

public class DownloadAdapter extends RecyclerView.Adapter<DownloadAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final List<Download> items;

    public interface OnClickListener {
        void onItemClick(Download item);
        void onActionClick(Download item);
        void onItemLongClick(Download item);
    }

    public DownloadAdapter(OnClickListener listener) {
        this.listener = listener;
        this.items = new ArrayList<>();
    }

    public void addAll(List<Download> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    /**
     * 智能更新：只更新有变化的项，避免全量刷新导致闪烁
     * 进度/速度变化时使用payload局部刷新，不重新加载图片和名称
     */
    public void smartUpdate(List<Download> newList) {
        if (newList == null) return;

        // 删除不存在于新列表的项（按ID或vodName匹配）
        for (int i = items.size() - 1; i >= 0; i--) {
            boolean found = false;
            for (Download nd : newList) {
                if (nd.getId().equals(items.get(i).getId()) || nd.getVodName().equals(items.get(i).getVodName())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                items.remove(i);
                notifyItemRemoved(i);
            }
        }

        // 添加或更新项
        for (int i = 0; i < newList.size(); i++) {
            Download nd = newList.get(i);
            boolean found = false;
            for (int j = 0; j < items.size(); j++) {
                if (items.get(j).getId().equals(nd.getId()) || items.get(j).getVodName().equals(nd.getVodName())) {
                    found = true;
                    // 只在数据变化时更新
                    Download old = items.get(j);
                    if (needUpdate(old, nd)) {
                        items.set(j, nd);
                        // 只有进度/速度变化时用payload局部刷新，避免闪烁
                        if (old.getStatus() != null && old.getStatus().equals(nd.getStatus())) {
                            notifyItemChanged(j, "progress");
                        } else {
                            notifyItemChanged(j);
                        }
                    }
                    break;
                }
            }
            if (!found) {
                items.add(i, nd);
                notifyItemInserted(i);
            }
        }
    }

    /**
     * 判断是否需要更新（进度、状态、速度有变化才更新）
     * 已完成的任务不需要更新，避免闪烁
     */
    private boolean needUpdate(Download old, Download nd) {
        if ("completed".equals(old.getStatus())) return false;
        return old.getProgress() != nd.getProgress()
                || !old.getStatus().equals(nd.getStatus())
                || old.getSpeed() != nd.getSpeed();
    }

    public void update(Download download) {
        int position = -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getId().equals(download.getId())) {
                position = i;
                break;
            }
        }
        if (position != -1) {
            items.set(position, download);
            notifyItemChanged(position);
        }
    }

    public void remove(Download download) {
        int position = -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getId().equals(download.getId())) {
                position = i;
                break;
            }
        }
        if (position != -1) {
            items.remove(position);
            notifyItemRemoved(position);
        }
    }

    public List<Download> getItems() {
        return items;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterDownloadBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.initView(items.get(position));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads);
        } else {
            // payload局部更新，只更新进度/速度/状态
            Download item = items.get(position);
            holder.updateProgress(item);
        }
    }

    class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterDownloadBinding binding;
        private String lastPicUrl = "";

        ViewHolder(AdapterDownloadBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void initView(Download item) {
            binding.name.setText(item.getVodName());

            // 只在图片URL变化时才加载图片，避免Glide重复加载导致闪烁
            String picUrl = UrlUtil.convert(item.getVodPic());
            if (!picUrl.equals(lastPicUrl)) {
                lastPicUrl = picUrl;
                Glide.with(App.get()).load(picUrl).placeholder(R.drawable.ic_nav_vod).into(binding.cover);
            }

            // 文件路径
            if (item.getFilePath() != null && !item.getFilePath().isEmpty()) {
                binding.filePath.setText(item.getFilePath());
                binding.filePath.setVisibility(View.VISIBLE);
            } else {
                binding.filePath.setVisibility(View.GONE);
            }

            updateStatus(item);

            binding.getRoot().setOnClickListener(v -> listener.onItemClick(item));
            binding.getRoot().setOnLongClickListener(v -> {
                listener.onItemLongClick(item);
                return true;
            });
            binding.action.setOnClickListener(v -> listener.onActionClick(item));
        }

        /**
         * 局部更新进度（不重新加载图片、名称等，避免闪烁）
         */
        void updateProgress(Download item) {
            updateStatus(item);
        }

        private void updateStatus(Download item) {
            String status = item.getStatus();
            int statusInt = item.getStatusInt();

            if ("completed".equals(status) || statusInt == Download.STATUS_COMPLETED) {
                binding.status.setText(R.string.download_completed);
                binding.progress.setProgress(100);
                binding.progressText.setText(R.string.download_completed);
                binding.action.setImageResource(R.drawable.ic_action_delete);
            } else if ("merging".equals(status)) {
                binding.status.setText(R.string.download_merging);
                binding.progress.setProgress(item.getProgress());
                binding.progressText.setText(R.string.download_merging);
                binding.action.setImageResource(R.drawable.ic_action_delete);
            } else if ("queued".equals(status)) {
                binding.status.setText("排队中");
                binding.progress.setProgress(0);
                binding.progressText.setText("等待下载");
                binding.action.setImageResource(R.drawable.ic_action_delete);
            } else if ("failed".equals(status) || statusInt == Download.STATUS_FAILED) {
                binding.status.setText(R.string.download_failed);
                binding.progress.setProgress(item.getProgress());
                binding.progressText.setText(item.getStatus());
                binding.action.setImageResource(R.drawable.ic_action_delete);
            } else {
                binding.status.setText(R.string.downloading);
                binding.progress.setProgress(item.getProgress());
                StringBuilder progressText = new StringBuilder();
                if (item.getProgress() > 0) {
                    progressText.append(item.getProgress()).append("%");
                } else {
                    progressText.append("---");
                }
                if (item.getSpeed() > 0) {
                    progressText.append("  ").append(formatSpeed(item.getSpeed()));
                }
                binding.progressText.setText(progressText.toString());
                binding.action.setImageResource(R.drawable.ic_action_delete);
            }
        }

        private String formatSpeed(long speed) {
            if (speed < 1024) return speed + " B/s";
            if (speed < 1024 * 1024) return String.format("%.1f KB/s", speed / 1024.0);
            return String.format("%.1f MB/s", speed / (1024.0 * 1024.0));
        }
    }
}
