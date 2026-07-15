package com.fongmi.android.tv.player;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

import androidx.activity.ComponentActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Url;
import com.fongmi.android.tv.databinding.ActivityVideoBinding;
import com.fongmi.android.tv.ui.activity.DownloadActivity;
import com.fongmi.android.tv.ui.adapter.EpisodeAdapter;
import com.fongmi.android.tv.ui.adapter.FlagAdapter;
import com.fongmi.android.tv.ui.dialog.DownloadEpisodeDialog;
import com.fongmi.android.tv.utils.Downloader;
import com.fongmi.android.tv.utils.Notify;
import com.github.catvod.utils.Logger;
import com.permissionx.guolindev.PermissionX;

import java.util.List;

public class DownloadController {

    private final FragmentActivity mActivity;
    private final ActivityVideoBinding mBinding;
    private final Players mPlayers;
    private final FlagAdapter mFlagAdapter;
    private final EpisodeAdapter mEpisodeAdapter;

    public DownloadController(FragmentActivity activity, ActivityVideoBinding binding, Players players, FlagAdapter flagAdapter, EpisodeAdapter episodeAdapter) {
        this.mActivity = activity;
        this.mBinding = binding;
        this.mPlayers = players;
        this.mFlagAdapter = flagAdapter;
        this.mEpisodeAdapter = episodeAdapter;
    }

    public void onDownload() {
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
        String title = getVideoTitle();
        String pic = getVideoPic();
        Downloader.get().title(title).image(pic).start(mActivity, url, mPlayers.getHeaders());
    }

    private void downloadMultiEpisodes(Flag flag, List<Episode> episodeList) {
        DownloadEpisodeDialog.create(episodeList).listener(selected -> {
            String vodName = mBinding.name.getText().toString();
            String pic = getVideoPic();
            App.execute(() -> {
                for (Episode episode : selected) {
                    String episodeUrl = episode.getUrl();
                    if (episodeUrl == null || episodeUrl.isEmpty()) continue;
                    String episodeTitle = getEpisodeTitle(vodName, episode);
                    String resolvedUrl = resolveEpisodeUrl(episodeUrl, flag);
                    if (resolvedUrl == null || resolvedUrl.isEmpty()) {
                        Logger.e("DownloadMulti: resolveURL failed: " + episodeUrl);
                        continue;
                    }
                    String finalEpisodeTitle = episodeTitle;
                    App.post(() -> Downloader.get().title(finalEpisodeTitle).image(pic).start(mActivity, resolvedUrl, mPlayers.getHeaders()));
                }
            });
        }).show(mActivity);
    }

    private String resolveEpisodeUrl(String episodeUrl, Flag flag) {
        try {
            Site site = VodConfig.get().getSite(mActivity.getIntent().getStringExtra("key"));
            if (site.getType() == 3) {
                com.github.catvod.crawler.Spider spider = site.recent().spider();
                String content = spider.playerContent(flag.getFlag(), episodeUrl, VodConfig.get().getFlags());
                Result result = Result.fromJson(content);
                if (result.getFlag().isEmpty()) result.setFlag(flag.getFlag());
                result.setUrl(Source.get().fetch(result));
                result.setHeader(site.getHeader());
                return result.getRealUrl();
            } else {
                Url url = Url.create().add(episodeUrl);
                Result result = new Result();
                result.setUrl(url);
                result.setFlag(flag.getFlag());
                result.setHeader(site.getHeader());
                result.setPlayUrl(site.getPlayUrl());
                result.setUrl(Source.get().fetch(result));
                return result.getRealUrl();
            }
        } catch (Exception e) {
            Logger.e("DownloadMulti: resolveURL failed: " + episodeUrl, e);
            return null;
        }
    }

    public void onDownloadMgr() {
        if (!checkStoragePermission()) {
            Notify.show(R.string.error_permission_storage);
            requestStoragePermission();
            return;
        }
        App.post(() -> DownloadActivity.start(mActivity), 200);
    }

    private boolean checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            return ContextCompat.checkSelfPermission(mActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(mActivity)
                    .setTitle(R.string.download_permission_title)
                    .setMessage(R.string.download_permission_msg)
                    .setPositiveButton(R.string.download_permission_settings, (d, w) -> {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                        intent.setData(Uri.parse("package:" + mActivity.getPackageName()));
                        mActivity.startActivity(intent);
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        } else {
            PermissionX.init(mActivity).permissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    .request((allGranted, grantedList, deniedList) -> {
                        if (allGranted) {
                            Notify.show(R.string.download_permission_granted);
                        } else {
                            Notify.show(R.string.download_permission_denied);
                        }
                    });
        }
    }

    private String getVideoTitle() {
        String title = mBinding.name.getText().toString();
        Episode episode = getEpisode();
        if (episode != null && episode.getName() != null && !episode.getName().isEmpty()) {
            title = title + " " + episode.getName();
        }
        return title;
    }

    private String getEpisodeTitle(String baseTitle, Episode episode) {
        if (episode.getName() != null && !episode.getName().isEmpty()) {
            return baseTitle + " " + episode.getName();
        }
        return baseTitle;
    }

    private String getVideoPic() {
        Object tag = mBinding.video.getTag();
        return tag != null ? tag.toString() : "";
    }

    private Flag getFlag() {
        return mFlagAdapter.getActivated();
    }

    private Episode getEpisode() {
        return mEpisodeAdapter.getActivated();
    }
}
