package com.example.feedx.image;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.widget.ImageView;

import com.example.feedx.pool.ThreadPoolManager;
import com.example.feedx.pool.XAsync;

import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

//import com.example.netdemo.net.HttpHandler;
//import com.example.netdemo.test.ViewCallback;

public class ImageLoader {

    private String TAG = "ImageLoader";

    private ImageCache mImageCache;

    public ImageLoader(Context context) {
        // 默认使用双缓存
        mImageCache = new DoubleCache(context);
    }

    public void setImageCache(ImageCache cache) {
        mImageCache = cache;
    }

    public void displayImage(final String url, final ImageView imageView) {
        final Bitmap bitmap = mImageCache.get(url);
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
            Log.e(TAG,"image cache");
            return;
        }
        requestImage(url, imageView);
    }

    private void requestImage(final String url, final ImageView imageView) {
        ThreadPoolManager.getInstance().execute(new XAsync<Bitmap>() {
            @Override
            protected Bitmap task() {
                try {
                    return image(url);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            protected void callback(Bitmap result) {
                if (result != null) {
                    imageView.setImageBitmap(result);
                    mImageCache.put(url, result);
                } else {
                    // 可以显示默认图片
                    Log.e(TAG,"image error");
                }
            }
        });
    }

    private OkHttpClient client = new OkHttpClient();

    public Bitmap image(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .build();
        try (Response response = client.newCall(request).execute()) {
            return BitmapFactory.decodeStream(response.body().byteStream());
        }
    }

    /**
     * 双缓存，内存 + 磁盘缓存，
     * 先内存缓存获取，有就可，如果没有图片，
     * 再磁盘缓存获取，有就可，没有再，
     * 网络获取
     */
    class DoubleCache implements ImageCache {

        ImageCache mMemoryCache;
        ImageCache mDiskCache;

        public DoubleCache(Context context) {
            mMemoryCache = new MemoryCache();
            mDiskCache = new DiskCache(context);
        }

        @Override
        public Bitmap get(String url) {
            Bitmap bitmap = mMemoryCache.get(url);
            if (bitmap != null) {
                return bitmap;
            }
            bitmap = mDiskCache.get(url);
            if (bitmap != null) {
                // 从磁盘缓存取出后，进行了一次内存缓存
                mMemoryCache.put(url, bitmap);
            }
            return bitmap;
        }

        @Override
        public void put(final String url, final Bitmap bmp) {
            mMemoryCache.put(url, bmp);
            mDiskCache.put(url, bmp);
        }
    }

}
