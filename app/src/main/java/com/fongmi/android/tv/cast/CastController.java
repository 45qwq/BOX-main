package com.fongmi.android.tv.cast;

import androidx.annotation.NonNull;

import com.android.cast.dlna.dmc.DLNACastManager;
import com.android.cast.dlna.dmc.control.DeviceControl;
import com.android.cast.dlna.dmc.control.OnDeviceControlListener;
import com.android.cast.dlna.dmc.control.ServiceActionCallback;
import com.fongmi.android.tv.bean.CastVideo;

import kotlin.Unit;

/**
 * DLNA 投屏控制层
 * 负责设备连接、AVTransport、Seek、Play 的生命周期管理
 */
public class CastController implements OnDeviceControlListener {

    private DeviceControl control;
    private CastVideo video;
    private Callback callback;

    public interface Callback {
        void onCasted();
        void onError(String message);
        void onDisconnected();
    }

    public void connect(@NonNull org.fourthline.cling.model.meta.Device<?, ?, ?> device, CastVideo video, Callback callback) {
        this.video = video;
        this.callback = callback;
        this.control = DLNACastManager.INSTANCE.connectDevice(device, this);
    }

    public void disconnect() {
        control = null;
    }

    @Override
    public void onConnected(@NonNull org.fourthline.cling.model.meta.Device<?, ?, ?> device) {
        if (control == null || video == null) return;
        control.setAVTransportURI(video.getUrl(), video.getName(), new ServiceActionCallback<Unit>() {
            @Override
            public void onSuccess(Unit unit) {
                doSeekAndPlay();
            }

            @Override
            public void onFailure(String s) {
                if (callback != null) callback.onError(s);
            }
        });
    }

    @Override
    public void onDisconnected(@NonNull org.fourthline.cling.model.meta.Device<?, ?, ?> device) {
        if (callback != null) callback.onDisconnected();
    }

    private void doSeekAndPlay() {
        if (video != null && video.getPosition() > 0) {
            control.seek(video.getPosition(), new ServiceActionCallback<Unit>() {
                @Override
                public void onSuccess(Unit unit) {
                    doPlay();
                }

                @Override
                public void onFailure(String s) {
                    // seek 失败，继续尝试播放
                    doPlay();
                }
            });
        } else {
            doPlay();
        }
    }

    private void doPlay() {
        control.play("1", new ServiceActionCallback<Unit>() {
            @Override
            public void onSuccess(Unit unit) {
                if (callback != null) callback.onCasted();
            }

            @Override
            public void onFailure(String s) {
                if (callback != null) callback.onError(s);
            }
        });
    }

    @Override
    public void onAvTransportStateChanged(@NonNull org.fourthline.cling.support.model.TransportState state) {
    }

    @Override
    public void onEventChanged(@NonNull org.fourthline.cling.support.lastchange.EventedValue<?> event) {
    }

    @Override
    public void onRendererVolumeChanged(int volume) {
    }

    @Override
    public void onRendererVolumeMuteChanged(boolean mute) {
    }
}