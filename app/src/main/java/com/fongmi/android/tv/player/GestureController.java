package com.fongmi.android.tv.player;

import android.content.res.Configuration;
import android.media.AudioManager;
import android.view.KeyEvent;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivityVideoBinding;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.concurrent.TimeUnit;

public class GestureController {

    private final PlayerController mPlayerController;
    private final ActivityVideoBinding mBinding;
    private final AudioManager mAudioManager;
    private final Runnable mR1;

    public GestureController(PlayerController playerController, ActivityVideoBinding binding, AudioManager audioManager, Runnable hideControlRunnable) {
        this.mPlayerController = playerController;
        this.mBinding = binding;
        this.mAudioManager = audioManager;
        this.mR1 = hideControlRunnable;
    }

    // ==================== Key events ====================

    public boolean onKeyDown(int keyCode) {
        Players players = mPlayerController.getPlayers();
        if (players == null || players.isEmpty()) return false;

        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return handleSeekKey(players, -10000);
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return handleSeekKey(players, 10000);
            case KeyEvent.KEYCODE_DPAD_UP:
                return handleVolumeKey(1);
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return handleVolumeKey(-1);
        }
        return false;
    }

    private boolean handleSeekKey(Players players, long seekTime) {
        if (players.isPlaying() || players.getPosition() > 0) {
            long currentPosition = players.getPosition();
            long newPosition = currentPosition + seekTime;
            if (newPosition < 0) newPosition = 0;
            long duration = players.getDuration();
            if (duration > 0 && newPosition > duration) newPosition = duration;
            players.seekTo(newPosition);
            onSeek(seekTime);
            App.post(() -> mBinding.widget.seek.setVisibility(android.view.View.GONE), 1000);
            return true;
        }
        return false;
    }

    private boolean handleVolumeKey(int delta) {
        if (mAudioManager != null) {
            int currentVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            int maxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int newVolume = Math.max(0, Math.min(maxVolume, currentVolume + delta));
            mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0);
            onVolume((int) (newVolume * 100.0f / maxVolume));
            App.post(this::onVolumeEnd, 1000);
            return true;
        }
        return false;
    }

    // ==================== Seek callbacks (from CustomKeyDownVod) ====================

    public void onSeek(long time) {
        mBinding.widget.action.setImageResource(time > 0 ? R.drawable.ic_widget_forward : R.drawable.ic_widget_rewind);
        mBinding.widget.time.setText(mPlayerController.getPlayers().getPositionTime(time));
        mBinding.widget.seek.setVisibility(android.view.View.VISIBLE);
        mBinding.widget.progress.setVisibility(android.view.View.GONE);
    }

    public void onSeekEnd(long time) {
        handleLandscapeSeek(time);
    }

    private void handleLandscapeSeek(long time) {
        Players players = mPlayerController.getPlayers();
        boolean isLandscape = mBinding.getRoot().getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        long delay = isLandscape ? 150 : 100;

        mBinding.widget.seek.setVisibility(android.view.View.GONE);
        mBinding.widget.progress.setVisibility(android.view.View.VISIBLE);
        players.pause();
        players.seek(time);
        App.post(() -> {
            long actualPosition = players.getPosition();
            if (Math.abs(actualPosition - time) > 500) {
                players.seek(time);
            }
            mPlayerController.play();
            mBinding.widget.progress.setVisibility(android.view.View.GONE);
        }, delay);
    }

    // ==================== Brightness ====================

    public void onBright(int progress) {
        mBinding.widget.bright.setVisibility(android.view.View.VISIBLE);
        mBinding.widget.brightProgress.setProgress(progress);
        if (progress < 35) mBinding.widget.brightIcon.setImageResource(R.drawable.ic_widget_bright_low);
        else if (progress < 70) mBinding.widget.brightIcon.setImageResource(R.drawable.ic_widget_bright_medium);
        else mBinding.widget.brightIcon.setImageResource(R.drawable.ic_widget_bright_high);
    }

    public void onBrightEnd() {
        mBinding.widget.bright.setVisibility(android.view.View.GONE);
    }

    // ==================== Volume ====================

    public void onVolume(int progress) {
        mBinding.widget.volume.setVisibility(android.view.View.VISIBLE);
        mBinding.widget.volumeProgress.setProgress(progress);
        if (progress < 35) mBinding.widget.volumeIcon.setImageResource(R.drawable.ic_widget_volume_low);
        else if (progress < 70) mBinding.widget.volumeIcon.setImageResource(R.drawable.ic_widget_volume_medium);
        else mBinding.widget.volumeIcon.setImageResource(R.drawable.ic_widget_volume_high);
    }

    public void onVolumeEnd() {
        mBinding.widget.volume.setVisibility(android.view.View.GONE);
    }

    // ==================== Fling ====================

    public void onFlingUp() {
        mPlayerController.playNext();
    }

    public void onFlingDown() {
        mPlayerController.playPrev();
    }

    // ==================== Tap ====================

    public void onSingleTap(ControlVisibility controlVisibility) {
        if (controlVisibility.isVisible()) {
            controlVisibility.hide();
        } else {
            controlVisibility.show();
        }
    }

    public void onDoubleTap(Runnable enterFullscreen) {
        enterFullscreen.run();
    }

    public void onDoubleTapLeft() {
        long seekTime = -10000;
        Players players = mPlayerController.getPlayers();
        long newPosition = Math.max(0, players.getPosition() + seekTime);
        players.seekTo(newPosition);
        onSeek(seekTime);
        App.post(() -> mBinding.widget.seek.setVisibility(android.view.View.GONE), 800);
    }

    public void onDoubleTapRight() {
        long seekTime = 10000;
        Players players = mPlayerController.getPlayers();
        long duration = players.getDuration();
        long newPosition = Math.min(duration > 0 ? duration : Long.MAX_VALUE, players.getPosition() + seekTime);
        players.seekTo(newPosition);
        onSeek(seekTime);
        App.post(() -> mBinding.widget.seek.setVisibility(android.view.View.GONE), 800);
    }

    // ==================== Orientation ====================

    public void onLandscapeMode() {
        Players players = mPlayerController.getPlayers();
        if (players != null) {
            long duration = players.getDuration();
            if (duration > 0) {
                if (duration > TimeUnit.MINUTES.toMillis(30)) {
                    mBinding.control.seek.setKeyTimeIncrement(TimeUnit.MINUTES.toMillis(1));
                } else if (duration > TimeUnit.MINUTES.toMillis(10)) {
                    mBinding.control.seek.setKeyTimeIncrement(TimeUnit.SECONDS.toMillis(30));
                } else {
                    mBinding.control.seek.setKeyTimeIncrement(TimeUnit.SECONDS.toMillis(15));
                }
            }
            long position = players.getPosition();
            if (position > 0 && duration > 0) {
                mBinding.control.seek.setPosition(position);
                mBinding.control.seek.setDuration(duration);
            }
        }
    }

    public void onPortraitMode() {
        Players players = mPlayerController.getPlayers();
        if (players != null) {
            long duration = players.getDuration();
            if (duration > 0) {
                mBinding.control.seek.setKeyTimeIncrement(duration);
            }
        }
    }

    // ==================== Interface ====================

    public interface ControlVisibility {
        boolean isVisible();
        void show();
        void hide();
    }
}