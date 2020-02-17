package com.example.feedx.pool;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

public abstract class XAsync<T> implements Runnable {

    private static final int MSG_POST_RESULT = 1;
    private static final int MSG_POST_PROGRESS = 2;
    private H mH;

    public XAsync() {
        mH = new H();
    }

    @Override
    public void run() {
        T result = null;
        try {
            result = task();
        } finally {
            postResult(result);
        }
    }

    protected final void postProgress(int progress) {
        mH.obtainMessage(MSG_POST_PROGRESS, progress).sendToTarget();
    }

    private void postResult(T result) {
        mH.obtainMessage(MSG_POST_RESULT, result).sendToTarget();
    }

    protected abstract T task();

    protected void progress(int progress) {
    }

    protected void callback (T result) {
    }

    private class H extends Handler {
        public H() {
            super(Looper.getMainLooper());
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_POST_RESULT:
                    callback((T) msg.obj);
                    break;
                case MSG_POST_PROGRESS:
                    progress((Integer) msg.obj);
                    break;
            }
        }
    }
}
