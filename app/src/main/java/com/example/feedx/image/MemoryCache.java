package com.example.feedx.image;

import android.graphics.Bitmap;
import android.util.LruCache;

public class MemoryCache implements ImageCache {

    private LruCache<String,Bitmap> mMemoryCache;

    public MemoryCache() {
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        // LruCache 的缓存大小，一般为当前进程可用容量的1/8
        int cacheSize = maxMemory / 8;
        mMemoryCache = new LruCache<String, Bitmap>(cacheSize) {

            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getRowBytes() * bitmap.getHeight() / 1024;
            }
        };
    }

    @Override
    public Bitmap get(String url) {
        return mMemoryCache.get(url);
    }

    @Override
    public void put(String url, Bitmap bmp) {
        mMemoryCache.put(url,bmp);
    }
}
