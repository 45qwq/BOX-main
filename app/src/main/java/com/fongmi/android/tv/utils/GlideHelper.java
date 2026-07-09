package com.fongmi.android.tv.utils;

import android.graphics.Bitmap;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.fongmi.android.tv.R;

public class GlideHelper {

    public static RequestListener<Bitmap> getBitmapListener(ImageView view, ImageView.ScaleType scaleType) {
        return new RequestListener<Bitmap>() {
            @Override
            public boolean onLoadFailed(@Nullable GlideException e, Object model, @NonNull Target<Bitmap> target, boolean isFirstResource) {
                view.setImageResource(R.drawable.ic_img_error);
                view.setScaleType(scaleType);
                return true;
            }

            @Override
            public boolean onResourceReady(@NonNull Bitmap resource, @NonNull Object model, @NonNull Target<Bitmap> target, @NonNull DataSource dataSource, boolean isFirstResource) {
                view.setScaleType(ImageView.ScaleType.CENTER_CROP);
                return false;
            }
        };
    }

    public static RequestListener<Bitmap> getBitmapListener(ImageView view) {
        return getBitmapListener(view, ImageView.ScaleType.CENTER);
    }
}
