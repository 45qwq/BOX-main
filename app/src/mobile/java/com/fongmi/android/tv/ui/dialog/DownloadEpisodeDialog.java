package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.ui.adapter.DownloadEpisodeAdapter;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;

public class DownloadEpisodeDialog extends BottomSheetDialogFragment {

    private List<Episode> episodes;
    private List<Episode> selectedEpisodes;
    private RecyclerView episodeRecycler;
    private DownloadEpisodeAdapter adapter;
    private OnDownloadListener listener;
    private TextView downloadBtn;

    public interface OnDownloadListener {
        void onDownload(List<Episode> selectedEpisodes);
    }

    public static DownloadEpisodeDialog create(List<Episode> episodes) {
        DownloadEpisodeDialog dialog = new DownloadEpisodeDialog();
        dialog.episodes = new ArrayList<>(episodes);
        return dialog;
    }

    public DownloadEpisodeDialog episodes(List<Episode> episodes) {
        this.episodes = new ArrayList<>(episodes);
        return this;
    }

    public DownloadEpisodeDialog listener(OnDownloadListener listener) {
        this.listener = listener;
        return this;
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) {
            if (f instanceof BottomSheetDialogFragment) return;
        }
        show(activity.getSupportFragmentManager(), null);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_download_episode, null);

        // 初始化时默认选中当前集（如果有）
        for (Episode ep : episodes) {
            ep.setSelected(ep.isActivated());
        }

        episodeRecycler = view.findViewById(R.id.episodeRecycler);
        episodeRecycler.setLayoutManager(new GridLayoutManager(getContext(), 4));
        adapter = new DownloadEpisodeAdapter(episodes, (item, position) -> {
            item.setSelected(!item.isSelected());
            adapter.notifyItemChanged(position);
            updateDownloadButtonText();
        });
        episodeRecycler.setAdapter(adapter);

        view.findViewById(R.id.selectAll).setOnClickListener(v -> selectAll());
        view.findViewById(R.id.deselectAll).setOnClickListener(v -> deselectAll());
        view.findViewById(R.id.invertSelect).setOnClickListener(v -> invertSelect());

        downloadBtn = view.findViewById(R.id.downloadBtn);
        downloadBtn.setOnClickListener(v -> onDownloadClick());
        updateDownloadButtonText();

        AlertDialog dialog = new AlertDialog.Builder(getContext(), R.style.ThemeOverlay_AppCompat_Dialog)
                .setView(view)
                .setCancelable(true)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        return dialog;
    }

    private void selectAll() {
        for (Episode ep : episodes) ep.setSelected(true);
        adapter.notifyDataSetChanged();
        updateDownloadButtonText();
    }

    private void deselectAll() {
        for (Episode ep : episodes) ep.setSelected(false);
        adapter.notifyDataSetChanged();
        updateDownloadButtonText();
    }

    private void invertSelect() {
        for (Episode ep : episodes) ep.setSelected(!ep.isSelected());
        adapter.notifyDataSetChanged();
        updateDownloadButtonText();
    }

    private void updateDownloadButtonText() {
        int count = 0;
        for (Episode ep : episodes) {
            if (ep.isSelected()) count++;
        }
        if (count > 0) {
            downloadBtn.setText(getString(R.string.download_selected_with_count, count));
        } else {
            downloadBtn.setText(R.string.download_selected);
        }
    }

    private void onDownloadClick() {
        selectedEpisodes = new ArrayList<>();
        for (Episode ep : episodes) {
            if (ep.isSelected()) selectedEpisodes.add(ep);
        }
        if (selectedEpisodes.isEmpty()) return;
        if (listener != null) {
            listener.onDownload(selectedEpisodes);
        }
        dismiss();
    }
}