package com.fongmi.android.tv.ui.base;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.DisplayCutout;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.viewbinding.ViewBinding;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.ResUtil;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;

public abstract class BaseActivity extends AppCompatActivity {

    protected ActivityResultLauncher<Intent> pickLauncher;

    protected abstract ViewBinding getBinding();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setNightMode();
        super.onCreate(savedInstanceState);
        pickLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) onPickFile(result.getData().getData());
        });
        if (transparent()) setTransparent(this);
        setContentView(getBinding().getRoot());
        EventBus.getDefault().register(this);
        initView(savedInstanceState);
        setBackCallback();
        initEvent();
        requestNotificationPermission();
    }

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        refreshWall();
    }

    protected Activity getActivity() {
        return this;
    }

    /** 子类覆写以接收文件选择结果（原 REQUEST_PICK_FILE 行为）。默认空实现。 */
    protected void onPickFile(Uri uri) {
    }

    public ActivityResultLauncher<Intent> getPickLauncher() {
        return pickLauncher;
    }

    protected boolean transparent() {
        return true;
    }

    protected boolean customWall() {
        return true;
    }

    protected boolean handleBack() {
        return false;
    }

    protected void initView(Bundle savedInstanceState) {
    }

    protected void initEvent() {
    }

    protected void onBackPress() {
    }

    /**
     * 根据 Setting 配置应用暗色模式。
     * 0 = 跟随系统 MODE_NIGHT_FOLLOW_SYSTEM
     * 1 = 始终暗色 MODE_NIGHT_YES（默认）
     * 2 = 始终亮色 MODE_NIGHT_NO
     */
    private void setNightMode() {
        int mode = Setting.getDarkMode();
        switch (mode) {
            case 0:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
            case 2:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case 1:
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
        }
    }

    protected boolean isVisible(View view) {
        return view.getVisibility() == View.VISIBLE;
    }

    protected boolean isGone(View view) {
        return view.getVisibility() == View.GONE;
    }

    protected void setPadding(ViewGroup layout) {
        setPadding(layout, false);
    }

    protected void setPadding(ViewGroup layout, boolean leftOnly) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        DisplayCutout cutout = ResUtil.getDisplay(this).getCutout();
        if (cutout == null) return;
        int top = cutout.getSafeInsetTop();
        int left = cutout.getSafeInsetLeft();
        int right = cutout.getSafeInsetRight();
        int bottom = cutout.getSafeInsetBottom();
        int padding = left | right | top | bottom;
        layout.setPadding(padding, 0, leftOnly ? 0 : padding, 0);
    }

    protected void noPadding(ViewGroup layout) {
        layout.setPadding(0, 0, 0, 0);
    }

    private void setBackCallback() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(handleBack()) {
            @Override
            public void handleOnBackPressed() {
                onBackPress();
            }
        });
    }

    private void setTransparent(Activity activity) {
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        activity.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        activity.getWindow().setStatusBarColor(Color.TRANSPARENT);
    }

    private void refreshWall() {
        try {
            if (!customWall()) return;
            // 壁纸切换时清除内存缓存，确保新壁纸从磁盘加载
            Glide.get(App.get()).clearMemory();
            App.execute(() -> Glide.get(App.get()).clearDiskCache());
            int wallIndex = Setting.getWall();
            int screenWidth = ResUtil.getScreenWidth();
            int screenHeight = ResUtil.getScreenHeight();
            // 使用 Glide 动态裁剪到屏幕尺寸
            Glide.with(App.get())
                    .asBitmap()
                    .load(wallIndex == 0 ? FileUtil.getWall(0) : ResUtil.getDrawable("wallpaper_" + wallIndex))
                    .override(screenWidth, screenHeight)
                    .transform(new com.bumptech.glide.load.resource.bitmap.CenterCrop())
                    .into(new com.bumptech.glide.request.target.CustomTarget<android.graphics.Bitmap>() {
                        @Override
                        public void onResourceReady(@NonNull android.graphics.Bitmap resource, @Nullable com.bumptech.glide.request.transition.Transition<? super android.graphics.Bitmap> transition) {
                            // 使用 BitmapDrawable 并设置重力为填充
                            android.graphics.drawable.BitmapDrawable drawable = new android.graphics.drawable.BitmapDrawable(getResources(), resource);
                            drawable.setGravity(android.view.Gravity.FILL);
                            getWindow().setBackgroundDrawable(drawable);
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {
                        }

                        @Override
                        public void onLoadFailed(@Nullable Drawable errorDrawable) {
                            super.onLoadFailed(errorDrawable);
                            getWindow().setBackgroundDrawableResource(R.drawable.wallpaper_6);
                        }
                    });
        } catch (Exception e) {
            getWindow().setBackgroundDrawableResource(R.drawable.wallpaper_6);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (event.getType() == RefreshEvent.Type.WALL) refreshWall();
    }

    private static final int REQUEST_NOTIFICATION_PERMISSION = 1001;

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return;
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATION_PERMISSION);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }
}