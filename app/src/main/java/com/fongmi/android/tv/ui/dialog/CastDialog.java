package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.android.cast.dlna.dmc.DLNACastManager;
import com.android.cast.dlna.dmc.OnDeviceRegistryListener;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.CastVideo;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Device;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.cast.CastController;
import com.fongmi.android.tv.cast.CastHttpClient;
import com.fongmi.android.tv.databinding.DialogDeviceBinding;
import com.fongmi.android.tv.event.ScanEvent;
import com.fongmi.android.tv.ui.activity.ScanActivity;
import com.fongmi.android.tv.ui.adapter.DeviceAdapter;
import com.fongmi.android.tv.utils.DLNADevice;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ScanTask;
import com.github.catvod.utils.Util;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.List;

public class CastDialog extends BaseDialog implements DeviceAdapter.OnClickListener, ScanTask.Listener, OnDeviceRegistryListener {

    private final CastHttpClient httpClient;
    private final CastController castController;
    private final ScanTask scanTask;

    private DialogDeviceBinding binding;
    private DeviceAdapter adapter;
    private Listener listener;
    private CastVideo video;
    private String historyStr;
    private boolean fm;

    public static CastDialog create() {
        return new CastDialog();
    }

    public CastDialog() {
        scanTask = new ScanTask(this);
        httpClient = new CastHttpClient();
        castController = new CastController();
    }

    public CastDialog history(History history) {
        String fd = history.getVodId();
        if (fd.contains("127.0.0.1")) fd = fd.replace("127.0.0.1", Util.getIp());
        this.historyStr = history.toString().replace(history.getVodId(), fd);
        return this;
    }

    public CastDialog video(CastVideo video) {
        this.video = video;
        return this;
    }

    public CastDialog fm(boolean fm) {
        this.fm = fm;
        return this;
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof BottomSheetDialogFragment) return;
        show(activity.getSupportFragmentManager(), null);
        this.listener = (Listener) activity;
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogDeviceBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        binding.scan.setVisibility(fm ? View.VISIBLE : View.GONE);
        EventBus.getDefault().register(this);
        setRecyclerView();
        getDevice();
        initDLNA();
    }

    @Override
    protected void initEvent() {
        binding.scan.setOnClickListener(v -> onScan());
        binding.refresh.setOnClickListener(v -> onRefresh());
    }

    private void setRecyclerView() {
        binding.recycler.setHasFixedSize(true);
        binding.recycler.setAdapter(adapter = new DeviceAdapter(this));
    }

    private void getDevice() {
        App.execute(() -> {
            List<Device> devices = fm ? Device.getAll() : new java.util.ArrayList<>();
            App.post(() -> {
                if (fm) adapter.addAll(devices);
                adapter.addAll(DLNADevice.get().getAll());
            });
        });
    }

    private void initDLNA() {
        DLNACastManager.INSTANCE.bindCastService(App.get());
        DLNACastManager.INSTANCE.registerDeviceListener(this);
    }

    private void onScan() {
        ScanActivity.start(getActivity());
    }

    private void onRefresh() {
        if (fm) scanTask.start(adapter.getIps());
        DLNACastManager.INSTANCE.search(null);
        adapter.clear();
    }

    private void onCasted() {
        listener.onCasted();
        dismiss();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onScanEvent(ScanEvent event) {
        scanTask.start(event.getAddress());
    }

    @Override
    public void onFind(List<Device> devices) {
        if (!devices.isEmpty()) adapter.addAll(devices);
    }

    @Override
    public void onDeviceAdded(@NonNull org.fourthline.cling.model.meta.Device<?, ?, ?> device) {
        adapter.addAll(DLNADevice.get().add(device));
    }

    @Override
    public void onDeviceRemoved(@NonNull org.fourthline.cling.model.meta.Device<?, ?, ?> device) {
        adapter.remove(DLNADevice.get().remove(device));
    }

    @Override
    public void onItemClick(Device item) {
        if (item.isDLNA()) {
            castController.connect(DLNADevice.get().find(item), video, new CastController.Callback() {
                @Override
                public void onCasted() {
                    CastDialog.this.onCasted();
                }

                @Override
                public void onError(String message) {
                    Notify.show(message);
                }

                @Override
                public void onDisconnected() {
                    Notify.show(R.string.device_offline);
                }
            });
        } else {
            httpClient.send(item, Config.vod(), historyStr, new CastHttpClient.Callback() {
                @Override
                public void onCasted() {
                    CastDialog.this.onCasted();
                }

                @Override
                public void onError(String message) {
                    if ("device_offline".equals(message)) {
                        Notify.show(R.string.device_offline);
                    } else {
                        Notify.show(message);
                    }
                }
            });
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        DLNADevice.get().disconnect();
        castController.disconnect();
        EventBus.getDefault().unregister(this);
        DLNACastManager.INSTANCE.unregisterListener(this);
        DLNACastManager.INSTANCE.unbindCastService(App.get());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        scanTask.stop();
    }

    @Override
    public boolean onLongClick(Device item) {
        return false;
    }

    public interface Listener {

        void onCasted();
    }
}