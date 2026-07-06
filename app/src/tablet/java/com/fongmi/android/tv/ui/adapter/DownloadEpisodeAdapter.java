package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Episode;

import java.util.List;

public class DownloadEpisodeAdapter extends RecyclerView.Adapter<DownloadEpisodeAdapter.ViewHolder> {

    private final List<Episode> mItems;
    private final OnItemClickListener mListener;

    public interface OnItemClickListener {
        void onItemClick(Episode episode, int position);
    }

    public DownloadEpisodeAdapter(List<Episode> items, OnItemClickListener listener) {
        this.mItems = items;
        this.mListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.adapter_download_episode_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Episode item = mItems.get(position);
        String name = item.getName();
        if (name == null || name.isEmpty()) {
            name = "第" + (position + 1) + "集";
        }
        holder.text.setText(name);
        holder.text.setActivated(item.isSelected());
        holder.text.setSelected(item.isSelected());
        holder.itemView.setOnClickListener(v -> {
            if (mListener != null) {
                mListener.onItemClick(item, position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView text;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            text = itemView.findViewById(R.id.text);
        }
    }
}