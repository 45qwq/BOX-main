package com.fongmi.android.tv.cast;

import androidx.annotation.NonNull;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Device;
import com.github.catvod.net.OkHttp;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Response;

/**
 * 非 DLNA 设备投屏的 HTTP 请求层
 * 负责构建 FormBody、发送 HTTP 请求、解析响应
 */
public class CastHttpClient implements okhttp3.Callback {

    private final OkHttpClient client;
    private Callback callback;

    public interface Callback {
        void onCasted();
        void onError(String message);
    }

    public CastHttpClient() {
        this.client = OkHttp.client(Constant.TIMEOUT_SYNC);
    }

    public void send(Device item, Config config, String historyStr, Callback callback) {
        this.callback = callback;
        App.execute(() -> {
            FormBody.Builder formBody = new FormBody.Builder()
                    .add("device", Device.get().toString())
                    .add("config", config.toString());
            if (historyStr != null) {
                formBody.add("history", historyStr);
            }
            FormBody requestBody = formBody.build();
            App.post(() -> OkHttp.newCall(client, item.getIp().concat("/action?do=cast"), requestBody).enqueue(this));
        });
    }

    @Override
    public void onFailure(@NonNull Call call, @NonNull IOException e) {
        App.post(() -> {
            if (callback != null) callback.onError(e.getMessage());
        });
    }

    @Override
    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
        String body = response.body().string();
        App.post(() -> {
            if (callback == null) return;
            if ("OK".equals(body)) {
                callback.onCasted();
            } else {
                callback.onError("device_offline");
            }
        });
    }
}