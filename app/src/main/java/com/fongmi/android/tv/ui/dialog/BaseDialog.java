package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public abstract class BaseDialog extends BottomSheetDialogFragment {

    protected ActivityResultLauncher<Intent> pickLauncher;

    protected abstract ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return getBinding(inflater, container).getRoot();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pickLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) onPickFile(result.getData().getData());
        });
    }

    /** 子类覆写以接收文件选择结果（原 REQUEST_PICK_FILE 行为）。默认空实现。 */
    protected void onPickFile(android.net.Uri uri) {
    }

    public ActivityResultLauncher<Intent> getPickLauncher() {
        return pickLauncher;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        initView();
        initEvent();
    }

    protected void initView() {
    }

    protected void initEvent() {
    }

    protected boolean transparent() {
        return false;
    }

    protected void setDimAmount(float amount) {
        getDialog().getWindow().setDimAmount(amount);
        getDialog().getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        dialog.setOnShowListener((DialogInterface f) -> setBehavior(dialog));
        return dialog;
    }

    private void setBehavior(BottomSheetDialog dialog) {
        FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (transparent()) bottomSheet.setBackgroundColor(ResUtil.getColor(R.color.transparent));
        BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        behavior.setSkipCollapsed(true);
    }
}
