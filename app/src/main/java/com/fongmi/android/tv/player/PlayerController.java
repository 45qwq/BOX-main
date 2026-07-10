package com.fongmi.android.tv.player;

import static androidx.media3.common.Player.COMMAND_SET_SPEED_AND_PITCH;

import android.content.res.Configuration;
import android.os.Handler;
import android.os.Looper;

import androidx.media3.common.C;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.databinding.ActivityVideoBinding;
import com.fongmi.android.tv.event.PlayerEvent;
import com.fongmi.android.tv.ui.adapter.EpisodeAdapter;
import com.fongmi.android.tv.ui.adapter.FlagAdapter;
import com.fongmi.android.tv.ui.adapter.ParseAdapter;
import com.fongmi.android.tv.ui.adapter.QualityAdapter;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.concurrent.TimeUnit;

public class PlayerController {

    private final Players mPlayers;
    private final ActivityVideoBinding mBinding;
    private final Runnable mR1;
    private final Handler mHandler;
    private History mHistory;
    private FlagAdapter mFlagAdapter;
    private EpisodeAdapter mEpisodeAdapter;
    private QualityAdapter mQualityAdapter;
    private ParseAdapter mParseAdapter;
    private String tag;
    private boolean loop;
    private Runnable mOnEpisodeSwitch;

    public PlayerController(Players players, ActivityVideoBinding binding, Runnable hideControlRunnable) {
        this.mPlayers = players;
        this.mBinding = binding;
        this.mR1 = hideControlRunnable;
        this.mHandler = new Handler(Looper.getMainLooper());
    }

    public void setDependencies(History history, FlagAdapter flagAdapter, EpisodeAdapter episodeAdapter, QualityAdapter qualityAdapter, ParseAdapter parseAdapter, String tag) {
        this.mHistory = history;
        this.mFlagAdapter = flagAdapter;
        this.mEpisodeAdapter = episodeAdapter;
        this.mQualityAdapter = qualityAdapter;
        this.mParseAdapter = parseAdapter;
        this.tag = tag;
    }

    public void setHistory(History history) {
        this.mHistory = history;
    }

    public History getHistory() {
        return mHistory;
    }

    public Players getPlayers() {
        return mPlayers;
    }

    // ==================== Playback control ====================

    public void play() {
        if (mHistory != null && mPlayers.isEnded()) mPlayers.seekTo(mHistory.getOpening());
        if (!mPlayers.isEmpty() && mPlayers.isIdle()) mPlayers.prepare();
        mPlayers.play();
        checkPlayImg();
        if (mBinding.dim != null) {
            mBinding.dim.setVisibility(android.view.View.GONE);
        }
    }

    public void pause() {
        mPlayers.pause();
        checkPlayImg();
        if (mBinding.dim != null) {
            mBinding.dim.setVisibility(android.view.View.VISIBLE);
        }
    }

    public void togglePlay() {
        if (mPlayers.isPlaying()) pause();
        else if (mPlayers.isEmpty()) refresh();
        else play();
    }

    public boolean playNext() {
        // 防御性检查：mEpisodeAdapter 可能在依赖未设置时为 null（如详情数据未加载完成时用户上滑）
        if (mEpisodeAdapter == null || mEpisodeAdapter.isEmpty()) return false;
        Episode item = mEpisodeAdapter.getNext();
        if (item == null) return false;
        if (!item.isActivated()) {
            onItemClick(item);
            return true;
        }
        return false;
    }

    public boolean playPrev() {
        // 防御性检查：同 playNext
        if (mEpisodeAdapter == null || mEpisodeAdapter.isEmpty()) return false;
        Episode item = mEpisodeAdapter.getPrev();
        if (item == null) return false;
        if (!item.isActivated()) {
            onItemClick(item);
            return true;
        }
        return false;
    }

    public void refresh() {
        reset(false);
    }

    public void reset(boolean replay) {
        mPlayers.stop();
        mPlayers.clear();
        mHandler.post(() -> {
            // 重置需要在主线程触发的回调
            if (mFlagAdapter == null || mFlagAdapter.isEmpty()) return;
            if (mEpisodeAdapter == null || mEpisodeAdapter.isEmpty()) return;
            Flag flag = mFlagAdapter.getActivated();
            Episode episode = mEpisodeAdapter.getActivated();
            if (flag != null && episode != null) {
                getPlayer(flag, episode, replay);
            }
        });
    }

    public void stop() {
        mPlayers.stop();
    }

    // ==================== Seek ====================

    public void seek(long time) {
        if (mPlayers.isPlaying() || mPlayers.getPosition() > 0) {
            mPlayers.seek(time);
        }
    }

    public void seekTo(long position) {
        if (mPlayers.isPlaying() || mPlayers.getPosition() > 0) {
            long newPosition = Math.max(0, position);
            if (mPlayers.getDuration() > 0) {
                newPosition = Math.min(mPlayers.getDuration(), newPosition);
            }
            mPlayers.seekTo(newPosition);
        }
    }

    public void setPosition() {
        if (mHistory != null) mPlayers.seekTo(Math.max(mHistory.getOpening(), mHistory.getPosition()));
    }

    // ==================== Speed ====================

    public String addSpeed() {
        String text = mPlayers.addSpeed();
        if (mHistory != null) mHistory.setSpeed(mPlayers.getSpeed());
        return text;
    }

    public String toggleSpeed() {
        String text = mPlayers.toggleSpeed();
        if (mHistory != null) mHistory.setSpeed(mPlayers.getSpeed());
        return text;
    }

    public void setSpeed(float speed) {
        if (mHistory != null) mHistory.setSpeed(speed);
        mBinding.control.action.speed.setText(mPlayers.setSpeed(speed));
    }

    public void onSpeedUp() {
        if (!mPlayers.isPlaying()) return;
        mBinding.control.action.speed.setText(mPlayers.setSpeed(Setting.getSpeed()));
        mBinding.widget.speed.startAnimation(ResUtil.getAnim(R.anim.forward));
        mBinding.widget.speed.setVisibility(android.view.View.VISIBLE);
    }

    public void onSpeedEnd() {
        mBinding.control.action.speed.setText(mPlayers.setSpeed(mHistory != null ? mHistory.getSpeed() : 1.0f));
        mBinding.widget.speed.setVisibility(android.view.View.GONE);
        mBinding.widget.speed.clearAnimation();
    }

    // ==================== Op/Ed ====================

    public void onEnding() {
        long current = mPlayers.getPosition();
        long duration = mPlayers.getDuration();
        if (current < 0 || duration < 0) return;
        if (duration - current > Constant.OPED_LIMIT) return;
        setEnding(duration - current);
    }

    public void onOpening() {
        long current = mPlayers.getPosition();
        long duration = mPlayers.getDuration();
        if (current < 0 || duration < 0) return;
        if (current > Constant.OPED_LIMIT) return;
        setOpening(current);
    }

    public void setEnding(long ending) {
        if (mHistory != null) mHistory.setEnding(ending);
        mBinding.control.action.ending.setText(ending <= 0
                ? mBinding.getRoot().getContext().getString(R.string.play_ed)
                : mPlayers.stringToTime(mHistory != null ? mHistory.getEnding() : ending));
    }

    public void setOpening(long opening) {
        if (mHistory != null) mHistory.setOpening(opening);
        mBinding.control.action.opening.setText(opening <= 0
                ? mBinding.getRoot().getContext().getString(R.string.play_op)
                : mPlayers.stringToTime(mHistory != null ? mHistory.getOpening() : opening));
    }

    public boolean isLoopEnabled() {
        return loop;
    }

    public void toggleLoop() {
        loop = !loop;
        mBinding.control.action.loop.setActivated(loop);
    }

    // ==================== Decode ====================

    public void toggleDecode() {
        mPlayers.toggleDecode();
        mBinding.control.action.decode.setText(mPlayers.getDecodeText());
    }

    // ==================== Scale ====================

    public void setScale(int scale) {
        if (mHistory != null) mHistory.setScale(scale);
        mBinding.exo.setResizeMode(scale);
        mBinding.control.action.scale.setText(ResUtil.getStringArray(R.array.select_scale)[scale]);
    }

    public void onScale(int tag) {
        // Called from outside - set scale by tag
    }

    // ==================== Metadata ====================

    public void setMetadata() {
        String title = mHistory != null ? mHistory.getVodName() : "";
        Episode episode = mEpisodeAdapter != null ? mEpisodeAdapter.getActivated() : null;
        String episodeName = episode != null ? episode.getName() : "";
        String artist = episodeName.isEmpty() || title.equals(episodeName) ? "" : mBinding.getRoot().getContext().getString(R.string.play_now, episodeName);
        mPlayers.setMetadata(title, artist, mHistory != null ? mHistory.getVodPic() : "", mBinding.exo.getDefaultArtwork());
    }

    public void setTrackVisible() {
        mBinding.control.action.text.setVisibility(mPlayers.haveTrack(C.TRACK_TYPE_TEXT) || mPlayers.isVod() ? android.view.View.VISIBLE : android.view.View.GONE);
        mBinding.control.action.audio.setVisibility(mPlayers.haveTrack(C.TRACK_TYPE_AUDIO) ? android.view.View.VISIBLE : android.view.View.GONE);
        mBinding.control.action.video.setVisibility(mPlayers.haveTrack(C.TRACK_TYPE_VIDEO) ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    public void setDecodeText() {
        mBinding.control.action.decode.setText(mPlayers.getDecodeText());
    }

    // ==================== Player init ====================

    public void getPlayer(Flag flag, Episode episode, boolean replay) {
        mBinding.control.title.setText(episode.getName());
        mBinding.control.title.setSelected(true);
        // updateHistory and other logic handled by activity
    }

    // ==================== End handling ====================

    public void checkEnded(boolean notify, EndHandler endHandler) {
        if (loop) {
            reset(true);
        } else {
            if (endHandler != null) endHandler.onEnded(notify);
            checkPlayImg();
        }
    }

    public void checkOrientation(OrientationCallback callback) {
        // Orientation check delegated
    }

    // ==================== UI helpers ====================

    public void checkPlayImg() {
        mBinding.control.play.setImageResource(mPlayers.isPlaying()
                ? androidx.media3.ui.R.drawable.exo_icon_pause
                : androidx.media3.ui.R.drawable.exo_icon_play);
    }

    public void setOnEpisodeSwitch(Runnable runnable) {
        mOnEpisodeSwitch = runnable;
    }

    public void onItemClick(Episode item) {
        // Delegate to flag adapter
        if (mFlagAdapter != null) mFlagAdapter.toggle(item);
        if (mEpisodeAdapter != null) {
            mEpisodeAdapter.notifyItemRangeChanged(0, mEpisodeAdapter.getItemCount());
        }
        // 触发播放切换回调（上一集/下一集时启动播放）
        if (mOnEpisodeSwitch != null) mOnEpisodeSwitch.run();
    }

    public interface EndHandler {
        void onEnded(boolean notify);
    }

    public interface OrientationCallback {
        void onOrientationChanged(boolean isPortrait);
    }
}