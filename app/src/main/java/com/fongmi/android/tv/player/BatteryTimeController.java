package com.fongmi.android.tv.player;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.R;

public class BatteryTimeController {

    private final FragmentActivity mActivity;
    private final Handler mHandler;
    private final Runnable mTimeUpdateRunnable;
    private final BroadcastReceiver mBatteryReceiver;
    private final BroadcastReceiver mScreenReceiver;
    private final Runnable mOnScreenOff;
    private final Runnable mOnScreenOn;
    private int mBatteryLevel = -1;
    private boolean mIsCharging = false;

    public BatteryTimeController(FragmentActivity activity, Runnable onScreenOff, Runnable onScreenOn) {
        this.mActivity = activity;
        this.mHandler = new Handler(Looper.getMainLooper());
        this.mOnScreenOff = onScreenOff;
        this.mOnScreenOn = onScreenOn;

        mBatteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                    int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                    int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                    if (level != -1 && scale != -1) {
                        mBatteryLevel = (int) ((level / (float) scale) * 100);
                        mIsCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL);
                        updateTimeBattery();
                    }
                }
            }
        };

        mScreenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null || intent.getAction() == null) return;
                if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                    if (mOnScreenOff != null) mOnScreenOff.run();
                } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                    if (mOnScreenOn != null) mOnScreenOn.run();
                }
            }
        };

        mTimeUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                updateTimeBattery();
                mHandler.postDelayed(this, 30000);
            }
        };
    }

    public void updateTimeBattery() {
        boolean isFullscreen = false;
        View root = mActivity.findViewById(android.R.id.content);
        if (root != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            isFullscreen = (root.getSystemUiVisibility() & View.SYSTEM_UI_FLAG_FULLSCREEN) != 0;
        }

        TextView timeBattery = mActivity.findViewById(R.id.time_battery);
        TextView batteryText = mActivity.findViewById(R.id.battery_icon);
        ImageView chargingIndicator = mActivity.findViewById(R.id.charging_indicator);

        if (isFullscreen) {
            if (timeBattery != null) {
                String time = DateFormat.getTimeFormat(mActivity).format(System.currentTimeMillis());
                timeBattery.setText(time);
                timeBattery.setVisibility(View.VISIBLE);
            }
            if (chargingIndicator != null) {
                chargingIndicator.setVisibility(mIsCharging && mBatteryLevel >= 0 ? View.VISIBLE : View.GONE);
            }
            if (batteryText != null && mBatteryLevel >= 0) {
                batteryText.setText(mBatteryLevel + "%");
                batteryText.setVisibility(View.VISIBLE);
            } else if (batteryText != null) {
                batteryText.setVisibility(View.GONE);
            }
        } else {
            if (timeBattery != null) timeBattery.setVisibility(View.GONE);
            if (batteryText != null) batteryText.setVisibility(View.GONE);
            if (chargingIndicator != null) chargingIndicator.setVisibility(View.GONE);
        }
    }

    public void startUpdates() {
        try {
            mActivity.registerReceiver(mBatteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        } catch (Exception e) {
            // ignore
        }
        IntentFilter screenFilter = new IntentFilter();
        screenFilter.addAction(Intent.ACTION_SCREEN_ON);
        screenFilter.addAction(Intent.ACTION_SCREEN_OFF);
        try {
            mActivity.registerReceiver(mScreenReceiver, screenFilter);
        } catch (Exception e) {
            // ignore
        }
        updateTimeBattery();
        mHandler.post(mTimeUpdateRunnable);
    }

    public void stopUpdates() {
        try {
            if (mBatteryReceiver != null) mActivity.unregisterReceiver(mBatteryReceiver);
        } catch (IllegalArgumentException e) {
            // ignore
        }
        try {
            if (mScreenReceiver != null) mActivity.unregisterReceiver(mScreenReceiver);
        } catch (IllegalArgumentException e) {
            // ignore
        }
        mHandler.removeCallbacks(mTimeUpdateRunnable);
    }

    public void onConfigurationChanged() {
        updateTimeBattery();
    }
}
