package com.fongmi.android.tv.ui.activity;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.databinding.ActivityHomeBinding;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.db.BackupManager;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.event.ServerEvent;
import com.fongmi.android.tv.event.StateEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.player.Source;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.FragmentStateManager;
import com.fongmi.android.tv.ui.fragment.SettingFragment;
import com.fongmi.android.tv.ui.fragment.VodFragment;
import com.fongmi.android.tv.utils.Notify;
import com.github.catvod.net.OkHttp;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class HomeActivity extends BaseActivity {

    private FragmentStateManager mManager;
    private ActivityHomeBinding mBinding;
    private int orientation;

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityHomeBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        checkAction(intent);
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        // 检查隐私协议
        if (!Setting.isPrivacyAgreed()) {
            Intent intent = new Intent(this, PrivacyAgreementActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }
        
        // 确保通知渠道已创建（用户已同意协议的情况）
        com.fongmi.android.tv.utils.Notify.createChannel();
        
        orientation = getResources().getConfiguration().orientation;
        // Updater.create().release().start(this); // 移除自动检查更新，只在点击版本号时检查
        // UI 骨架：仅初始化 Fragment 和导航栏，不阻塞主线程
        initFragment(savedInstanceState);
        setNavigation();
        // 加载配置数据（VodConfig.load() 内部已使用 App.execute() 异步加载，无需外层异步包装）
        // 注意：必须同步调用 initConfig()，避免异步导致基类未创建完成就触发 RefreshEvent，导致事件丢失
        initConfig();
    }

    @Override
    protected void initEvent() {
    }

    private void checkAction(Intent intent) {
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            VideoActivity.push(this, intent.getStringExtra(Intent.EXTRA_TEXT));
        } else if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            VideoActivity.push(this, intent.getData().toString());
        }
    }

    private void initFragment(Bundle savedInstanceState) {
        mManager = new FragmentStateManager(mBinding.container, getSupportFragmentManager()) {
            @Override
            public Fragment getItem(int position) {
                if (position == 0) return VodFragment.newInstance();
                if (position == 1) return SettingFragment.newInstance();
                return null;
            }
        };
        if (savedInstanceState == null) mManager.change(0);
    }

    private void initConfig() {
        // WallConfig/VodConfig 的 init() 方法内部会执行 Room 数据库查询 (Config.wall/vod())
        // 必须在后台线程执行，避免主线程数据库访问异常
        App.execute(() -> {
            com.fongmi.android.tv.bean.DefaultConfig.initIfNeeded();
            WallConfig.get().init();
            // 安全恢复：若上次启动发生过崩溃（频繁换源等导致的进程崩溃），
            // 则不自动加载上次的源，避免"崩溃→重启→再崩溃"的死循环。
            // 进入空源/安全态，等用户手动选择源。换源时会清除该标记。
            boolean lastCrash = com.github.catvod.utils.Prefers.getBoolean("crash", false);
            if (lastCrash) {
                com.github.catvod.utils.Prefers.remove("crash");
                App.post(() -> Notify.show("检测到上次异常，已进入安全模式，请重新选择订阅源"));
                RefreshEvent.config();
                StateEvent.empty();
                return;
            }
            VodConfig.get().init().load(getCallback());
        });
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void success(String result) {
                Notify.show(result);
            }

            @Override
            public void success() {
                // 源加载成功：说明此前若发生过崩溃，现已恢复正常，清除崩溃持久化标记
                try {
                    com.github.catvod.utils.Prefers.remove("crash");
                } catch (Exception ignore) {
                }
                checkAction(getIntent());
                RefreshEvent.config();
                RefreshEvent.video();
            }

            @Override
            public void error(String msg) {
                RefreshEvent.config();
                StateEvent.empty();
                Notify.show(msg);
            }
        };
    }

    private void setNavigation() {
    }

    public void change(int position) {
        mManager.change(position);
    }

    @Override
    public void onRefreshEvent(RefreshEvent event) {
        super.onRefreshEvent(event);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onServerEvent(ServerEvent event) {
        if (event.getType() != ServerEvent.Type.PUSH) return;
        VideoActivity.push(this, event.getText());
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        App.post(() -> checkOrientation(newConfig), 100);
    }

    private void checkOrientation(Configuration newConfig) {
        if (orientation != newConfig.orientation) {
            orientation = newConfig.orientation;
            RefreshEvent.video();
        }
    }

    protected boolean handleBack() {
        return true;
    }

    @Override
    protected void onBackPress() {
        if (mManager.isVisible(1)) {
            mManager.change(0);
        } else if (mManager.canBack(0)) {
            showExitConfirm();
        }
    }

    /**
     * 退出应用确认弹窗
     */
    private void showExitConfirm() {
        if (isFinishing() || isDestroyed()) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.exit_confirm_title)
                .setMessage(R.string.exit_confirm_message)
                .setPositiveButton(R.string.exit_confirm_ok, (d, w) -> finish())
                .setNegativeButton(R.string.exit_confirm_cancel, null)
                .setCancelable(true)
                .show();
    }

    @Override
    protected void onDestroy() {
        WallConfig.get().clear();
        VodConfig.get().clear();
        OkHttp.get().clear();
        BackupManager.get().backup();
        Source.get().exit();
        super.onDestroy();
    }
}
