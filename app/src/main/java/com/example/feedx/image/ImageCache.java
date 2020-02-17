package com.example.feedx.image;

import android.graphics.Bitmap;

public interface ImageCache {
    public Bitmap get(String url);
    public void put(String url, Bitmap bmp);
}
