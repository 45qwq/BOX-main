package com.fongmi.android.tv.utils

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.fongmi.android.tv.impl.PermissionCallback
import java.util.function.Consumer

object PermissionUtil {

    @JvmStatic
    fun requestAudio(activity: FragmentActivity, callback: Consumer<Boolean>) {
        com.permissionx.guolindev.PermissionX.init(activity).permissions(android.Manifest.permission.RECORD_AUDIO).request(PermissionCallback(callback))
    }

    @JvmStatic
    fun requestFile(activity: FragmentActivity, callback: Consumer<Boolean>) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            callback.accept(true)
        } else {
            com.permissionx.guolindev.PermissionX.init(activity).permissions(android.Manifest.permission.WRITE_EXTERNAL_STORAGE).request(PermissionCallback(callback))
        }
    }

    @JvmStatic
    fun requestFile(fragment: Fragment, callback: Consumer<Boolean>) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            callback.accept(true)
        } else {
            com.permissionx.guolindev.PermissionX.init(fragment).permissions(android.Manifest.permission.WRITE_EXTERNAL_STORAGE).request(PermissionCallback(callback))
        }
    }
}
