package com.fongmi.android.tv.player;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.Configuration;
import android.view.View;

import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.databinding.ActivityVideoBinding;
import com.fongmi.android.tv.ui.dialog.ControlDialog;
import com.fongmi.android.tv.ui.dialog.DanmakuDialog;
import com.fongmi.android.tv.ui.dialog.EpisodeListDialog;
import com.fongmi.android.tv.ui.dialog.InfoDialog;
import com.fongmi.android.tv.ui.dialog.SubtitleDialog;
import com.fongmi.android.tv.ui.dialog.TrackDialog;
import com.fongmi.android.tv.utils.PiP;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;

import java.util.ArrayList;
import java.util.List;

public class ControlPanelManager {

    private final ActivityVideoBinding mBinding;
    private final PlayerController mPlayerController;
    private final FragmentActivity mActivity;
    private final Runnable mR1;
    private final Runnable mR3;
    private final PiP mPiP;
    private final List<Dialog> mDialogs;
    private ControlDialog mControlDialog;
    private boolean fullscreen;
    private boolean lock;
    private boolean rotate;
    private boolean useParse;

    public ControlPanelManager(ActivityVideoBinding binding, PlayerController playerController, FragmentActivity activity, Runnable hideControlRunnable, Runnable orientRunnable, PiP pip) {
        this.mBinding = binding;
        this.mPlayerController = playerController;
        this.mActivity = activity;
        this.mR1 = hideControlRunnable;
        this.mR3 = orientRunnable;
        this.mPiP = pip;
        this.mDialogs = new ArrayList<>();
    }

    public void setUseParse(boolean useParse) {
        this.useParse = useParse;
    }

    public boolean isUseParse() {
        return useParse;
    }

    public boolean isFullscreen() {
        return fullscreen;
    }

    public boolean isLock() {
        return lock;
    }

    public boolean isRotate() {
        return rotate;
    }

    public PiP getPiP() {
        return mPiP;
    }

    // ==================== Control visibility ====================

    public void showControl() {
        if (mPiP.isInMode(mActivity)) return;
        Players players = mPlayerController.getPlayers();
        mBinding.control.danmaku.setVisibility(lock || !players.haveDanmaku() ? View.GONE : View.VISIBLE);
        mBinding.control.setting.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        mBinding.control.right.rotate.setVisibility(fullscreen && !lock ? View.VISIBLE : View.GONE);
        mBinding.control.keep.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        mBinding.control.right.back.setVisibility(fullscreen && !lock ? View.VISIBLE : View.GONE);
        mBinding.control.parse.setVisibility(fullscreen && useParse ? View.VISIBLE : View.GONE);
        mBinding.control.action.getRoot().setVisibility(fullscreen ? View.VISIBLE : View.GONE);
        mBinding.control.right.lock.setVisibility(fullscreen ? View.VISIBLE : View.GONE);
        mBinding.control.info.setVisibility(players.isEmpty() ? View.GONE : View.VISIBLE);
        mBinding.control.cast.setVisibility(players.isEmpty() ? View.GONE : View.VISIBLE);
        mBinding.control.center.setVisibility(lock ? View.GONE : View.VISIBLE);
        mBinding.control.bottom.setVisibility(lock ? View.GONE : View.VISIBLE);
        mBinding.control.top.setVisibility(lock ? View.GONE : View.VISIBLE);
        mBinding.control.getRoot().setVisibility(View.VISIBLE);
        setR1Callback();
        mPlayerController.checkPlayImg();
    }

    public void hideControl() {
        mBinding.control.getRoot().setVisibility(View.GONE);
        App.removeCallbacks(mR1);
    }

    public boolean isControlVisible() {
        return mBinding.control.getRoot().getVisibility() == View.VISIBLE;
    }

    public void hideSheet() {
        for (Dialog dialog : mDialogs) dialog.dismiss();
        for (Fragment fragment : mActivity.getSupportFragmentManager().getFragments()) {
            if (fragment instanceof DialogFragment) {
                ((DialogFragment) fragment).dismiss();
            }
        }
        mDialogs.clear();
    }

    // ==================== Fullscreen ====================

    public void toggleFullscreen() {
        if (fullscreen) exitFullscreen();
        else enterFullscreen();
    }

    public void enterFullscreen() {
        if (fullscreen) return;
        fullscreen = true;
        mBinding.control.full.setVisibility(View.GONE);
        setRotate(true);
        mPlayerController.getPlayers().setDanmakuSize(1.0f);
        Util.hideSystemUI(mActivity);
        App.post(mR3, 2000);
        hideControl();
    }

    public void exitFullscreen() {
        if (!fullscreen) return;
        fullscreen = false;
        mBinding.control.full.setVisibility(View.VISIBLE);
        mPlayerController.getPlayers().setDanmakuSize(0.8f);
        setRotate(false);
        Util.showSystemUI(mActivity);
        App.post(mR3, 2000);
        hideControl();
    }

    public boolean shouldEnterFullscreenOnEpisodeClick(boolean isActivated) {
        boolean enter = !fullscreen && isActivated;
        if (enter) enterFullscreen();
        return enter;
    }

    // ==================== Lock ====================

    public void setLock(boolean lock) {
        this.lock = lock;
        mBinding.control.right.lock.setImageResource(lock ? R.drawable.ic_control_lock_on : R.drawable.ic_control_lock_off);
    }

    public void toggleLock() {
        setLock(!lock);
        showControl();
    }

    public int getLockOrientation(boolean isPort, boolean isAutoRotate) {
        if (lock) {
            return ResUtil.isLand(mActivity)
                    ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT;
        } else if (rotate) {
            return android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT;
        } else if (isPort && isAutoRotate) {
            return android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_USER;
        } else {
            return ResUtil.isLand(mActivity)
                    ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT;
        }
    }

    // ==================== Rotate ====================

    public void toggleRotate() {
        rotate = !rotate;
        mActivity.setRequestedOrientation(ResUtil.isLand(mActivity)
                ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
                : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    }

    public void setRotate(boolean rotate) {
        this.rotate = rotate;
        if (fullscreen && rotate) noPadding(mBinding.control.getRoot());
        if (fullscreen && !rotate) setPadding(mBinding.control.getRoot());
    }

    public void setRotate(boolean rotate, boolean fullscreen) {
        this.rotate = rotate;
        this.fullscreen = fullscreen;
        if (!fullscreen || rotate) noPadding(mBinding.control.getRoot());
        if (fullscreen && !rotate) setPadding(mBinding.control.getRoot());
    }

    // ==================== Dialogs ====================

    public void showSettingDialog(HistoryProvider historyProvider) {
        mControlDialog = ControlDialog.create()
                .parent(mBinding)
                .history(historyProvider.getHistory())
                .player(mPlayerController.getPlayers())
                .parse(useParse)
                .show(mActivity);
    }

    public void showTrackDialog(int type) {
        TrackDialog.create()
                .player(mPlayerController.getPlayers())
                .type(type)
                .show(mActivity);
        hideControl();
    }

    public void showDanmakuDialog() {
        DanmakuDialog.create()
                .player(mPlayerController.getPlayers())
                .show(mActivity);
        hideControl();
    }

    public void showSubtitleDialog() {
        App.post(this::hideControl, 200);
        App.post(() -> SubtitleDialog.create()
                .view(mBinding.exo.getSubtitleView())
                .full(fullscreen)
                .show(mActivity), 200);
    }

    public void showInfoDialog(CharSequence title) {
        Players players = mPlayerController.getPlayers();
        InfoDialog.create(mActivity)
                .title(title)
                .headers(players.getHeaders())
                .url(players.getUrl())
                .show();
    }

    public void showEpisodeListDialog(java.util.List<com.fongmi.android.tv.bean.Episode> episodes) {
        mDialogs.add(EpisodeListDialog.create(mActivity)
                .episodes(episodes)
                .show());
    }

    // ==================== Control dialog update ====================

    public void updateControlDialogIfVisible() {
        if (mControlDialog != null && mControlDialog.isVisible()) {
            mControlDialog.setParseVisible(useParse);
        }
    }

    public void updateControlDialogTrack() {
        if (mControlDialog != null && mControlDialog.isVisible()) {
            mControlDialog.setTrackVisible();
        }
    }

    public void updateControlDialogParse() {
        // parse update delegated
    }

    // ==================== UI helpers ====================

    private void setR1Callback() {
        App.post(mR1, Constant.INTERVAL_HIDE);
    }

    private void noPadding(View view) {
        view.setPadding(0, 0, 0, 0);
    }

    private void setPadding(View view) {
        int dp = (int) (8 * mActivity.getResources().getDisplayMetrics().density);
        view.setPadding(dp, 0, dp, 0);
    }

    public interface HistoryProvider {
        com.fongmi.android.tv.bean.History getHistory();
    }
}