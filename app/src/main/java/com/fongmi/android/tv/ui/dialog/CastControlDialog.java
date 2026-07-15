package com.fongmi.android.tv.ui.dialog;

import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogCastControlBinding;
import com.fongmi.android.tv.event.CastEvent;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.UpnpCastManager;

import org.fourthline.cling.model.meta.Device;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class CastControlDialog extends BaseDialog {

    private DialogCastControlBinding binding;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isPlaying = false;
    private boolean isMuted = false;
    private boolean seeking = false;

    public static CastControlDialog create() {
        return new CastControlDialog();
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) {
            if (f instanceof CastControlDialog) return;
        }
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogCastControlBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        Device<?, ?, ?> device = UpnpCastManager.getSelectedDevice();
        if (device == null) {
            dismiss();
            return;
        }
        binding.tvDeviceName.setText(device.getDetails().getFriendlyName());
        updatePlayButton();
        // 启动状态轮询
        UpnpCastManager.startPolling();
    }

    @Override
    protected void initEvent() {
        binding.btnPlayPause.setOnClickListener(v -> {
            if (isPlaying) UpnpCastManager.pause();
            else UpnpCastManager.play();
        });
        binding.btnStop.setOnClickListener(v -> UpnpCastManager.stop());
        binding.btnMute.setOnClickListener(v -> UpnpCastManager.setMute(!isMuted));
        binding.seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && seeking) {
                    // 根据进度比例和总时长计算当前时间显示
                    long totalMillis = parseTime(binding.tvTotal.getText().toString());
                    if (totalMillis > 0) {
                        long currentMillis = (long) ((float) progress / seekBar.getMax() * totalMillis);
                        binding.tvCurrent.setText(formatTime(currentMillis));
                    }
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                seeking = true;
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                seeking = false;
                long totalMillis = parseTime(binding.tvTotal.getText().toString());
                if (totalMillis > 0) {
                    long targetMillis = (long) ((float) seekBar.getProgress() / seekBar.getMax() * totalMillis);
                    UpnpCastManager.seekMillis(targetMillis);
                }
            }
        });
        binding.volumeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) UpnpCastManager.setVolume(progress);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        UpnpCastManager.stopPolling();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onCastEvent(CastEvent event) {
        if (event instanceof CastEvent.StateChanged) {
            CastEvent.StateChanged sc = (CastEvent.StateChanged) event;
            isPlaying = sc.getState() == CastEvent.TransportState.PLAYING;
            updatePlayButton();
            updateStateText(sc.getState());
        } else if (event instanceof CastEvent.PositionChanged) {
            CastEvent.PositionChanged pc = (CastEvent.PositionChanged) event;
            if (!seeking) {
                binding.tvCurrent.setText(formatTime(pc.getCurrent()));
                binding.tvTotal.setText(formatTime(pc.getTotal()));
                if (pc.getTotal() > 0) {
                    int progress = (int) ((float) pc.getCurrent() / pc.getTotal() * 1000);
                    binding.seekBar.setProgress(progress);
                }
            }
        } else if (event instanceof CastEvent.VolumeChanged) {
            CastEvent.VolumeChanged vc = (CastEvent.VolumeChanged) event;
            if (vc.getVolume() >= 0) binding.volumeBar.setProgress(vc.getVolume());
            isMuted = vc.isMuted();
            updateMuteButton();
        } else if (event instanceof CastEvent.Error) {
            Notify.show(((CastEvent.Error) event).getMessage());
        }
    }

    private void updatePlayButton() {
        binding.btnPlayPause.setImageResource(isPlaying ? androidx.media3.ui.R.drawable.exo_icon_pause : androidx.media3.ui.R.drawable.exo_icon_play);
    }

    private void updateMuteButton() {
        binding.btnMute.setImageResource(isMuted ? R.drawable.ic_widget_volume_off : R.drawable.ic_widget_volume_high);
    }

    private void updateStateText(CastEvent.TransportState state) {
        int resId;
        switch (state) {
            case PLAYING: resId = R.string.cast_playing; break;
            case PAUSED: resId = R.string.cast_paused; break;
            case TRANSITIONING: resId = R.string.cast_loading; break;
            default: resId = R.string.cast_stopped; break;
        }
        binding.tvState.setText(resId);
    }

    private String formatTime(long millis) {
        long totalSeconds = millis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%02d:%02d", minutes, seconds);
    }

    private long parseTime(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) return 0;
        String[] parts = timeStr.split(":");
        if (parts.length == 2) {
            try { return (Long.parseLong(parts[0]) * 60 + Long.parseLong(parts[1])) * 1000; }
            catch (NumberFormatException e) { return 0; }
        } else if (parts.length == 3) {
            try { return (Long.parseLong(parts[0]) * 3600 + Long.parseLong(parts[1]) * 60 + Long.parseLong(parts[2])) * 1000; }
            catch (NumberFormatException e) { return 0; }
        }
        return 0;
    }
}
