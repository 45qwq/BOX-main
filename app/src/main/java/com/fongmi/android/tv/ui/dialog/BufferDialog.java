package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.databinding.DialogBufferBinding;
import com.fongmi.android.tv.impl.BufferCallback;

public class BufferDialog extends BaseDialog implements View.OnClickListener {

    private DialogBufferBinding mBinding;
    private BufferCallback mCallback;
    private int mValue;

    public static BufferDialog create(BufferCallback callback) {
        return new BufferDialog().setCallback(callback);
    }

    public BufferDialog setCallback(BufferCallback callback) {
        this.mCallback = callback;
        return this;
    }

    public void show(FragmentActivity activity) {
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = DialogBufferBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        mBinding.slider.setValue(mValue = Setting.getBuffer());
    }

    @Override
    protected void initEvent() {
        mBinding.positive.setOnClickListener(this);
        mBinding.negative.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.positive) {
            mCallback.setBuffer((int) mBinding.slider.getValue());
        } else {
            mCallback.setBuffer(mValue);
        }
        dismiss();
    }
}