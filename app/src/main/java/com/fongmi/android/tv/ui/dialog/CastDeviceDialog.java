package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogCastDeviceBinding;
import com.fongmi.android.tv.databinding.ItemCastDeviceBinding;
import com.fongmi.android.tv.event.CastEvent;
import com.fongmi.android.tv.utils.UpnpCastManager;

import org.fourthline.cling.model.meta.Device;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

public class CastDeviceDialog extends BaseDialog implements CastDeviceAdapter.OnClickListener {

    private DialogCastDeviceBinding binding;
    private CastDeviceAdapter adapter;
    private final List<Device<?, ?, ?>> devices = new ArrayList<>();

    public static CastDeviceDialog create() {
        return new CastDeviceDialog();
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) {
            if (f instanceof CastDeviceDialog) return;
        }
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogCastDeviceBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        binding.recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recycler.setItemAnimator(null);
        adapter = new CastDeviceAdapter(devices, this);
        binding.recycler.setAdapter(adapter);
        // 显示已连接设备
        Device<?, ?, ?> selected = UpnpCastManager.getSelectedDevice();
        if (selected != null) {
            devices.add(selected);
            adapter.setSelectedPosition(0);
            binding.btnDisconnect.setVisibility(View.VISIBLE);
        }
        updateDeviceList();
        updateEmptyView();
    }

    @Override
    protected void initEvent() {
        binding.btnRefresh.setOnClickListener(v -> {
            binding.searchProgress.setVisibility(View.VISIBLE);
            UpnpCastManager.search();
        });
        binding.btnDisconnect.setOnClickListener(v -> {
            UpnpCastManager.disconnect();
            dismiss();
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onCastEvent(CastEvent event) {
        if (event instanceof CastEvent.DeviceFound) {
            updateDeviceList();
        } else if (event instanceof CastEvent.DeviceRemoved) {
            updateDeviceList();
        } else if (event instanceof CastEvent.SearchCompleted) {
            binding.searchProgress.setVisibility(View.GONE);
        } else if (event instanceof CastEvent.DeviceSelected) {
            dismiss();
        }
    }

    private void updateDeviceList() {
        binding.searchProgress.setVisibility(View.GONE);
        List<Device<?, ?, ?>> found = UpnpCastManager.getDevices();
        // 合并已选设备
        Device<?, ?, ?> selected = UpnpCastManager.getSelectedDevice();
        devices.clear();
        if (selected != null) devices.add(selected);
        for (Device<?, ?, ?> d : found) {
            if (selected == null || !d.getIdentity().getUdn().equals(selected.getIdentity().getUdn())) {
                devices.add(d);
            }
        }
        if (selected != null) adapter.setSelectedPosition(0);
        adapter.notifyDataSetChanged();
        updateEmptyView();
    }

    private void updateEmptyView() {
        boolean empty = devices.isEmpty();
        binding.emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.recycler.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onItemClick(Device<?, ?, ?> device, int position) {
        UpnpCastManager.selectDevice(device);
    }
}
