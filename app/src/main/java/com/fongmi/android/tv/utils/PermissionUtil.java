package com.fongmi.android.tv.utils;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.impl.PermissionCallback;

import java.util.function.Consumer;

public class PermissionUtil {

    public static void requestAudio(FragmentActivity activity, Consumer<Boolean> callback) {
        com.permissionx.guolindev.PermissionX.init(activity).permissions(android.Manifest.permission.RECORD_AUDIO).request(new PermissionCallback(callback));
    }

    public static void requestFile(FragmentActivity activity, Consumer<Boolean> callback) {
        // Android 11+ 已不再需要 WRITE_EXTERNAL_STORAGE，app-specific 目录可直接读写
        // 保留此方法以兼容旧版 Android 10 及以下
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            callback.accept(true);
        } else {
            com.permissionx.guolindev.PermissionX.init(activity).permissions(android.Manifest.permission.WRITE_EXTERNAL_STORAGE).request(new PermissionCallback(callback));
        }
    }

    public static void requestFile(Fragment fragment, Consumer<Boolean> callback) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            callback.accept(true);
        } else {
            com.permissionx.guolindev.PermissionX.init(fragment).permissions(android.Manifest.permission.WRITE_EXTERNAL_STORAGE).request(new PermissionCallback(callback));
        }
    }
}