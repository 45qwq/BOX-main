package com.fongmi.android.tv.player;

import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.CastMember;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.ActivityVideoBinding;
import com.fongmi.android.tv.ui.adapter.EpisodeAdapter;
import com.fongmi.android.tv.ui.adapter.FlagAdapter;
import com.fongmi.android.tv.ui.adapter.ParseAdapter;
import com.fongmi.android.tv.ui.adapter.QualityAdapter;
import com.fongmi.android.tv.ui.adapter.QuickAdapter;
import com.fongmi.android.tv.ui.base.ViewType;
import com.fongmi.android.tv.ui.custom.CustomMovement;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.CastUtil;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Sniffer;
import com.fongmi.android.tv.utils.Util;
import com.fongmi.android.tv.ui.activity.FolderActivity;
import com.fongmi.android.tv.bean.Url;
import com.github.bassaer.library.MDColor;
import com.github.catvod.utils.Trans;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

public class EpisodeManager {

    private final ActivityVideoBinding mBinding;
    private EpisodeAdapter mEpisodeAdapter;
    private FlagAdapter mFlagAdapter;
    private QualityAdapter mQualityAdapter;
    private QuickAdapter mQuickAdapter;
    private ParseAdapter mParseAdapter;
    private History mHistory;
    private boolean mActorExpanded;

    public EpisodeManager(ActivityVideoBinding binding) {
        this.mBinding = binding;
    }

    public void setDependencies(History history) {
        this.mHistory = history;
    }

    public void setHistory(History history) {
        this.mHistory = history;
    }

    public History getHistory() {
        return mHistory;
    }

    public FlagAdapter getFlagAdapter() {
        return mFlagAdapter;
    }

    public EpisodeAdapter getEpisodeAdapter() {
        return mEpisodeAdapter;
    }

    public QualityAdapter getQualityAdapter() {
        return mQualityAdapter;
    }

    public QuickAdapter getQuickAdapter() {
        return mQuickAdapter;
    }

    public ParseAdapter getParseAdapter() {
        return mParseAdapter;
    }

    // ==================== Adapter initialization ====================

    public void initAdapters(
            FlagAdapter.OnClickListener flagListener,
            EpisodeAdapter.OnClickListener episodeListener,
            QualityAdapter.OnClickListener qualityListener,
            QuickAdapter.OnClickListener quickListener,
            ParseAdapter.OnClickListener parseListener) {
        mBinding.flag.setHasFixedSize(true);
        mBinding.flag.setItemAnimator(null);
        mBinding.flag.addItemDecoration(new SpaceItemDecoration(8));
        mBinding.flag.setAdapter(mFlagAdapter = new FlagAdapter(flagListener));

        mBinding.quick.setAdapter(mQuickAdapter = new QuickAdapter(quickListener));

        mBinding.episode.setHasFixedSize(true);
        mBinding.episode.setItemAnimator(null);
        mBinding.episode.addItemDecoration(new SpaceItemDecoration(8));
        mBinding.episode.setAdapter(mEpisodeAdapter = new EpisodeAdapter(episodeListener, ViewType.HORI));

        mBinding.quality.setHasFixedSize(true);
        mBinding.quality.setItemAnimator(null);
        mBinding.quality.addItemDecoration(new SpaceItemDecoration(8));
        mBinding.quality.setAdapter(mQualityAdapter = new QualityAdapter(qualityListener));

        mBinding.control.parse.setHasFixedSize(true);
        mBinding.control.parse.setItemAnimator(null);
        mBinding.control.parse.addItemDecoration(new SpaceItemDecoration(8));
        mBinding.control.parse.setAdapter(mParseAdapter = new ParseAdapter(parseListener, ViewType.DARK));
    }

    // ==================== Detail display ====================

    public void setDetail(Vod item, String pic, String name, String siteName) {
        mActorExpanded = false;
        mBinding.downloadRow.setVisibility(item == null || item.getVodFlags().isEmpty() ? View.GONE : View.VISIBLE);
        item.ensureVodPic(pic);
        item.ensureVodName(name);
        mBinding.video.setTag(item.getVodPic());
        mBinding.name.setText(item.getVodName());
        setText(mBinding.remark, 0, item.getVodRemarks());
        setText(mBinding.site, R.string.detail_site, siteName);
        setText(mBinding.content, 0, Html.fromHtml(item.getVodContent()).toString());
        setActorText(mBinding.actor, R.string.detail_actor, item.getVodActor(), CastMember.CastType.ACTOR);
        setActorText(mBinding.director, R.string.detail_director, item.getVodDirector(), CastMember.CastType.DIRECTOR);
        mBinding.contentLayout.setVisibility(mBinding.content.getVisibility());
        mFlagAdapter.addAll(item.getVodFlags());
        setOther(mBinding.other, item);
    }

    public void setEpisodeAdapter(List<Episode> items) {
        mBinding.control.action.episodes.setVisibility(items.size() < 2 ? View.GONE : View.VISIBLE);
        mBinding.control.nextRoot.setVisibility(items.size() < 2 ? View.GONE : View.VISIBLE);
        mBinding.control.prevRoot.setVisibility(items.size() < 2 ? View.GONE : View.VISIBLE);
        mBinding.episode.setVisibility(items.size() == 0 ? View.GONE : View.VISIBLE);
        mBinding.reverse.setVisibility(items.size() < 2 ? View.GONE : View.VISIBLE);
        mBinding.more.setVisibility(items.size() < 10 ? View.GONE : View.VISIBLE);
        mEpisodeAdapter.addAll(items);
    }

    public void setQualityVisible(boolean visible) {
        mBinding.qualityText.setVisibility(visible ? View.VISIBLE : View.GONE);
        mBinding.quality.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public void setQualityVisible(Result result) {
        setQualityVisible(result.getUrl().isMulti());
    }

    public void setQualityAdapter(Result result) {
        mQualityAdapter.addAll(result);
    }

    // ==================== Flag / Episode ====================

    public void onFlagClick(Flag item) {
        if (item.isActivated()) return;
        mFlagAdapter.setActivated(item);
        mBinding.flag.scrollToPosition(mFlagAdapter.getPosition());
        setEpisodeAdapter(item.getEpisodes());
        setQualityVisible(false);
    }

    public Episode seamless(Flag flag, String vodRemarks, boolean noMark) {
        Episode episode = flag.find(vodRemarks, noMark);
        if (episode == null || episode.isActivated()) return null;
        setQualityVisible(episode != null && episode.isActivated() && mQualityAdapter.getItemCount() > 1);
        return episode;
    }

    public void reverseEpisode(boolean scroll) {
        mFlagAdapter.reverse();
        setEpisodeAdapter(mFlagAdapter.getActivated().getEpisodes());
        if (scroll) mBinding.episode.scrollToPosition(mEpisodeAdapter.getPosition());
    }

    public void updateHistoryEpisode(History history, Episode item, boolean replay, String flag) {
        replay = replay || !item.equals(history.getEpisode());
        history.setEpisodeUrl(item.getUrl());
        history.setVodRemarks(item.getName());
        history.setVodFlag(flag);
        history.setCreateTime(System.currentTimeMillis());
        history.setPosition(replay ? androidx.media3.common.C.TIME_UNSET : history.getPosition());
    }

    public void onParseClick(Parse item) {
        setParse(item);
        mParseAdapter.notifyItemRangeChanged(0, mParseAdapter.getItemCount());
    }

    public void setParse(Parse item) {
        com.fongmi.android.tv.api.config.VodConfig.get().setParse(item);
    }

    public void notifyAdapters() {
        if (mEpisodeAdapter != null) mEpisodeAdapter.notifyItemRangeChanged(0, mEpisodeAdapter.getItemCount());
        if (mFlagAdapter != null) mFlagAdapter.notifyItemRangeChanged(0, mFlagAdapter.getItemCount());
    }

    // ==================== Search result ====================

    public void setSearchResult(Result result, String id, String keyword, boolean autoMode, boolean initAuto, Runnable onEmptyRunnable) {
        List<Vod> items = result.getList();
        java.util.Iterator<Vod> iterator = items.iterator();
        while (iterator.hasNext()) if (mismatch(iterator.next(), id, keyword, autoMode)) iterator.remove();
        mBinding.quick.setVisibility(View.VISIBLE);
        mQuickAdapter.addAll(items);
        if (initAuto) {
            // nextSite will be called by activity
        }
        if (items.isEmpty()) return;
        if (onEmptyRunnable != null) App.removeCallbacks(onEmptyRunnable);
    }

    private boolean mismatch(Vod item, String id, String keyword, boolean autoMode) {
        if (id.equals(item.getVodId())) return true;
        if (autoMode) return !item.getVodName().equals(keyword);
        else return !item.getVodName().contains(keyword);
    }

    // ==================== Text helpers ====================

    private void setText(TextView view, int resId, String text) {
        if (text == null) text = "";
        view.setText(getSpan(resId, text), TextView.BufferType.SPANNABLE);
        view.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
        view.setLinkTextColor(MDColor.YELLOW_500);
        CustomMovement.bind(view);
        view.setTag(text);
    }

    private SpannableStringBuilder getSpan(int resId, String text) {
        if (resId > 0) text = mBinding.getRoot().getContext().getString(resId, text);
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

    private android.text.style.ClickableSpan getClickSpan(Result result) {
        return new android.text.style.ClickableSpan() {
            @Override
            public void onClick(@NonNull View view) {
                // FolderActivity starts - this needs to be handled by the activity
                ((TextView) view).setMaxLines(Integer.MAX_VALUE);
            }
        };
    }

    private void setOther(TextView view, Vod item) {
        StringBuilder sb = new StringBuilder();
        if (!item.getVodYear().isEmpty()) sb.append(mBinding.getRoot().getContext().getString(R.string.detail_year, item.getVodYear())).append("  ");
        if (!item.getVodArea().isEmpty()) sb.append(mBinding.getRoot().getContext().getString(R.string.detail_area, item.getVodArea())).append("  ");
        if (!item.getTypeName().isEmpty()) sb.append(mBinding.getRoot().getContext().getString(R.string.detail_type, item.getTypeName())).append("  ");
        view.setVisibility(sb.length() == 0 ? View.GONE : View.VISIBLE);
        view.setText(Util.substring(sb.toString(), 2));
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
        String label = mBinding.getRoot().getContext().getString(resId, "");
        SpannableStringBuilder span = new SpannableStringBuilder(label);
        for (int i = 0; i < members.size(); i++) {
            CastMember member = members.get(i);
            int start = span.length();
            span.append(member.getName());
            int end = span.length();
            span.setSpan(getCastClickSpan(member), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (i < members.size() - 1) span.append(" / ");
        }
        view.setText(span, TextView.BufferType.SPANNABLE);
        view.setMaxLines(1);
        view.setVisibility(View.VISIBLE);
        view.setLinkTextColor(MDColor.YELLOW_500);
        view.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private android.text.style.ClickableSpan getCastClickSpan(CastMember member) {
        return new android.text.style.ClickableSpan() {
            @Override
            public void onClick(@NonNull View view) {
                // CastWorksActivity start - handled by activity
            }
            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                super.updateDrawState(ds);
                ds.setUnderlineText(false);
            }
        };
    }
}