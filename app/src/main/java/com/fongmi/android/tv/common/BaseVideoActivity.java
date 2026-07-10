package com.fongmi.android.tv.common;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.CastMember;
import com.fongmi.android.tv.bean.CastVideo;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.bean.Url;
import com.fongmi.android.tv.databinding.ActivityVideoBinding;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.ActionEvent;
import com.fongmi.android.tv.event.CastEvent;
import com.fongmi.android.tv.event.ErrorEvent;
import com.fongmi.android.tv.event.PlayerEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.player.PlayerController;
import com.fongmi.android.tv.player.GestureController;
import com.fongmi.android.tv.player.ControlPanelManager;
import com.fongmi.android.tv.player.EpisodeManager;
import com.fongmi.android.tv.player.Players;
import com.fongmi.android.tv.player.exo.ExoUtil;
import com.fongmi.android.tv.player.Source;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.ui.adapter.EpisodeAdapter;
import com.fongmi.android.tv.ui.adapter.FlagAdapter;
import com.fongmi.android.tv.ui.adapter.ParseAdapter;
import com.fongmi.android.tv.ui.adapter.QualityAdapter;
import com.fongmi.android.tv.ui.adapter.QuickAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.base.ViewType;
import com.fongmi.android.tv.ui.custom.CustomKeyDownVod;
import com.fongmi.android.tv.ui.custom.CustomMovement;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.ui.dialog.CastDialog;
import com.fongmi.android.tv.ui.dialog.ControlDialog;
import com.fongmi.android.tv.ui.dialog.DanmakuDialog;
import com.fongmi.android.tv.ui.dialog.DownloadEpisodeDialog;
import com.fongmi.android.tv.ui.dialog.EpisodeGridDialog;
import com.fongmi.android.tv.ui.dialog.EpisodeListDialog;
import com.fongmi.android.tv.ui.dialog.InfoDialog;
import com.fongmi.android.tv.ui.dialog.ReceiveDialog;
import com.fongmi.android.tv.ui.dialog.SubtitleDialog;
import com.fongmi.android.tv.ui.dialog.TrackDialog;
import com.fongmi.android.tv.utils.CastUtil;
import com.fongmi.android.tv.utils.Clock;
import com.fongmi.android.tv.utils.Downloader;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PiP;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Sniffer;
import com.fongmi.android.tv.utils.Timer;
import com.fongmi.android.tv.utils.Traffic;
import com.fongmi.android.tv.utils.Util;
import com.fongmi.android.tv.ui.activity.CastWorksActivity;
import com.fongmi.android.tv.ui.activity.DownloadActivity;
import com.fongmi.android.tv.ui.activity.FolderActivity;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.github.bassaer.library.MDColor;
import com.github.catvod.utils.Logger;
import com.github.catvod.utils.Trans;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.permissionx.guolindev.PermissionX;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;

public abstract class BaseVideoActivity extends BaseActivity implements Clock.Callback, CustomKeyDownVod.Listener, ControlDialog.Listener, TrackDialog.Listener, FlagAdapter.OnClickListener, EpisodeAdapter.OnClickListener, QualityAdapter.OnClickListener, QuickAdapter.OnClickListener, ParseAdapter.OnClickListener, CastDialog.Listener, InfoDialog.Listener {

    protected ActivityVideoBinding mBinding;
    protected ViewGroup.LayoutParams mFrameParams;

    private Observer<Result> mObserveDetail;
    private Observer<Result> mObservePlayer;
    private Observer<Result> mObserveSearch;
    private EpisodeAdapter mEpisodeAdapter;
    private QualityAdapter mQualityAdapter;
    private ControlDialog mControlDialog;
    private QuickAdapter mQuickAdapter;
    private ParseAdapter mParseAdapter;
    private CustomKeyDownVod mKeyDown;
    private ExecutorService mExecutor;
    private SiteViewModel mViewModel;
    private FlagAdapter mFlagAdapter;
    private List<String> mBroken;
    private History mHistory;
    private Players mPlayers;
    private PlayerController mPlayerController;
    private GestureController mGestureController;
    private ControlPanelManager mControlPanel;
    private EpisodeManager mEpisodeMgr;
    private Vod mCurrentVod;
    private boolean initAuto;
    private boolean autoMode;
    private boolean useParse;
    private boolean redirect;
    private boolean stop;
    private Runnable mR1;
    private Runnable mR2;
    private Runnable mR3;
    private Runnable mR4;
    private Clock mClock;
    private String tag;
    private PiP mPiP;
    private Handler mHandler;
    private Runnable mTimeUpdateRunnable;
    private BroadcastReceiver mBatteryReceiver;
    private BroadcastReceiver mScreenReceiver;
    private int mBatteryLevel = -1;
    private boolean mIsCharging = false;
    private boolean mPausedByScreen = false;
    private AudioManager mAudioManager;
    private boolean mActorExpanded = false;

    // ==================== Public static methods ====================

    public static void push(FragmentActivity activity, String text) {
        if (FileChooser.isValid(activity, Uri.parse(text))) file(activity, FileChooser.getPathFromUri(activity, Uri.parse(text)));
        else start(activity, Sniffer.getUrl(text));
    }

    public static void file(FragmentActivity activity, String path) {
        if (TextUtils.isEmpty(path)) return;
        String name = new File(path).getName();
        PermissionX.init(activity).permissions(Manifest.permission.WRITE_EXTERNAL_STORAGE).request((allGranted, grantedList, deniedList) -> start(activity, "push_agent", "file://" + path, name));
    }

    public static void cast(Activity activity, History history) {
        start(activity, history.getSiteKey(), history.getVodId(), history.getVodName(), history.getVodPic());
    }

    public static void collect(Activity activity, String key, String id, String name, String pic) {
        start(activity, key, id, name, pic, null, true);
    }

    public static void start(Activity activity, String url) {
        start(activity, "push_agent", url, url);
    }

    public static void start(Activity activity, String key, String id, String name) {
        start(activity, key, id, name, null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic) {
        start(activity, key, id, name, pic, null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark) {
        start(activity, key, id, name, pic, mark, false);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect) {
        Intent intent = new Intent(activity, VideoActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("collect", collect);
        intent.putExtra("mark", mark);
        intent.putExtra("name", name);
        intent.putExtra("pic", pic);
        intent.putExtra("key", key);
        intent.putExtra("id", id);
        activity.startActivity(intent);
    }

    // ==================== Intent helpers ====================

    private String getName() {
        return Objects.toString(getIntent().getStringExtra("name"), "");
    }

    private String getPic() {
        return Objects.toString(getIntent().getStringExtra("pic"), "");
    }

    private String getMark() {
        return Objects.toString(getIntent().getStringExtra("mark"), "");
    }

    private String getKey() {
        return Objects.toString(getIntent().getStringExtra("key"), "");
    }

    private String getId() {
        return Objects.toString(getIntent().getStringExtra("id"), "");
    }

    private String getHistoryKey() {
        return getKey().concat(AppDatabase.SYMBOL).concat(getId()).concat(AppDatabase.SYMBOL) + VodConfig.getCid();
    }

    private Site getSite() {
        return VodConfig.get().getSite(getKey());
    }

    private Flag getFlag() {
        return mFlagAdapter.getActivated();
    }

    private Episode getEpisode() {
        return mEpisodeAdapter.getActivated();
    }

    private int getScale() {
        return mHistory != null && mHistory.getScale() != -1 ? mHistory.getScale() : Setting.getScale();
    }

    private boolean isReplay() {
        return Setting.getReset() == 1;
    }

    private boolean isFromCollect() {
        return getIntent().getBooleanExtra("collect", false);
    }

    private boolean isAutoRotate() {
        return Settings.System.getInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0) == 1;
    }

    private boolean isLand() {
        return mBinding.getRoot().getTag().equals("land");
    }

    private boolean isPort() {
        return mBinding.getRoot().getTag().equals("port");
    }

    // ==================== BaseActivity overrides ====================

    @Override
    protected boolean transparent() {
        return false;
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityVideoBinding.inflate(getLayoutInflater());
    }

    // ==================== Protected hooks for subclass differences ====================

    /**
     * 子类可重写此方法以支持不同的父布局类型。
     * 默认实现与手机端一致，使用 RelativeLayout.LayoutParams。
     */
    protected ViewGroup.LayoutParams createMatchParentLayoutParams() {
        return new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
    }

    /**
     * 画中画模式下的视频布局更新。
     * 子类可重写以支持不同的布局逻辑。
     */
    protected void updateVideoLayoutForPiP(boolean isInPictureInPictureMode) {
        if (isInPictureInPictureMode) {
            mBinding.video.setLayoutParams(createMatchParentLayoutParams());
        } else {
            mBinding.video.setLayoutParams(mFrameParams);
        }
    }

    // ==================== Lifecycle ====================

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String id = Objects.toString(intent.getStringExtra("id"), "");
        if (TextUtils.isEmpty(id) || id.equals(getId())) return;
        mBinding.swipeLayout.setRefreshing(true);
        getIntent().putExtras(intent);
        stopSearch();
        setOrient();
        checkId();
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mKeyDown = CustomKeyDownVod.create(this, mBinding.exo);
        mFrameParams = mBinding.video.getLayoutParams();
        mBinding.progressLayout.showProgress();
        mBinding.swipeLayout.setEnabled(false);
        mObserveDetail = this::setDetail;
        mObservePlayer = this::setPlayer;
        mObserveSearch = this::setSearch;
        mPlayers = Players.create(this);
        mBroken = new ArrayList<>();
        mClock = Clock.create();
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mR1 = this::hideControl;
        mR2 = this::setTraffic;
        mR3 = this::setOrient;
        mR4 = this::showEmpty;
        mPiP = new PiP();
        // 初始化控制器组件
        mPlayerController = new PlayerController(mPlayers, mBinding, mR1);
        // 上一集/下一集按钮切换后触发播放刷新
        mPlayerController.setOnEpisodeSwitch(() -> {
            if (mFlagAdapter != null && mEpisodeAdapter != null && !mFlagAdapter.isEmpty() && !mEpisodeAdapter.isEmpty()) {
                onRefresh();
            }
        });
        mGestureController = new GestureController(mPlayerController, mBinding, mAudioManager, mR1);
        mControlPanel = new ControlPanelManager(mBinding, mPlayerController, this, mR1, mR3, mPiP);
        mEpisodeMgr = new EpisodeManager(mBinding);
        checkDanmakuImg();
        setRecyclerView();
        setVideoView();
        // 设置 PlayerController 依赖：注入 FlagAdapter/EpisodeAdapter/QualityAdapter/ParseAdapter/tag
        // 防止用户在详情数据加载完成前上滑触发 playNext 时 mEpisodeAdapter 为 null 导致 NPE
        mPlayerController.setDependencies(null, mFlagAdapter, mEpisodeAdapter, mQualityAdapter, mParseAdapter, tag);
        setViewModel();
        showProgress();
        showDanmaku();
        checkId();
        mHandler = new Handler(Looper.getMainLooper());
        initTimeBatteryUpdate();
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void initEvent() {
        mBinding.name.setOnClickListener(view -> onName());
        mBinding.more.setOnClickListener(view -> onMore());
        mBinding.content.setOnClickListener(view -> onContent());
        mBinding.reverse.setOnClickListener(view -> onReverse());
        mBinding.name.setOnLongClickListener(view -> onChange());
        mBinding.content.setOnLongClickListener(view -> onCopy());
        mBinding.control.cast.setOnClickListener(view -> onCast());
        mBinding.control.info.setOnClickListener(view -> onInfo());
        mBinding.control.full.setOnClickListener(view -> onFull());
        mBinding.control.keep.setOnClickListener(view -> onKeep());
        mBinding.downloadBtn.setOnClickListener(view -> onDownload());
        mBinding.downloadMgr.setOnClickListener(view -> onDownloadMgr());
        mBinding.control.play.setOnClickListener(view -> checkPlay());
        mBinding.control.next.setOnClickListener(view -> checkNext());
        mBinding.control.prev.setOnClickListener(view -> checkPrev());
        mBinding.control.setting.setOnClickListener(view -> onSetting());
        mBinding.control.title.setOnLongClickListener(view -> onChange());
        mBinding.control.right.back.setOnClickListener(view -> onFull());
        mBinding.control.right.lock.setOnClickListener(view -> onLock());
        mBinding.control.right.rotate.setOnClickListener(view -> onRotate());
        mBinding.control.danmaku.setOnClickListener(view -> onDanmakuShow());
        mBinding.control.action.text.setOnClickListener(this::onTrack);
        mBinding.control.action.audio.setOnClickListener(this::onTrack);
        mBinding.control.action.video.setOnClickListener(this::onTrack);
        mBinding.control.action.loop.setOnClickListener(view -> onLoop());
        mBinding.control.action.scale.setOnClickListener(view -> onScale());
        mBinding.control.action.speed.setOnClickListener(view -> onSpeed());
        mBinding.control.action.reset.setOnClickListener(view -> onReset());
        mBinding.control.action.player.setOnClickListener(view -> onChoose());
        mBinding.control.action.decode.setOnClickListener(view -> onDecode());
        mBinding.control.action.ending.setOnClickListener(view -> onEnding());
        mBinding.control.action.opening.setOnClickListener(view -> onOpening());
        mBinding.control.action.danmaku.setOnClickListener(view -> onDanmaku());
        mBinding.control.action.episodes.setOnClickListener(view -> onEpisodes());
        mBinding.control.action.text.setOnLongClickListener(view -> onTextLong());
        mBinding.control.action.speed.setOnLongClickListener(view -> onSpeedLong());
        mBinding.control.action.reset.setOnLongClickListener(view -> onResetToggle());
        mBinding.control.action.ending.setOnLongClickListener(view -> onEndingReset());
        mBinding.control.action.opening.setOnLongClickListener(view -> onOpeningReset());
        mBinding.video.setOnTouchListener((view, event) -> mKeyDown.onTouchEvent(event));
        mBinding.control.action.getRoot().setOnTouchListener(this::onActionTouch);
        mBinding.swipeLayout.setOnRefreshListener(this::onSwipeRefresh);
        // 增大下拉触发距离，避免滚动时误触刷新
        mBinding.swipeLayout.setDistanceToTriggerSync(400);
        mBinding.control.seek.setListener(mPlayers);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mClock.stop().start();
        setStop(false);
        onPlay();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startTimeBatteryUpdates();
        if (isRedirect()) onPlay();
        setRedirect(false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopTimeBatteryUpdates();
        if (isRedirect()) onPaused();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (Setting.isBackgroundOff()) onPaused();
        if (Setting.isBackgroundOff()) mClock.stop();
        setStop(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopSearch();
        mPlayers.release();
        mClock.release();
        Timer.get().reset();
        RefreshEvent.history();
        PlaybackService.stop();
        mHandler.removeCallbacksAndMessages(null);
        App.removeCallbacks(mR1, mR2, mR3, mR4);
        EventBus.getDefault().unregister(this);
        mViewModel.result.removeObserver(mObserveDetail);
        mViewModel.player.removeObserver(mObservePlayer);
        mViewModel.search.removeObserver(mObserveSearch);
        stopTimeBatteryUpdates();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mGestureController.onKeyDown(keyCode)) return true;
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        if (mControlPanel.isControlVisible()) {
            mControlPanel.hideControl();
        } else if (mControlPanel.isFullscreen() && !mControlPanel.isLock()) {
            mControlPanel.exitFullscreen();
        } else if (!mControlPanel.isLock()) {
            stopSearch();
            super.onBackPressed();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) mPlayers.checkData(data);
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (isRedirect()) return;
        if (isLock()) App.post(this::onLock, 500);
        if (mPlayers.haveTrack(C.TRACK_TYPE_VIDEO)) mPiP.enter(this, mPlayers.getVideoWidth(), mPlayers.getVideoHeight(), getScale());
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, @NonNull Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (!isFullscreen()) updateVideoLayoutForPiP(isInPictureInPictureMode);
        if (isInPictureInPictureMode) {
            hideControl();
            hideDanmaku();
            hideSheet();
        } else {
            mPausedByScreen = false;
            showDanmaku();
            if (isStop()) finish();
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (isAutoRotate() && isPort() && newConfig.orientation == Configuration.ORIENTATION_PORTRAIT && !mControlPanel.isRotate()) mControlPanel.exitFullscreen();
        if (isAutoRotate() && isPort() && newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) mControlPanel.enterFullscreen();
        if (mControlPanel.isFullscreen()) Util.hideSystemUI(this);
        updateTimeBattery();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (isFullscreen() && hasFocus) Util.hideSystemUI(this);
    }

    // ==================== Time & Battery ====================

    private void initTimeBatteryUpdate() {
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
                if (isInPictureInPictureMode()) {
                    if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                        if (mPlayers.isPlaying()) {
                            onPaused();
                            mPausedByScreen = true;
                        }
                    } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                        if (mPausedByScreen) {
                            onPlay();
                            mPausedByScreen = false;
                        }
                    }
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

    private void updateTimeBattery() {
        TextView timeBattery = findViewById(R.id.time_battery);
        TextView batteryText = findViewById(R.id.battery_icon);
        android.widget.ImageView chargingIndicator = findViewById(R.id.charging_indicator);

        if (isFullscreen()) {
            if (timeBattery != null) {
                String time = DateFormat.getTimeFormat(this).format(System.currentTimeMillis());
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

    private void startTimeBatteryUpdates() {
        registerReceiver(mBatteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        IntentFilter screenFilter = new IntentFilter();
        screenFilter.addAction(Intent.ACTION_SCREEN_ON);
        screenFilter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mScreenReceiver, screenFilter);
        updateTimeBattery();
        mHandler.post(mTimeUpdateRunnable);
    }

    private void stopTimeBatteryUpdates() {
        try {
            if (mBatteryReceiver != null) unregisterReceiver(mBatteryReceiver);
        } catch (IllegalArgumentException e) {
            Logger.w("BaseVideoActivity: 注销电量广播失败: " + e.getMessage());
        }
        try {
            if (mScreenReceiver != null) unregisterReceiver(mScreenReceiver);
        } catch (IllegalArgumentException e) {
            Logger.w("BaseVideoActivity: 注销屏幕广播失败: " + e.getMessage());
        }
        mHandler.removeCallbacks(mTimeUpdateRunnable);
    }

    // ==================== Initialization ====================

    private void setRecyclerView() {
        mEpisodeMgr.initAdapters(this, this, this, this, this);
        mFlagAdapter = mEpisodeMgr.getFlagAdapter();
        mEpisodeAdapter = mEpisodeMgr.getEpisodeAdapter();
        mQualityAdapter = mEpisodeMgr.getQualityAdapter();
        mQuickAdapter = mEpisodeMgr.getQuickAdapter();
        mParseAdapter = mEpisodeMgr.getParseAdapter();
    }

    private void setVideoView() {
        mPlayers.init(mBinding.exo);
        PlaybackService.start(mPlayers);
        ExoUtil.setSubtitleView(mBinding.exo);
        mPlayers.setDanmakuView(mBinding.danmaku);
        mPlayers.setTag(tag = UUID.randomUUID().toString());
        if (isPort() && ResUtil.isLand(this)) enterFullscreen();
        mBinding.control.action.decode.setText(mPlayers.getDecodeText());
        mBinding.control.action.danmaku.setVisibility(Setting.isDanmakuLoad() ? View.VISIBLE : View.GONE);
        mBinding.control.action.reset.setText(ResUtil.getStringArray(R.array.select_reset)[Setting.getReset()]);
        mBinding.video.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> mPiP.update(getActivity(), view));
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.result.observeForever(mObserveDetail);
        mViewModel.player.observeForever(mObservePlayer);
        mViewModel.search.observeForever(mObserveSearch);
        mViewModel.episode.observe(this, episode -> {
            onItemClick(episode);
            hideSheet();
        });
    }

    // ==================== Detail & Player ====================

    private void checkId() {
        if (getId().startsWith("push://")) getIntent().putExtra("key", "push_agent").putExtra("id", getId().substring(7));
        if (getId().isEmpty() || getId().startsWith("msearch:")) setEmpty(false);
        else getDetail();
    }

    private void getDetail() {
        mViewModel.detailContent(getKey(), getId());
    }

    private void getDetail(Vod item) {
        getIntent().putExtra("key", item.getSiteKey());
        getIntent().putExtra("pic", item.getVodPic());
        getIntent().putExtra("id", item.getVodId());
        mBinding.swipeLayout.setRefreshing(true);
        mBinding.swipeLayout.setEnabled(false);
        mBinding.scroll.scrollTo(0, 0);
        mClock.setCallback(null);
        mPlayers.reset();
        mPlayers.stop();
        getDetail();
    }

    private void setDetail(Result result) {
        mBinding.swipeLayout.setRefreshing(false);
        if (result.getList().isEmpty()) setEmpty(result.hasMsg());
        else setDetail(result.getList().get(0));
        if (result.hasMsg() && result.getList().isEmpty()) {
            Notify.show(result.getMsg());
        }
    }

    private void setEmpty(boolean finish) {
        if (isFromCollect() || finish) {
            finish();
        } else if (getName().isEmpty()) {
            showEmpty();
        } else {
            mBinding.name.setText(getName());
            App.post(mR4, 10000);
            checkSearch(false);
        }
    }

    private void showEmpty() {
        showError(getString(R.string.error_detail));
        mBinding.swipeLayout.setEnabled(true);
        mBinding.progressLayout.showEmpty();
        stopSearch();
    }

    private void setDetail(Vod item) {
        mActorExpanded = false;
        mCurrentVod = item;
        mBinding.downloadRow.setVisibility(item == null || item.getVodFlags().isEmpty() ? View.GONE : View.VISIBLE);
        mBinding.progressLayout.showContent();
        item.ensureVodPic(getPic());
        item.ensureVodName(getName());
        mBinding.video.setTag(item.getVodPic());
        mBinding.name.setText(item.getVodName());
        setText(mBinding.remark, 0, item.getVodRemarks());
        setText(mBinding.site, R.string.detail_site, getSite().getName());
        setText(mBinding.content, 0, Html.fromHtml(item.getVodContent()).toString());
        setActorText(mBinding.actor, R.string.detail_actor, item.getVodActor(), CastMember.CastType.ACTOR);
        setActorText(mBinding.director, R.string.detail_director, item.getVodDirector(), CastMember.CastType.DIRECTOR);
        mBinding.contentLayout.setVisibility(mBinding.content.getVisibility());
        mFlagAdapter.addAll(item.getVodFlags());
        setOther(mBinding.other, item);
        setArtwork(item.getVodPic());
        setPoster(item.getVodPic());
        App.removeCallbacks(mR4);
        checkHistory(item);
        checkKeepImg();
    }

    private void setActorText(TextView view, int resId, String text, CastMember.CastType type) {
        if (text == null || text.isEmpty()) {
            view.setVisibility(View.GONE);
            return;
        }
        String cleanText = Html.fromHtml(text).toString();
        List<CastMember> members = CastUtil.parseCastMembers(cleanText, type);
        if (members.isEmpty()) {
            view.setVisibility(View.GONE);
            return;
        }
        String label = getString(resId, "");
        SpannableStringBuilder span = new SpannableStringBuilder(label);
        for (int i = 0; i < members.size(); i++) {
            CastMember member = members.get(i);
            int start = span.length();
            span.append(member.getName());
            int end = span.length();
            span.setSpan(getCastClickSpan(member), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (i < members.size() - 1) {
                span.append(" / ");
            }
        }
        if (view.getId() == R.id.actor) {
            SpannableStringBuilder collapsed = new SpannableStringBuilder(span);
            collapsed.append("  展开");
            view.setText(collapsed, TextView.BufferType.SPANNABLE);
            view.setMaxLines(1);
            view.setOnClickListener(v -> {
                mActorExpanded = !mActorExpanded;
                if (mActorExpanded) {
                    ((TextView) v).setText(span, TextView.BufferType.SPANNABLE);
                    ((TextView) v).setMaxLines(Integer.MAX_VALUE);
                } else {
                    SpannableStringBuilder collapsedText = new SpannableStringBuilder(span);
                    collapsedText.append("  展开");
                    ((TextView) v).setText(collapsedText, TextView.BufferType.SPANNABLE);
                    ((TextView) v).setMaxLines(1);
                }
            });
        } else {
            view.setText(span, TextView.BufferType.SPANNABLE);
            view.setMaxLines(1);
            view.setOnClickListener(null);
        }
        view.setVisibility(View.VISIBLE);
        view.setLinkTextColor(MDColor.YELLOW_500);
        view.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private ClickableSpan getCastClickSpan(CastMember member) {
        return new ClickableSpan() {
            @Override
            public void onClick(@NonNull View view) {
                CastWorksActivity.start(BaseVideoActivity.this, member.getName(), member.getType());
            }
            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                super.updateDrawState(ds);
                ds.setUnderlineText(false);
            }
        };
    }

    private void setText(TextView view, int resId, String text) {
        view.setText(getSpan(resId, text), TextView.BufferType.SPANNABLE);
        view.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
        view.setLinkTextColor(MDColor.YELLOW_500);
        CustomMovement.bind(view);
        view.setTag(text);
    }

    private SpannableStringBuilder getSpan(int resId, String text) {
        if (resId > 0) text = getString(resId, text);
        Map<String, String> map = new HashMap<>();
        Matcher m = Sniffer.CLICKER.matcher(text);
        while (m.find()) {
            String key = Trans.s2t(m.group(2)).trim();
            text = text.replace(m.group(), key);
            map.put(key, m.group(1));
        }
        SpannableStringBuilder span = new SpannableStringBuilder(text);
        for (String s : map.keySet()) {
            int index = text.indexOf(s);
            Result result = Result.type(map.get(s));
            span.setSpan(getClickSpan(result), index, index + s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return span;
    }

    private ClickableSpan getClickSpan(Result result) {
        return new ClickableSpan() {
            @Override
            public void onClick(@NonNull View view) {
                FolderActivity.start(getActivity(), getKey(), result);
                ((TextView) view).setMaxLines(Integer.MAX_VALUE);
                setRedirect(true);
            }
        };
    }

    private void setOther(TextView view, Vod item) {
        StringBuilder sb = new StringBuilder();
        if (!item.getVodYear().isEmpty()) sb.append(getString(R.string.detail_year, item.getVodYear())).append("  ");
        if (!item.getVodArea().isEmpty()) sb.append(getString(R.string.detail_area, item.getVodArea())).append("  ");
        if (!item.getTypeName().isEmpty()) sb.append(getString(R.string.detail_type, item.getTypeName())).append("  ");
        view.setVisibility(sb.length() == 0 ? View.GONE : View.VISIBLE);
        view.setText(Util.substring(sb.toString(), 2));
    }

    private void getPlayer(Flag flag, Episode episode, boolean replay) {
        mBinding.control.title.setText(getString(R.string.detail_title, mBinding.name.getText(), episode.getName()));
        mViewModel.playerContent(getKey(), flag.getFlag(), episode.getUrl());
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mBinding.control.title.setSelected(true);
        updateHistory(episode, replay);
        showProgress();
        setMetadata();
    }

    private void setPlayer(Result result) {
        result.getUrl().set(mQualityAdapter.getPosition());
        if (!result.getDesc().isEmpty()) setText(mBinding.content, R.string.detail_content, Html.fromHtml(result.getDesc()).toString());
        setUseParse(VodConfig.hasParse() && ((result.getPlayUrl().isEmpty() && VodConfig.get().getFlags().contains(result.getFlag())) || result.getJx() == 1));
        if (mControlDialog != null && mControlDialog.isVisible()) mControlDialog.setParseVisible(isUseParse());
        mBinding.control.parse.setVisibility(isFullscreen() && isUseParse() ? View.VISIBLE : View.GONE);
        mPlayers.start(result, isUseParse(), getSite().isChangeable() ? getSite().getTimeout() : -1);
        setQualityVisible(result.getUrl().isMulti());
        mBinding.swipeLayout.setRefreshing(false);
        mPlayers.setKey(getHistoryKey());
        mQualityAdapter.addAll(result);
    }

    // ==================== Click callbacks ====================

    @Override
    public void onItemClick(Flag item) {
        if (item.isActivated()) return;
        mFlagAdapter.setActivated(item);
        mBinding.flag.scrollToPosition(mFlagAdapter.getPosition());
        setEpisodeAdapter(item.getEpisodes());
        setQualityVisible(false);
        seamless(item);
    }

    @Override
    public void onItemClick(Episode item) {
        if (shouldEnterFullscreen(item)) return;
        mFlagAdapter.toggle(item);
        notifyItemChanged(mEpisodeAdapter);
        mBinding.episode.scrollToPosition(mEpisodeAdapter.getPosition());
        if (isFullscreen()) Notify.show(getString(R.string.play_ready, item.getName()));
        onRefresh();
    }

    @Override
    public void onItemClick(Result result) {
        try {
            mPlayers.start(result, isUseParse(), getSite().isChangeable() ? getSite().getTimeout() : -1);
        } catch (Exception e) {
            ErrorEvent.extract(tag, e.getMessage());
            Logger.e("Error", e);
        }
    }

    @Override
    public void onItemClick(Vod item) {
        setAutoMode(false);
        getDetail(item);
    }

    @Override
    public void onItemClick(Parse item) {
        setParse(item);
        onRefresh();
    }

    private void setParse(Parse item) {
        VodConfig.get().setParse(item);
        notifyItemChanged(mParseAdapter);
        if (mControlDialog != null && mControlDialog.isVisible()) mControlDialog.updateParse();
    }

    private void setEpisodeAdapter(List<Episode> items) {
        mBinding.control.action.episodes.setVisibility(items.size() < 2 ? View.GONE : View.VISIBLE);
        mBinding.control.nextRoot.setVisibility(items.size() < 2 ? View.GONE : View.VISIBLE);
        mBinding.control.prevRoot.setVisibility(items.size() < 2 ? View.GONE : View.VISIBLE);
        mBinding.episode.setVisibility(items.size() == 0 ? View.GONE : View.VISIBLE);
        mBinding.reverse.setVisibility(items.size() < 2 ? View.GONE : View.VISIBLE);
        mBinding.more.setVisibility(items.size() < 10 ? View.GONE : View.VISIBLE);
        mEpisodeAdapter.addAll(items);
    }

    private void seamless(Flag flag) {
        Episode episode = flag.find(mHistory.getVodRemarks(), getMark().isEmpty());
        setQualityVisible(episode != null && episode.isActivated() && mQualityAdapter.getItemCount() > 1);
        if (episode == null || episode.isActivated()) return;
        mHistory.setVodRemarks(episode.getName());
        onItemClick(episode);
    }

    private void setQualityVisible(boolean visible) {
        mBinding.qualityText.setVisibility(visible ? View.VISIBLE : View.GONE);
        mBinding.quality.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void reverseEpisode(boolean scroll) {
        mFlagAdapter.reverse();
        setEpisodeAdapter(getFlag().getEpisodes());
        if (scroll) mBinding.episode.scrollToPosition(mEpisodeAdapter.getPosition());
    }

    private void onName() {
        String name = mBinding.name.getText().toString();
        Notify.show(getString(R.string.detail_search, name));
        initSearch(name, false);
    }

    private void onMore() {
        EpisodeGridDialog.create().reverse(mHistory.isRevSort()).episodes(mEpisodeAdapter.getItems()).show(this);
    }

    private void onContent() {
        mBinding.content.setMaxLines(mBinding.content.getMaxLines() == 2 ? Integer.MAX_VALUE : 2);
    }

    private void onReverse() {
        mHistory.setRevSort(!mHistory.isRevSort());
        reverseEpisode(false);
    }

    private boolean onChange() {
        checkSearch(true);
        return true;
    }

    private boolean onCopy() {
        Util.copy(mBinding.content.getText().toString());
        return true;
    }

    private void onCast() {
        CastDialog.create().history(mHistory).video(CastVideo.get(mBinding.name.getText().toString(), mPlayers.getUrl(), mPlayers.getPosition())).fm(true).show(this);
    }

    private void onInfo() {
        mControlPanel.showInfoDialog(mBinding.control.title.getText());
    }

    private void onFull() {
        setR1Callback();
        toggleFullscreen();
    }

    private void onKeep() {
        App.execute(() -> {
            Keep keep = Keep.find(getHistoryKey());
            boolean exists = keep != null;
            if (exists) keep.delete();
            else createKeep();
            App.post(() -> {
                Notify.show(exists ? R.string.keep_del : R.string.keep_add);
                RefreshEvent.keep();
                checkKeepImg();
            });
        });
    }

    // ==================== Download ====================

    private void onDownload() {
        hideControl();
        if (!checkStoragePermission()) {
            Notify.show(R.string.error_permission_storage);
            requestStoragePermission();
            return;
        }
        Flag currentFlag = getFlag();
        List<Episode> episodeList = currentFlag != null ? currentFlag.getEpisodes() : null;
        if (episodeList != null && episodeList.size() > 1) {
            downloadMultiEpisodes(currentFlag, episodeList);
            return;
        }
        if (mPlayers.isEmpty() || mPlayers.getUrl().isEmpty()) {
            Notify.show(R.string.error_play_url);
            return;
        }
        String url = mPlayers.getUrl();
        String title = mBinding.name.getText().toString();
        Episode episode = getEpisode();
        if (episode != null && episode.getName() != null && !episode.getName().isEmpty()) {
            title = title + " " + episode.getName();
        }
        String pic = mBinding.video.getTag() != null ? mBinding.video.getTag().toString() : "";
        Downloader.get().title(title).image(pic).start(this, url, mPlayers.getHeaders());
    }

    private void downloadMultiEpisodes(Flag flag, List<Episode> episodeList) {
        DownloadEpisodeDialog.create(episodeList).listener(selected -> {
            String title = mBinding.name.getText().toString();
            String pic = mBinding.video.getTag() != null ? mBinding.video.getTag().toString() : "";
            Map<String, String> headers = mPlayers.getHeaders();
            App.execute(() -> {
                for (Episode episode : selected) {
                    String episodeUrl = episode.getUrl();
                    if (episodeUrl == null || episodeUrl.isEmpty()) continue;
                    String episodeTitle = title;
                    if (episode.getName() != null && !episode.getName().isEmpty()) {
                        episodeTitle = title + " " + episode.getName();
                    }
                    String resolvedUrl = resolveEpisodeUrl(episodeUrl);
                    if (resolvedUrl == null || resolvedUrl.isEmpty()) {
                        Logger.e("DownloadMulti: 解析URL失败: " + episodeUrl);
                        continue;
                    }
                    String finalEpisodeTitle = episodeTitle;
                    App.post(() -> Downloader.get().title(finalEpisodeTitle).image(pic).start(BaseVideoActivity.this, resolvedUrl, headers));
                }
            });
        }).show(this);
    }

    private String resolveEpisodeUrl(String episodeUrl) {
        try {
            Site site = VodConfig.get().getSite(getKey());
            if (site.getType() == 3) {
                com.github.catvod.crawler.Spider spider = site.recent().spider();
                String content = spider.playerContent(getFlag().getFlag(), episodeUrl, VodConfig.get().getFlags());
                Result result = Result.fromJson(content);
                if (result.getFlag().isEmpty()) result.setFlag(getFlag().getFlag());
                result.setUrl(Source.get().fetch(result));
                result.setHeader(site.getHeader());
                return result.getRealUrl();
            } else {
                Url url = Url.create().add(episodeUrl);
                Result result = new Result();
                result.setUrl(url);
                result.setFlag(getFlag().getFlag());
                result.setHeader(site.getHeader());
                result.setPlayUrl(site.getPlayUrl());
                result.setUrl(Source.get().fetch(result));
                return result.getRealUrl();
            }
        } catch (Exception e) {
            Logger.e("DownloadMulti: 解析URL失败: " + episodeUrl, e);
            return null;
        }
    }

    private void onDownloadMgr() {
        if (!checkStoragePermission()) {
            Notify.show(R.string.error_permission_storage);
            requestStoragePermission();
            return;
        }
        App.post(() -> DownloadActivity.start(this), 200);
    }

    private boolean checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.download_permission_title)
                    .setMessage(R.string.download_permission_msg)
                    .setPositiveButton(R.string.download_permission_settings, (d, w) -> {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        } else {
            PermissionX.init(this).permissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    .request((allGranted, grantedList, deniedList) -> {
                        if (allGranted) {
                            Notify.show(R.string.download_permission_granted);
                        } else {
                            Notify.show(R.string.download_permission_denied);
                        }
                    });
        }
    }

    // ==================== Playback controls ====================

    private void checkPlay() {
        setR1Callback();
        mPlayerController.togglePlay();
    }

    private void checkNext() {
        checkNext(true);
    }

    private void checkNext(boolean notify) {
        setR1Callback();
        if (!mPlayerController.playNext() && notify) Notify.show(R.string.error_play_next);
    }

    private void checkPrev() {
        setR1Callback();
        if (!mPlayerController.playPrev()) Notify.show(R.string.error_play_prev);
    }

    private void onSetting() {
        mControlDialog = ControlDialog.create().parent(mBinding).history(mHistory).player(mPlayers).parse(isUseParse()).show(this);
    }

    private void onLock() {
        mControlPanel.toggleLock();
        setRequestedOrientation(getLockOrient());
        mKeyDown.setLock(mControlPanel.isLock());
        checkLockImg();
    }

    private void onRotate() {
        setR1Callback();
        mControlPanel.toggleRotate();
    }

    private void onTrack(View view) {
        mControlPanel.showTrackDialog(Integer.parseInt(view.getTag().toString()));
    }

    private void onDanmaku() {
        mControlPanel.showDanmakuDialog();
    }

    private void onDanmakuShow() {
        Setting.putDanmakuShow(!Setting.isDanmakuShow());
        checkDanmakuImg();
        showDanmaku();
    }

    private void onLoop() {
        mPlayerController.toggleLoop();
    }

    private void onScale() {
        int index = getScale();
        String[] array = ResUtil.getStringArray(R.array.select_scale);
        if (mKeyDown.getScale() != 1.0f) mKeyDown.resetScale();
        else mPlayerController.setScale(index == array.length - 1 ? 0 : ++index);
        setR1Callback();
    }

    private void onSpeed() {
        mBinding.control.action.speed.setText(mPlayerController.addSpeed());
        setR1Callback();
    }

    private boolean onSpeedLong() {
        mBinding.control.action.speed.setText(mPlayerController.toggleSpeed());
        setR1Callback();
        return true;
    }

    private void onRefresh() {
        onReset(false);
    }

    private void onReset() {
        onReset(isReplay());
    }

    private void onReset(boolean replay) {
        mPlayers.stop();
        mPlayers.clear();
        mClock.setCallback(null);
        if (mFlagAdapter.isEmpty()) return;
        if (mEpisodeAdapter.isEmpty()) return;
        getPlayer(getFlag(), getEpisode(), replay);
    }

    private boolean onResetToggle() {
        Setting.putReset(Math.abs(Setting.getReset() - 1));
        mBinding.control.action.reset.setText(ResUtil.getStringArray(R.array.select_reset)[Setting.getReset()]);
        return true;
    }

    private void onDecode() {
        mPlayerController.toggleDecode();
        setR1Callback();
    }

    private void onEnding() {
        mPlayerController.onEnding();
        setR1Callback();
    }

    private boolean onEndingReset() {
        setR1Callback();
        mPlayerController.setEnding(0);
        return true;
    }

    private void setEnding(long ending) {
        mPlayerController.setEnding(ending);
    }

    private void onOpening() {
        mPlayerController.onOpening();
        setR1Callback();
    }

    private boolean onOpeningReset() {
        setR1Callback();
        mPlayerController.setOpening(0);
        return true;
    }

    private void setOpening(long opening) {
        mPlayerController.setOpening(opening);
    }

    private void onEpisodes() {
        mControlPanel.showEpisodeListDialog(mEpisodeAdapter.getItems());
    }

    private void onChoose() {
        mPlayers.choose(this, mBinding.control.title.getText());
        setRedirect(true);
    }

    private boolean onTextLong() {
        mControlPanel.showSubtitleDialog();
        return true;
    }

    private boolean onActionTouch(View v, MotionEvent e) {
        setR1Callback();
        return false;
    }

    private void onSwipeRefresh() {
        if (mBinding.progressLayout.isEmpty()) getDetail();
        else onRefresh();
    }

    // ==================== Fullscreen ====================

    private void toggleFullscreen() {
        mControlPanel.toggleFullscreen();
        if (mControlPanel.isFullscreen()) {
            App.post(() -> mBinding.video.setLayoutParams(createMatchParentLayoutParams()), 50);
            setRequestedOrientation(mPlayers.isPortrait() ? ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            mKeyDown.resetScale();
        } else {
            setRequestedOrientation(isPort() ? ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
            App.post(() -> mBinding.episode.scrollToPosition(mEpisodeAdapter.getPosition()), 50);
            mBinding.video.setLayoutParams(mFrameParams);
            mKeyDown.resetScale();
        }
    }

    private boolean shouldEnterFullscreen(Episode item) {
        return mControlPanel.shouldEnterFullscreenOnEpisodeClick(item.isActivated());
    }

    private void enterFullscreen() {
        if (mControlPanel.isFullscreen()) return;
        App.post(() -> mBinding.video.setLayoutParams(createMatchParentLayoutParams()), 50);
        setRequestedOrientation(mPlayers.isPortrait() ? ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        mKeyDown.resetScale();
        mControlPanel.enterFullscreen();
    }

    private void exitFullscreen() {
        if (!mControlPanel.isFullscreen()) return;
        setRequestedOrientation(isPort() ? ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
        App.post(() -> mBinding.episode.scrollToPosition(mEpisodeAdapter.getPosition()), 50);
        mBinding.video.setLayoutParams(mFrameParams);
        mKeyDown.resetScale();
        mControlPanel.exitFullscreen();
    }

    private int getLockOrient() {
        return mControlPanel.getLockOrientation(isPort(), isAutoRotate());
    }

    // ==================== UI visibility ====================

    private void showProgress() {
        mBinding.widget.progress.setVisibility(View.VISIBLE);
        App.post(mR2, 0);
        hideError();
    }

    private void hideProgress() {
        mBinding.widget.progress.setVisibility(View.GONE);
        App.removeCallbacks(mR2);
        Traffic.reset();
    }

    private void showError(String text) {
        mBinding.widget.error.setVisibility(View.VISIBLE);
        mBinding.widget.text.setText(text);
        hideProgress();
    }

    private void hideError() {
        mBinding.widget.error.setVisibility(View.GONE);
        mBinding.widget.text.setText("");
    }

    private void showDanmaku() {
        mBinding.danmaku.setVisibility(Setting.isDanmakuShow() ? View.VISIBLE : View.INVISIBLE);
    }

    private void hideDanmaku() {
        mBinding.danmaku.setVisibility(View.INVISIBLE);
    }

    private void showControl() {
        mControlPanel.showControl();
        updateTimeBattery();
        setR1Callback();
        mPlayerController.checkPlayImg();
    }

    private void hideControl() {
        mControlPanel.hideControl();
    }

    private void hideSheet() {
        mControlPanel.hideSheet();
    }

    // ==================== Utility methods ====================

    private void setTraffic() {
        Traffic.setSpeed(mBinding.widget.traffic);
        App.post(mR2, Constant.INTERVAL_TRAFFIC);
    }

    private void setOrient() {
        if (isPort() && isAutoRotate()) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
        if (isLand() && isAutoRotate()) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE);
    }

    private void setR1Callback() {
        App.post(mR1, Constant.INTERVAL_HIDE);
    }

    private void setArtwork(String url) {
        ImgUtil.load(url, R.drawable.radio, new CustomTarget<>(ResUtil.getScreenWidth(), ResUtil.getScreenHeight()) {
            @Override
            public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                mBinding.exo.setDefaultArtwork(resource);
            }
            @Override
            public void onLoadFailed(@Nullable Drawable error) {
                mBinding.exo.setDefaultArtwork(error);
            }
            @Override
            public void onLoadCleared(@Nullable Drawable placeholder) {
            }
        });
    }

    private void setPoster(String url) {
        ImgUtil.load(url, R.drawable.radio, new CustomTarget<>(100 * 3, 140 * 3) {
            @Override
            public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                mBinding.poster.setImageDrawable(resource);
            }
            @Override
            public void onLoadFailed(@Nullable Drawable error) {
                mBinding.poster.setImageResource(R.drawable.radio);
            }
            @Override
            public void onLoadCleared(@Nullable Drawable placeholder) {
            }
        });
    }

    private void checkFlag(Vod item) {
        boolean empty = item.getVodFlags().isEmpty();
        mBinding.flag.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) {
            ErrorEvent.flag(tag);
        } else {
            onItemClick(mHistory.getFlag());
            if (mHistory.isRevSort()) reverseEpisode(true);
        }
    }

    private void checkHistory(Vod item) {
        App.execute(() -> {
            History history = History.find(getHistoryKey());
            if (history == null) history = createHistory(item);
            if (Setting.isIncognito() && history.getKey().equals(getHistoryKey())) history.delete();
            final History finalHistory = history;
            App.post(() -> {
                mHistory = finalHistory;
                // 同步设置 PlayerController 的 history 依赖，避免播放控制时 mHistory 为 null
                mPlayerController.setHistory(mHistory);
                if (!TextUtils.isEmpty(getMark())) mHistory.setVodRemarks(getMark());
                mBinding.control.action.opening.setText(mHistory.getOpening() <= 0 ? getString(R.string.play_op) : mPlayers.stringToTime(mHistory.getOpening()));
                mBinding.control.action.ending.setText(mHistory.getEnding() <= 0 ? getString(R.string.play_ed) : mPlayers.stringToTime(mHistory.getEnding()));
                mBinding.control.action.speed.setText(mPlayers.setSpeed(mHistory.getSpeed()));
                mHistory.setVodPic(item.getVodPic());
                mPlayerController.setScale(getScale());
                checkFlag(item);
            });
        });
    }

    private History createHistory(Vod item) {
        History history = new History();
        history.setKey(getHistoryKey());
        history.setCid(VodConfig.getCid());
        history.setVodName(item.getVodName());
        history.findEpisode(item.getVodFlags());
        return history;
    }

    private void updateHistory(Episode item, boolean replay) {
        replay = replay || !item.equals(mHistory.getEpisode());
        mHistory.setEpisodeUrl(item.getUrl());
        mHistory.setVodRemarks(item.getName());
        mHistory.setVodFlag(getFlag().getFlag());
        mHistory.setCreateTime(System.currentTimeMillis());
        mHistory.setPosition(replay ? C.TIME_UNSET : mHistory.getPosition());
    }

    private void checkControl() {
        if (isVisible(mBinding.control.getRoot())) showControl();
    }

    private void checkPlayImg() {
        mBinding.control.play.setImageResource(mPlayers.isPlaying() ? androidx.media3.ui.R.drawable.exo_icon_pause : androidx.media3.ui.R.drawable.exo_icon_play);
        mPiP.update(this, mPlayers.isPlaying());
        ActionEvent.update();
    }

    private void checkKeepImg() {
        App.execute(() -> {
            boolean exists = Keep.find(getHistoryKey()) != null;
            App.post(() -> mBinding.control.keep.setImageResource(exists ? R.drawable.ic_control_keep_off : R.drawable.ic_control_keep_on));
        });
    }

    private void checkLockImg() {
        mBinding.control.right.lock.setImageResource(isLock() ? R.drawable.ic_control_lock_on : R.drawable.ic_control_lock_off);
    }

    private void checkDanmakuImg() {
        mBinding.control.danmaku.setImageResource(Setting.isDanmakuShow() ? R.drawable.ic_control_danmaku_on : R.drawable.ic_control_danmaku_off);
    }

    private void createKeep() {
        Keep keep = new Keep();
        keep.setKey(getHistoryKey());
        keep.setCid(VodConfig.getCid());
        keep.setSiteName(getSite().getName());
        keep.setVodPic(mBinding.video.getTag().toString());
        keep.setVodName(mBinding.name.getText().toString());
        keep.setCreateTime(System.currentTimeMillis());
        keep.save();
    }

    // ==================== Subtitle ====================

    @Override
    public void onSubtitleClick() {
        mControlPanel.showSubtitleDialog();
    }

    // ==================== Clock callback ====================

    @Override
    public void onTimeChanged() {
        long position, duration;
        mHistory.setPosition(position = mPlayers.getPosition());
        mHistory.setDuration(duration = mPlayers.getDuration());
        if (position >= 0 && duration > 0 && !Setting.isIncognito()) App.execute(() -> mHistory.update());
        if (mHistory.getEnding() > 0 && duration > 0 && mHistory.getEnding() + position >= duration) {
            checkEnded(false);
        }
    }

    // ==================== EventBus ====================

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onCastEvent(CastEvent event) {
        if (isRedirect()) return;
        mControlPanel.onReceiveEvent(event);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onActionEvent(ActionEvent event) {
        if (isRedirect()) return;
        if (ActionEvent.PLAY.equals(event.getAction()) || ActionEvent.PAUSE.equals(event.getAction())) {
            mBinding.control.play.performClick();
        } else if (ActionEvent.NEXT.equals(event.getAction())) {
            mBinding.control.next.performClick();
        } else if (ActionEvent.PREV.equals(event.getAction())) {
            mBinding.control.prev.performClick();
        } else if (ActionEvent.STOP.equals(event.getAction())) {
            finish();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (isRedirect()) return;
        if (event.getType() == RefreshEvent.Type.DETAIL) getDetail();
        else if (event.getType() == RefreshEvent.Type.PLAYER) onRefresh();
        else if (event.getType() == RefreshEvent.Type.SUBTITLE) mPlayers.setSub(Sub.from(event.getPath()));
        else if (event.getType() == RefreshEvent.Type.DANMAKU) mPlayers.setDanmaku(Danmaku.from(event.getPath()));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPlayerEvent(PlayerEvent event) {
        if (!event.getTag().equals(tag)) return;
        switch (event.getState()) {
            case PlayerEvent.PREPARE:
                mBinding.control.action.decode.setText(mPlayers.getDecodeText());
                setPosition();
                break;
            case Player.STATE_BUFFERING:
                showProgress();
                break;
            case Player.STATE_READY:
                hideProgress();
                checkControl();
                checkPlayImg();
                mPlayers.reset();
                break;
            case Player.STATE_ENDED:
                checkEnded(true);
                break;
            case PlayerEvent.TRACK:
                setMetadata();
                setTrackVisible();
                mClock.setCallback(this);
                break;
            case PlayerEvent.SIZE:
                checkOrientation();
                mBinding.control.size.setText(mPlayers.getSizeText());
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onErrorEvent(ErrorEvent event) {
        if (!event.getTag().equals(tag)) return;
        onError(event);
    }

    // ==================== Player event helpers ====================

    private void setPosition() {
        mPlayerController.setPosition();
    }

    private void checkOrientation() {
        if (isFullscreen() && !isRotate() && mPlayers.isPortrait()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT);
            setRotate(true);
        } else if (isFullscreen() && isRotate() && mPlayers.isLandscape()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE);
            setRotate(false);
        }
    }

    private void checkEnded(boolean notify) {
        if (mPlayerController.isLoopEnabled()) {
            onReset(true);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            checkNext(notify);
            checkPlayImg();
        }
    }

    private void setTrackVisible() {
        mPlayerController.setTrackVisible();
        if (mControlDialog != null && mControlDialog.isVisible()) mControlDialog.setTrackVisible();
    }

    private void setMetadata() {
        mPlayerController.setMetadata();
    }

    // ==================== Error flow ====================

    private void onError(ErrorEvent event) {
        mBinding.swipeLayout.setEnabled(true);
        App.execute(() -> Track.delete(mPlayers.getUrl()));
        showError(event.getMsg());
        mClock.setCallback(null);
        mPlayers.resetTrack();
        mPlayers.reset();
        mPlayers.stop();
        startFlow();
    }

    private void startFlow() {
        if (!getSite().isChangeable()) return;
        if (isUseParse()) checkParse();
        else checkFlag();
    }

    private void checkParse() {
        int position = mParseAdapter.getPosition();
        boolean last = position == mParseAdapter.getItemCount() - 1;
        boolean pass = position == 0 || last;
        if (last) initParse();
        if (pass) checkFlag();
        else nextParse(position);
    }

    private void initParse() {
        if (mParseAdapter.isEmpty()) return;
        setParse(mParseAdapter.first());
    }

    private void checkFlag() {
        int position = isGone(mBinding.flag) ? -1 : mFlagAdapter.getPosition();
        if (position == mFlagAdapter.getItemCount() - 1) checkSearch(false);
        else nextFlag(position);
    }

    private void checkSearch(boolean force) {
        if (mQuickAdapter.isEmpty()) initSearch(mBinding.name.getText().toString(), true);
        else if (isAutoMode() || force) nextSite();
    }

    private void initSearch(String keyword, boolean auto) {
        stopSearch();
        setAutoMode(auto);
        setInitAuto(auto);
        startSearch(keyword);
    }

    private boolean isPass(Site item) {
        if (isAutoMode() && !item.isChangeable()) return false;
        return item.isSearchable();
    }

    private void startSearch(String keyword) {
        mQuickAdapter.clear();
        List<Site> sites = new ArrayList<>();
        mExecutor = Executors.newFixedThreadPool(20);
        for (Site item : VodConfig.get().getSites()) if (isPass(item)) sites.add(item);
        for (Site site : sites) mExecutor.execute(() -> search(site, keyword));
    }

    private void stopSearch() {
        if (mExecutor == null) return;
        mExecutor.shutdownNow();
        mExecutor = null;
    }

    private void search(Site site, String keyword) {
        try {
            mViewModel.searchContent(site, keyword, true);
        } catch (Throwable e) {
            Logger.w("BaseVideoActivity: search", e);
        }
    }

    private void setSearch(Result result) {
        List<Vod> items = result.getList();
        Iterator<Vod> iterator = items.iterator();
        while (iterator.hasNext()) if (mismatch(iterator.next())) iterator.remove();
        mBinding.quick.setVisibility(View.VISIBLE);
        mQuickAdapter.addAll(items);
        if (isInitAuto()) nextSite();
        if (items.isEmpty()) return;
        App.removeCallbacks(mR4);
    }

    private boolean mismatch(Vod item) {
        if (getId().equals(item.getVodId())) return true;
        if (mBroken.contains(item.getVodId())) return true;
        String keyword = mBinding.name.getText().toString();
        if (isAutoMode()) return !item.getVodName().equals(keyword);
        else return !item.getVodName().contains(keyword);
    }

    private void nextParse(int position) {
        Parse parse = mParseAdapter.get(position + 1);
        Notify.show(getString(R.string.play_switch_parse, parse.getName()));
        onItemClick(parse);
    }

    private void nextFlag(int position) {
        Flag flag = mFlagAdapter.get(position + 1);
        Notify.show(getString(R.string.play_switch_flag, flag.getFlag()));
        onItemClick(flag);
    }

    private void nextSite() {
        if (mQuickAdapter.isEmpty()) return;
        Vod item = mQuickAdapter.get(0);
        Notify.show(getString(R.string.play_switch_site, item.getSiteName()));
        mQuickAdapter.remove(0);
        mBroken.add(getId());
        setInitAuto(false);
        getDetail(item);
    }

    // ==================== Pause / Play ====================

    private void onPaused() {
        mPlayerController.pause();
        if (mBinding.dim != null) {
            mBinding.dim.setVisibility(View.VISIBLE);
        }
    }

    private void onPlay() {
        mPlayerController.play();
        if (mBinding.dim != null) {
            mBinding.dim.setVisibility(View.GONE);
        }
    }

    // ==================== State getters/setters ====================

    private boolean isFullscreen() {
        return mControlPanel.isFullscreen();
    }

    private void setFullscreen(boolean fullscreen) {
        Util.toggleFullscreen(this, fullscreen);
        if (fullscreen) mControlPanel.setRotate(true, true);
        else mControlPanel.setRotate(false, false);
    }

    private boolean isInitAuto() {
        return initAuto;
    }

    private void setInitAuto(boolean initAuto) {
        this.initAuto = initAuto;
    }

    private boolean isAutoMode() {
        return autoMode;
    }

    private void setAutoMode(boolean autoMode) {
        this.autoMode = autoMode;
    }

    public boolean isUseParse() {
        return useParse;
    }

    public void setUseParse(boolean useParse) {
        this.useParse = useParse;
    }

    public boolean isRedirect() {
        return redirect;
    }

    public void setRedirect(boolean redirect) {
        this.redirect = redirect;
    }

    public boolean isRotate() {
        return mControlPanel.isRotate();
    }

    public void setRotate(boolean rotate, boolean fullscreen) {
        mControlPanel.setRotate(rotate, fullscreen);
        if (fullscreen) {
            setFullscreen(fullscreen);
        }
    }

    public void setRotate(boolean rotate) {
        mControlPanel.setRotate(rotate);
        onOrientationChanged();
    }

    private void onOrientationChanged() {
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mGestureController.onLandscapeMode();
        } else {
            mGestureController.onPortraitMode();
        }
    }

    public boolean isStop() {
        return stop;
    }

    public void setStop(boolean stop) {
        this.stop = stop;
    }

    public boolean isLock() {
        return mControlPanel.isLock();
    }

    public void setLock(boolean lock) {
        mControlPanel.setLock(lock);
    }

    private void notifyItemChanged(RecyclerView.Adapter<?> adapter) {
        adapter.notifyItemRangeChanged(0, adapter.getItemCount());
    }

    // ==================== Interface implementations ====================

    @Override
    public void onCasted() {
        onPaused();
    }

    @Override
    public void onScale(int tag) {
        mKeyDown.resetScale();
        mPlayerController.setScale(tag);
    }

    @Override
    public void onParse(Parse item) {
        onItemClick(item);
    }

    @Override
    public void onSpeedUp() {
        mPlayerController.onSpeedUp();
    }

    @Override
    public void onSpeedEnd() {
        mPlayerController.onSpeedEnd();
    }

    @Override
    public void onBright(int progress) {
        mGestureController.onBright(progress);
    }

    @Override
    public void onBrightEnd() {
        mGestureController.onBrightEnd();
    }

    @Override
    public void onVolume(int progress) {
        mGestureController.onVolume(progress);
    }

    @Override
    public void onVolumeEnd() {
        mGestureController.onVolumeEnd();
    }

    @Override
    public void onFlingUp() {
        mGestureController.onFlingUp();
    }

    @Override
    public void onFlingDown() {
        mGestureController.onFlingDown();
    }

    @Override
    public void onSeek(long time) {
        mGestureController.onSeek(time);
    }

    @Override
    public void onSeekEnd(long time) {
        mGestureController.onSeekEnd(time);
    }

    @Override
    public void onSingleTap() {
        if (isVisible(mBinding.control.getRoot())) {
            hideControl();
        } else {
            showControl();
        }
    }

    @Override
    public void onDoubleTap() {
        if (!isFullscreen()) {
            App.post(this::enterFullscreen, 250);
        }
    }

    @Override
    public void onDoubleTapLeft() {
        mGestureController.onDoubleTapLeft();
    }

    @Override
    public void onDoubleTapRight() {
        mGestureController.onDoubleTapRight();
    }

    @Override
    public void onShare(CharSequence title) {
        mPlayers.share(this, title);
        setRedirect(true);
    }
}