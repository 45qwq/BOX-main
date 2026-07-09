package com.fongmi.android.tv.ui.fragment;
import com.github.catvod.utils.Logger;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.Updater;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.FragmentSettingBinding;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.impl.ConfigCallback;
import com.fongmi.android.tv.impl.ProxyCallback;
import com.fongmi.android.tv.impl.SiteCallback;
import com.fongmi.android.tv.player.Source;
import com.fongmi.android.tv.ui.activity.CollectActivity;
import com.fongmi.android.tv.ui.activity.DownloadActivity;
import com.fongmi.android.tv.ui.activity.HistoryActivity;
import com.fongmi.android.tv.ui.activity.HomeActivity;
import com.fongmi.android.tv.ui.activity.SettingPlayerActivity;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.dialog.AboutDialog;
import com.fongmi.android.tv.ui.dialog.ConfigDialog;
import com.fongmi.android.tv.ui.dialog.HistoryDialog;
import com.fongmi.android.tv.ui.dialog.ProxyDialog;
import com.fongmi.android.tv.ui.dialog.RestoreDialog;
import com.fongmi.android.tv.ui.dialog.SiteDialog;
import com.fongmi.android.tv.ui.dialog.WallDialog;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.player.exo.CacheManager;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.bean.Doh;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.permissionx.guolindev.PermissionX;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

public class SettingFragment extends BaseFragment implements ConfigCallback, SiteCallback, ProxyCallback {

    private FragmentSettingBinding mBinding;
    private String[] size;
    private int type;

    public static SettingFragment newInstance() {
        return new SettingFragment();
    }

    private String getSwitch(boolean value) {
        return getString(value ? R.string.setting_on : R.string.setting_off);
    }

    private String getProxy(String proxy) {
        return proxy.isEmpty() ? getString(R.string.none) : UrlUtil.scheme(proxy);
    }

    private int getDohIndex() {
        return Math.max(0, VodConfig.get().getDoh().indexOf(Doh.objectFrom(Setting.getDoh())));
    }

    private String[] getDohList() {
        List<String> list = new ArrayList<>();
        for (Doh item : VodConfig.get().getDoh()) list.add(item.getName());
        return list.toArray(new String[0]);
    }

    private HomeActivity getRoot() {
        return (HomeActivity) getActivity();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentSettingBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        setSourceHintText(mBinding.vodUrl, VodConfig.getDesc(), R.string.source_hint_setting);
        mBinding.versionText.setText(getString(R.string.setting_version) + " " + BuildConfig.VERSION_NAME);

        // 延迟初始化缓存大小显示，非首屏必需
        mBinding.getRoot().postDelayed(this::setCacheText, 500);
        String[] quotes = getResources().getStringArray(R.array.motivational_quotes);
        int randomIndex = new java.util.Random().nextInt(quotes.length);
        mBinding.marquee.setText(quotes[randomIndex]);
        setOtherText();
    }

    private void setOtherText() {
        mBinding.dohText.setText(getDohList()[getDohIndex()]);
        mBinding.proxyText.setText(getProxy(Setting.getProxy()));
        mBinding.incognitoSwitch.setChecked(Setting.isIncognito());
        mBinding.sizeText.setText((size = ResUtil.getStringArray(R.array.select_size))[Setting.getSize()]);
        mBinding.wallText.setText(getWallText());
        mBinding.downloadConcurrentText.setText(getString(R.string.download_concurrent_hint, Setting.getDownloadConcurrent()));
    }

    private String getWallText() {
        int wallIndex = Setting.getWall();
        if (wallIndex == 0) return "本地图片";
        return "内置壁纸 " + wallIndex;
    }

    private void setCacheText() {
        FileUtil.getCacheSize(new Callback() {
            @Override
            public void success(String result) {
                long exoSize = CacheManager.get().getCacheSize();
                String exoText = exoSize > 0 ? FileUtil.byteCountToDisplaySize(exoSize) : "0 B";
                mBinding.cacheText.setText(getString(R.string.cache_size, result, exoText));
            }
        });
    }

    @Override
    protected void initEvent() {
        mBinding.vod.setOnClickListener(this::onVod);
        mBinding.proxy.setOnClickListener(this::onProxy);
        mBinding.cache.setOnClickListener(this::onCache);
        mBinding.webdav.setOnClickListener(this::onWebDAV);
        mBinding.backup.setOnClickListener(this::onBackup);
        mBinding.player.setOnClickListener(this::onPlayer);
        mBinding.restore.setOnClickListener(this::onRestore);
        mBinding.version.setOnClickListener(this::onVersion);
        mBinding.about.setOnClickListener(this::onAbout);
        mBinding.vod.setOnLongClickListener(this::onVodEdit);
        mBinding.vodHome.setOnClickListener(this::onVodHome);
        mBinding.vodHistory.setOnClickListener(this::onVodHistory);
        mBinding.version.setOnLongClickListener(this::onVersionDev);
        mBinding.incognitoSwitch.setOnClickListener(this::setIncognito);
        mBinding.size.setOnClickListener(this::setSize);
        mBinding.wall.setOnClickListener(this::onWall);
        mBinding.doh.setOnClickListener(this::setDoh);
        mBinding.downloadManagerLayout.setOnClickListener(this::onDownloadMgr);
        mBinding.downloadPathLayout.setOnClickListener(this::onDownloadPath);
        mBinding.downloadConcurrentLayout.setOnClickListener(this::onDownloadConcurrent);
        mBinding.settingBack.setOnClickListener(this::onSettingBack);
        mBinding.collectLayout.setOnClickListener(this::onCollect);
        mBinding.historyLayout.setOnClickListener(this::onHistory);
    }

    @Override
    public void setConfig(Config config) {
        if (getActivity() == null || !isAdded() || isDetached()) return;
        if (config == null || config.isEmpty()) return;

        try {
            if (config.getUrl().startsWith("file") && !PermissionX.isGranted(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                PermissionX.init(this).permissions(Manifest.permission.WRITE_EXTERNAL_STORAGE).request((allGranted, grantedList, deniedList) -> {
                    if (getActivity() != null && isAdded()) {
                        load(config);
                    }
                });
            } else {
                load(config);
            }
        } catch (Exception e) {
            Logger.e("Error", e);
        }
    }

    private void load(Config config) {
        if (getActivity() == null || !isAdded() || isDetached()) return;

        try {
            switch (config.getType()) {
                case 0:
                    Notify.progress(getActivity());
                    VodConfig.load(config, getCallback(0));
                    if (mBinding != null && mBinding.vodUrl != null) {
                        mBinding.vodUrl.setText(config.getDesc());
                    }
                    break;
                case 2:
                    Notify.progress(getActivity());
                    WallConfig.load(config, getCallback(2));
                    break;
            }
        } catch (Exception e) {
            Logger.e("Error", e);
            Notify.dismiss();
        }
    }

    private Callback getCallback(int type) {
        return new Callback() {
            @Override
            public void success(String result) {
                if (getActivity() == null || !isAdded()) return;
                Notify.show(result);
            }

            @Override
            public void success() {
                if (getActivity() == null || !isAdded()) return;
                setConfig(type);
            }

            @Override
            public void error(String msg) {
                if (getActivity() == null || !isAdded()) return;
                Notify.show(msg);
                Notify.dismiss();
                if (type == 0) {
                    setSourceHintText(mBinding.vodUrl, VodConfig.getDesc(), R.string.source_hint_setting);
                }
            }
        };
    }

    private void setConfig(int type) {
        switch (type) {
            case 0:
                setCacheText();
                Notify.dismiss();
                RefreshEvent.video();
                RefreshEvent.config();
                setSourceHintText(mBinding.vodUrl, VodConfig.getDesc(), R.string.source_hint_setting);
                break;
            case 2:
                setCacheText();
                Notify.dismiss();
                break;
        }
    }

    private void setSourceHintText(TextView textView, String desc, int hintStringRes) {
        if (TextUtils.isEmpty(desc)) {
            SpannableString spannable = new SpannableString(getString(hintStringRes));
            spannable.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.white)), 0, spannable.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannable.setSpan(new RelativeSizeSpan(0.8f), 0, spannable.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            int alpha = (int)(255 * 0.5f);
            spannable.setSpan(new ForegroundColorSpan(android.graphics.Color.argb(alpha, 255, 255, 255)), 0, spannable.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            textView.setText(spannable);
        } else {
            textView.setText(desc);
        }
    }

    @Override
    public void setSite(Site item) {
        VodConfig.get().setHome(item);
        RefreshEvent.video();
    }

    @Override
    public void onChanged() {
    }

    private void onVod(View view) {
        ConfigDialog.create(this).type(type = 0).show();
    }

    private void onSettingBack(View view) {
        HomeActivity activity = (HomeActivity) getActivity();
        if (activity != null) activity.change(0);
    }

    private void onCollect(View view) {
        CollectActivity.start(getActivity());
    }

    private void onHistory(View view) {
        HistoryActivity.start(getActivity());
    }

    private void onWall(View view) {
        WallDialog.create(this).show();
    }

    private boolean onVodEdit(View view) {
        ConfigDialog.create(this).type(type = 0).edit().show();
        return true;
    }

    private boolean onWallEdit(View view) {
        ConfigDialog.create(this).type(type = 2).edit().show();
        return true;
    }

    private void onVodHome(View view) {
        SiteDialog.create(this).all().show();
    }

    private void onVodHistory(View view) {
        HistoryDialog.create(this).type(type = 0).show();
    }

    private void onPlayer(View view) {
        SettingPlayerActivity.start(requireActivity());
    }

    private void onVersion(View view) {
        Updater.create().force().release().start(getActivity());
    }

    private void onAbout(View view) {
        AboutDialog.show(this);
    }

    private boolean onVersionDev(View view) {
        Updater.create().force().dev().start(getActivity());
        return true;
    }

    private void setWallDefault(View view) {
        WallConfig.refresh(Setting.getWall() == 4 ? 1 : Setting.getWall() + 1);
    }

    private void setWallRefresh(View view) {
        Notify.progress(getActivity());
        WallConfig.get().load(new Callback() {
            @Override
            public void success() {
                Notify.dismiss();
                setCacheText();
            }
        });
    }

    private void setIncognito(View view) {
        boolean isChecked = !Setting.isIncognito();
        Setting.putIncognito(isChecked);
    }

    private void setSize(View view) {
        new MaterialAlertDialogBuilder(getActivity()).setTitle(R.string.setting_size).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(size, Setting.getSize(), (dialog, which) -> {
            mBinding.sizeText.setText(size[which]);
            Setting.putSize(which);
            RefreshEvent.size();
            dialog.dismiss();
        }).show();
    }

    private void setDoh(View view) {
        new MaterialAlertDialogBuilder(getActivity()).setTitle(R.string.setting_doh).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(getDohList(), getDohIndex(), (dialog, which) -> {
            setDoh(VodConfig.get().getDoh().get(which));
            dialog.dismiss();
        }).show();
    }

    private void setDoh(Doh doh) {
        Source.get().stop();
        OkHttp.get().setDoh(doh);
        Notify.progress(getActivity());
        Setting.putDoh(doh.toString());
        mBinding.dohText.setText(doh.getName());
        App.execute(() -> {
            Config config = Config.vod();
            App.post(() -> VodConfig.load(config, getCallback(0)));
        });
    }

    private void onProxy(View view) {
        ProxyDialog.create(this).show();
    }

    @Override
    public void setProxy(String proxy) {
        Source.get().stop();
        Setting.putProxy(proxy);
        OkHttp.selector().clear();
        OkHttp.get().setProxy(proxy);
        Notify.progress(getActivity());
        mBinding.proxyText.setText(getProxy(proxy));
        App.execute(() -> {
            Config config = Config.vod();
            App.post(() -> VodConfig.load(config, getCallback(0)));
        });
    }

    private void onCache(View view) {
        App.execute(() -> {
            CacheManager.get().clearCache();
            Path.clear(Path.cache());
            App.post(() -> setCacheText());
        });
    }

    private void onWebDAV(View view) {
        com.fongmi.android.tv.ui.dialog.WebDAVDialog.create(this).show();
    }

    private void onBackup(View view) {
        PermissionX.init(this).permissions(Manifest.permission.WRITE_EXTERNAL_STORAGE).request((allGranted, grantedList, deniedList) -> AppDatabase.backup(new Callback() {
            @Override
            public void success() {
                Notify.show(R.string.backup_success);
            }

            @Override
            public void error() {
                Notify.show(R.string.backup_fail);
            }
        }));
    }

    private void onRestore(View view) {
        PermissionX.init(this).permissions(Manifest.permission.WRITE_EXTERNAL_STORAGE).request((allGranted, grantedList, deniedList) -> RestoreDialog.create().show(getActivity(), new Callback() {
            @Override
            public void success() {
                Notify.show(R.string.restore_success);
                Notify.progress(getActivity());
                setOtherText();
                initConfig();
            }

            @Override
            public void error() {
                Notify.show(R.string.restore_fail);
            }
        }));
    }

    private void initConfig() {
        WallConfig.get().init();
        VodConfig.get().init().load(getCallback(0));
    }

    @Override
    public void onResume() {
        super.onResume();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        EventBus.getDefault().unregister(this);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (event.getType() == RefreshEvent.Type.WALL) {
            if (mBinding != null) mBinding.wallText.setText(getWallText());
        } else if (event.getType() == RefreshEvent.Type.CONFIG) {
        }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (hidden) return;
        setSourceHintText(mBinding.vodUrl, VodConfig.getDesc(), R.string.source_hint_setting);
        setCacheText();
    }

    private void onDownloadMgr(View view) {
        if (!hasDownloadPermission()) {
            requestDownloadPermission();
            return;
        }
        DownloadActivity.start(getActivity());
    }

    private void onDownloadPath(View view) {
        if (!hasDownloadPermission()) {
            requestDownloadPermission();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_DOWNLOAD_PATH);
    }

    private void onDownloadConcurrent(View view) {
        int current = Setting.getDownloadConcurrent();
        String[] items = new String[]{"1", "2", "3", "4", "5"};
        new MaterialAlertDialogBuilder(getActivity())
                .setTitle(R.string.download_concurrent_limit)
                .setSingleChoiceItems(items, current - 1, (dialog, which) -> {
                    int value = which + 1;
                    Setting.putDownloadConcurrent(value);
                    mBinding.downloadConcurrentText.setText(getString(R.string.download_concurrent_hint, value));
                    try {
                        android.content.Intent intent = new android.content.Intent(getActivity(), com.fongmi.android.tv.service.DownloadService.class);
                        intent.setAction("RECONFIGURE_THREAD_POOL");
                        getActivity().startService(intent);
                    } catch (Exception e) {
                        com.github.catvod.utils.Logger.e("Failed to reconfigure thread pool", e);
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.dialog_negative, null)
                .show();
    }

    private static final int REQUEST_DOWNLOAD_PATH = 10001;

    private boolean hasDownloadPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            return ContextCompat.checkSelfPermission(getContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestDownloadPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            new MaterialAlertDialogBuilder(getActivity())
                    .setTitle("存储权限申请")
                    .setMessage("需要开启「所有文件访问权限」才能管理下载文件")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("去设置", (dialog, which) -> {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                        intent.setData(Uri.parse("package:" + getActivity().getPackageName()));
                        startActivity(intent);
                    })
                    .show();
        } else {
            PermissionX.init(this)
                    .permissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    .onExplainRequestReason((scope, deniedList) -> scope.showRequestReasonDialog(deniedList, "需要存储权限以管理下载文件", "确定"))
                    .request((allGranted, grantedList, deniedList) -> {
                        if (allGranted) {
                            DownloadActivity.start(getActivity());
                        }
                    });
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == WallDialog.REQUEST_PICK_WALLPAPER) {
            WallDialog.handleActivityResult(requestCode, resultCode, data, getActivity());
            mBinding.getRoot().postDelayed(() -> mBinding.wallText.setText(getWallText()), 1500);
            return;
        }
        if (resultCode != Activity.RESULT_OK || requestCode != FileChooser.REQUEST_PICK_FILE) {
            if (requestCode == REQUEST_DOWNLOAD_PATH && resultCode == Activity.RESULT_OK && data != null) {
                Uri treeUri = data.getData();
                getActivity().getContentResolver().takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                Setting.putDownloadPath(treeUri.toString());
                if (mBinding.downloadPathText != null) {
                    mBinding.downloadPathText.setText(treeUri.getPath());
                }
                com.fongmi.android.tv.utils.Notify.show("下载路径已设置");
            }
            return;
        }
        App.execute(() -> {
            Config config = Config.find("file:/" + FileChooser.getPathFromUri(getContext(), data.getData()).replace(Path.rootPath(), ""), type);
            App.post(() -> setConfig(config));
        });
    }
}
