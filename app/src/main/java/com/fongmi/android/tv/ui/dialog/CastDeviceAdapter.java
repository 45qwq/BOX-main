package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.databinding.ItemCastDeviceBinding;

import org.fourthline.cling.model.meta.Device;

import java.util.List;

public class CastDeviceAdapter extends RecyclerView.Adapter<CastDeviceAdapter.ViewHolder> {

    public interface OnClickListener {
        void onItemClick(Device<?, ?, ?> device, int position);
    }

    private final List<Device<?, ?, ?>> items;
    private final OnClickListener listener;
    private int selectedPosition = -1;

    public CastDeviceAdapter(List<Device<?, ?, ?>> items, OnClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    public void setSelectedPosition(int position) {
        this.selectedPosition = position;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemCastDeviceBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Device<?, ?, ?> device = items.get(position);
        holder.binding.tvDeviceName.setText(device.getDetails().getFriendlyName());
        boolean selected = position == selectedPosition;
        holder.binding.tvStatus.setText(selected ? "已连接" : "未连接");
        holder.binding.tvStatus.setTextColor(selected ? 0xFF3D62DE : 0x80FFFFFF);
        holder.itemView.setOnClickListener(v -> {
            int oldPos = selectedPosition;
            selectedPosition = holder.getBindingAdapterPosition();
            if (oldPos >= 0) notifyItemChanged(oldPos);
            notifyItemChanged(selectedPosition);
            if (listener != null) listener.onItemClick(device, selectedPosition);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemCastDeviceBinding binding;

        ViewHolder(ItemCastDeviceBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
