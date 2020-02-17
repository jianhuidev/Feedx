package com.example.feedx.pool;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadPoolManager {

    private static ThreadPoolManager mInstance;

    public static ThreadPoolManager getInstance(){
        if (mInstance == null){
            synchronized (ThreadPoolManager.class){
                if (mInstance == null){
                    mInstance = new ThreadPoolManager();
                }
            }
        }
        return mInstance;
    }

    private XThreadPool threadPool;

    public ThreadPoolManager() {
        // 当前设备可用处理器核心数*2 + 1 ，能让cpu 效率得到最大程度执行（有研究论证）
        int corePoolSize = Runtime.getRuntime().availableProcessors() * 2 + 1;
        int maxPoolSize = corePoolSize;
        long keepAliveTime = 0L;
        TimeUnit unit = TimeUnit.SECONDS;
        threadPool = new XThreadPool(corePoolSize,
                maxPoolSize,
                keepAliveTime,
                unit,
                new XBlockList<Runnable>(),
                new DefaultThreadFactory(),
                new RejectedHandler());
    }

    public void execute(Runnable r){
        if (r != null) {
            threadPool.execute(r);
        }
    }

    class DefaultThreadFactory implements ThreadFactory {

        private final AtomicInteger poolNumber = new AtomicInteger(1);
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final ThreadGroup group;
        private final String namePrefix;

        public DefaultThreadFactory() {
            this.group = Thread.currentThread().getThreadGroup();
            this.namePrefix = "pool-" +
                    poolNumber.getAndIncrement() +
                    "-thread-";
        }

        public DefaultThreadFactory(String namePrefix) {
            this.group = Thread.currentThread().getThreadGroup();
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(group,r,
                    namePrefix + threadNumber.getAndIncrement(),
                    0);
            if (t.isDaemon()) {
                t.setDaemon(false);
            }
            if (t.getPriority() != Thread.NORM_PRIORITY)
                t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }

    class RejectedHandler implements RejectedPolicyHandler {

        @Override
        public void rejectedPolicy(Runnable r, int reason) {
            if (reason == XThreadPool.NOR_REJECT) {
                System.out.println("log rejectedPolicy");
            } else if (reason == XThreadPool.SHUTDOWN_REJECT) {
                System.out.println("rejected reason shutdown");
            }
        }
    }
}
