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
import com.fongmi.android.tv.bean.DownloadGroup;
import com.fongmi.android.tv.databinding.AdapterDownloadBinding;
import com.fongmi.android.tv.databinding.AdapterDownloadGroupBinding;
import com.fongmi.android.tv.download.DownloadStateMachine;
import com.fongmi.android.tv.utils.UrlUtil;

import java.util.ArrayList;
import java.util.List;

public class DownloadAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_ITEM = 0;
    private static final int TYPE_GROUP_HEADER = 1;

    private final OnClickListener listener;
    private final List<Object> displayItems;
    private TabMode tabMode = TabMode.ACTIVE;

    public enum TabMode {
        ACTIVE,
        COMPLETED
    }

    public interface OnClickListener {
        void onItemClick(Download item);
        void onActionClick(Download item);
        void onItemLongClick(Download item);
        void onGroupClick(DownloadGroup group);
    }

    public DownloadAdapter(OnClickListener listener) {
        this.listener = listener;
        this.displayItems = new ArrayList<>();
    }

    public void setTabMode(TabMode mode) {
        this.tabMode = mode;
    }

    public TabMode getTabMode() {
        return tabMode;
    }

    public void addAll(List<Download> list) {
        displayItems.clear();
        displayItems.addAll(list);
        notifyDataSetChanged();
    }

    public void smartUpdate(List<Download> activeList, List<Download> completedList) {
        if (tabMode == TabMode.ACTIVE) {
            smartUpdateFlat(activeList);
        } else {
            smartUpdateGrouped(completedList);
        }
    }

    /**
     * 平面列表智能更新（下载中标签）
     * 使用 notifyDataSetChanged 避免逐个动画导致的闪烁
     */
    private void smartUpdateFlat(List<Download> newList) {
        if (newList == null) newList = new ArrayList<>();
        displayItems.clear();
        displayItems.addAll(newList);
        notifyDataSetChanged();
    }

    /**
     * 分组列表智能更新（已完成标签）
     * 使用 notifyDataSetChanged 避免逐项动画导致的闪烁
     */
    private void smartUpdateGrouped(List<Download> completedList) {
        if (completedList == null) completedList = new ArrayList<>();
        List<DownloadGroup> groups = DownloadGroup.groupBySeries(completedList);

        List<Object> newDisplayItems = new ArrayList<>();
        for (DownloadGroup group : groups) {
            newDisplayItems.add(group);
            int existingGroupPos = findGroupByKey(group.getGroupKey());
            if (existingGroupPos >= 0) {
                DownloadGroup existing = (DownloadGroup) displayItems.get(existingGroupPos);
                group.setExpanded(existing.isExpanded());
            }
            if (group.isExpanded()) {
                newDisplayItems.addAll(group.getChildren());
            }
        }

        displayItems.clear();
        displayItems.addAll(newDisplayItems);
        notifyDataSetChanged();
    }

    private int findItemById(String id) {
        for (int i = 0; i < displayItems.size(); i++) {
            if (displayItems.get(i) instanceof Download) {
                if (((Download) displayItems.get(i)).getId().equals(id)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private int findGroupByKey(String key) {
        for (int i = 0; i < displayItems.size(); i++) {
            if (displayItems.get(i) instanceof DownloadGroup) {
                if (((DownloadGroup) displayItems.get(i)).getGroupKey().equals(key)) {
                    return i;
                }
            }
        }
        return -1;
    }

    public void toggleGroup(int position) {
        Object item = displayItems.get(position);
        if (!(item instanceof DownloadGroup)) return;

        DownloadGroup group = (DownloadGroup) item;
        boolean wasExpanded = group.isExpanded();
        group.setExpanded(!wasExpanded);

        if (wasExpanded) {
            int childCount = group.getChildCount();
            displayItems.subList(position + 1, position + 1 + childCount).clear();
            notifyItemChanged(position);
            notifyItemRangeRemoved(position + 1, childCount);
        } else {
            List<Download> children = group.getChildren();
            displayItems.addAll(position + 1, children);
            notifyItemChanged(position);
            notifyItemRangeInserted(position + 1, children.size());
        }
    }

    public List<Download> getItems() {
        List<Download> result = new ArrayList<>();
        for (Object item : displayItems) {
            if (item instanceof Download) {
                result.add((Download) item);
            } else if (item instanceof DownloadGroup) {
                result.addAll(((DownloadGroup) item).getChildren());
            }
        }
        return result;
    }

    public boolean isEmpty() {
        return displayItems.isEmpty();
    }

    @Override
    public int getItemCount() {
        return displayItems.size();
    }

    @Override
    public int getItemViewType(int position) {
        if (displayItems.get(position) instanceof DownloadGroup) {
            return TYPE_GROUP_HEADER;
        }
        return TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_GROUP_HEADER) {
            AdapterDownloadGroupBinding binding = AdapterDownloadGroupBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new GroupViewHolder(binding);
        }
        AdapterDownloadBinding binding = AdapterDownloadBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ItemViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object item = displayItems.get(position);
        if (holder instanceof GroupViewHolder) {
            ((GroupViewHolder) holder).bind((DownloadGroup) item, position);
        } else if (holder instanceof ItemViewHolder) {
            ((ItemViewHolder) holder).bind((Download) item, position);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads);
        } else if (holder instanceof ItemViewHolder) {
            Object item = displayItems.get(position);
            if (item instanceof Download) {
                ((ItemViewHolder) holder).updateProgress((Download) item);
            }
        }
    }

    // ==================== Item ViewHolder ====================

    class ItemViewHolder extends RecyclerView.ViewHolder {

        private final AdapterDownloadBinding binding;
        private String lastPicUrl = "";

        ItemViewHolder(AdapterDownloadBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Download item, int position) {
            binding.name.setText(item.getVodName());

            String picUrl = UrlUtil.convert(item.getVodPic());
            if (!picUrl.equals(lastPicUrl)) {
                lastPicUrl = picUrl;
                Glide.with(App.get()).load(picUrl).placeholder(R.drawable.ic_nav_vod).into(binding.cover);
            }

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

        void updateProgress(Download item) {
            updateStatus(item);
        }

        private void updateStatus(Download item) {
            String status = item.getStatus();

            if (DownloadStateMachine.STATUS_COMPLETED.equals(status) || item.getStatusInt() == Download.STATUS_COMPLETED) {
                binding.status.setText(R.string.download_completed);
                binding.progress.setProgress(100);
                binding.progressText.setText(R.string.download_completed);
                binding.action.setImageResource(R.drawable.ic_action_delete);
            } else if (DownloadStateMachine.STATUS_MERGING.equals(status)) {
                binding.status.setText(R.string.download_merging);
                binding.progress.setProgress(item.getProgress());
                binding.progressText.setText(R.string.download_merging);
                binding.action.setImageResource(R.drawable.ic_action_delete);
            } else if (DownloadStateMachine.STATUS_QUEUED.equals(status)) {
                binding.status.setText("排队中");
                binding.progress.setProgress(0);
                binding.progressText.setText("等待下载");
                binding.action.setImageResource(R.drawable.ic_action_delete);
            } else if (DownloadStateMachine.STATUS_FAILED.equals(status) || item.getStatusInt() == Download.STATUS_FAILED) {
                binding.status.setText(R.string.download_failed);
                binding.progress.setProgress(item.getProgress());
                String errorMsg = item.getErrorMsg();
                if (errorMsg != null && !errorMsg.isEmpty()) {
                    binding.progressText.setText(errorMsg);
                    binding.progressText.setVisibility(View.VISIBLE);
                } else {
                    binding.progressText.setText(item.getStatus());
                }
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
                if (item.getSegmentInfo() != null) {
                    progressText.append("  ").append(item.getSegmentInfo());
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

    // ==================== Group ViewHolder ====================

    class GroupViewHolder extends RecyclerView.ViewHolder {

        private final AdapterDownloadGroupBinding binding;

        GroupViewHolder(AdapterDownloadGroupBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(DownloadGroup group, int position) {
            binding.groupTitle.setText(group.getGroupName());
            binding.episodeCount.setText(group.getSubtitle());

            binding.expandIcon.setRotation(group.isExpanded() ? 0 : -90);

            binding.getRoot().setOnClickListener(v -> {
                toggleGroup(getAdapterPosition());
            });
            binding.getRoot().setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onGroupClick(group);
                    return true;
                }
                return false;
            });
        }
    }
}