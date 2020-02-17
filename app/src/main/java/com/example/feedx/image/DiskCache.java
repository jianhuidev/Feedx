package com.example.feedx.image;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;

import com.example.feedx.pool.ThreadPoolManager;
import com.jakewharton.disklrucache.DiskLruCache;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class DiskCache implements ImageCache {

    DiskLruCache mDiskCache;

    public DiskCache(Context context) {
        try {
            File cacheDir = getDiskCacheDir(context, "image");
            if (!cacheDir.exists()) {
                cacheDir.mkdir();
            }
            mDiskCache = DiskLruCache.open(cacheDir, ImgUtil.getAppVersion(context), 1, 10 * 1024 * 1024);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Bitmap get(String url) {
        String key = ImgUtil.hashKeyForDisk(url);
        try {
            DiskLruCache.Snapshot snapShot = mDiskCache.get(key);
            if (snapShot != null) {
                InputStream is = snapShot.getInputStream(0);
                Bitmap bitmap = BitmapFactory.decodeStream(is);
                return bitmap;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;

    }

    @Override
    public void put(final String url, final Bitmap bmp) {
        ThreadPoolManager.getInstance().execute(new Runnable() {
            @Override
            public void run() {
                String key = ImgUtil.hashKeyForDisk(url);
                try {
                    DiskLruCache.Editor editor = mDiskCache.edit(key);
                    OutputStream ops = editor.newOutputStream(0);
                    if (writeBmp2Disk(bmp,ops)) {
                        editor.commit();
                    } else {
                        editor.abort();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void remove(String url) {
        String key = ImgUtil.hashKeyForDisk(url);
        try {
            mDiskCache.remove(key);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean writeBmp2Disk(Bitmap bmp, OutputStream ops) {
        byte[] bs = ImgUtil.bitmap2Bytes(bmp);
        BufferedOutputStream out = null;
        try {
            out = new BufferedOutputStream(ops, 8 * 1024);
            out.write(bs,0,bs.length);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (out!= null) {
                    out.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    /**
     * 将缓存记录同步到journal文件
     */
    public void flush() {
        if (mDiskCache != null) {
            try {
                mDiskCache.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }




    private File getDiskCacheDir(Context context, String uniqueName) {
        String cachePath;
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())
                || !Environment.isExternalStorageRemovable()) {
            cachePath = context.getExternalCacheDir().getPath();
        } else {
            cachePath = context.getCacheDir().getPath();
        }
        return new File(cachePath + File.separator + uniqueName);
    }
}
