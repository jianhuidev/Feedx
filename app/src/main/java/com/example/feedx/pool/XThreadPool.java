package com.example.feedx.pool;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 最新版
 */
public class XThreadPool {
    private volatile int corePoolSize;
    private volatile int maximumPoolSize;
    private volatile long keepAliveTime; // 统一用纳秒比较
    private final XBlockList<Runnable> workQueue;
    private volatile ThreadFactory threadFactory;
    private volatile RejectedPolicyHandler handler;

    /**
     * 工作线程集合，持有主锁才能访问
     */
    private final HashSet<Worker> workers = new HashSet<>();
    /**
     * 当前工作线程数，针对worker 的原子计数
     */
    private final AtomicInteger workerCount = new AtomicInteger(0);
    /**
     * 针对worker 集合的锁
     */
    private final ReentrantLock mainLock = new ReentrantLock();
    /**
     * 线程池是否处于关闭状态
     */
    private AtomicBoolean isShutdownState = new AtomicBoolean(false);
    /**
     * 由于线程与队列满了的普通的拒绝策略
     */
    public static final int NOR_REJECT = 1;
    /**
     * 线程池关闭了拒绝
     */
    public static final int SHUTDOWN_REJECT = 2;

    public XThreadPool(int corePoolSize,
                       int maximumPoolSize,
                       long keepAliveTime,
                       TimeUnit unit,
                       XBlockList<Runnable> workQueue,
                       ThreadFactory threadFactory,
                       RejectedPolicyHandler handler) {
        if (corePoolSize < 0 ||
                maximumPoolSize <= 0 ||
                maximumPoolSize < corePoolSize ||
                keepAliveTime < 0) {
            throw new IllegalArgumentException();
        }
        if (workQueue == null || threadFactory == null || handler == null) {
            throw new NullPointerException();
        }
        this.corePoolSize = corePoolSize;
        this.maximumPoolSize = maximumPoolSize;
        this.keepAliveTime = unit.toNanos(keepAliveTime);
        this.workQueue = workQueue;
        this.threadFactory = threadFactory;
        this.handler = handler;
    }

    public void execute(Runnable command) {
        if (command == null) {
            throw new NullPointerException();
        }

        if (isShutdown()) {
            reject(command,SHUTDOWN_REJECT);
            return;
        }

        // 1. 当前工作线程数 < 核心线程数
        if (workerCount.get() < corePoolSize) {
            // 注意添加方法有计数操作
            if (addWorker(command))
                return;
        }

        // 2. 看看能否存入队列
        if (workQueue.offer(command)) {
            if (workerCount.get() == 0) {
                // 发现没有则创建一个工作线程，这种情况极少
                addWorker(null);
            }
            return;
        }

        // 3. 创建线程执行任务（非核心线程），不要超过最大线程数
        if (workerCount.get() < maximumPoolSize) {
            addWorker(command);
        } else {
            reject(command, NOR_REJECT);
        }
    }

    /**
     * 添加一个工作线程并运行；
     * 1. 线程安全的将线程数加一；
     * 2. 添加工作线程到集合（需持有主锁）；
     * 3. 调用start() ；
     */
    private boolean addWorker(Runnable firstTask) {
        boolean workerStarted = false;
        boolean workerAdded = false;
        Worker w = null;
        try {
            w = new Worker(firstTask);
            final Thread t = w.thread;
            if (t != null) {
                final ReentrantLock mainLock = this.mainLock;
                mainLock.lock();
                try {
                    if (isShutdown()) {
                        reject(firstTask, SHUTDOWN_REJECT);
                        return false;
                    }
                    workers.add(w);
                    workerCount.getAndIncrement();
                    workerAdded = true;
                } finally {
                    mainLock.unlock();
                }

                if (workerAdded) {
                    t.start();
                    workerStarted = true;
                }
            }
        } finally {
            if (!workerStarted && workerAdded)
                addWorkerFailed(w);
        }
        return workerStarted;
    }

    private void addWorkerFailed(Worker w) {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            if (w != null) {
                workers.remove(w);
                workerCount.getAndDecrement();
            }
        } finally {
            mainLock.unlock();
        }
    }

    public boolean isShutdown() {
        return isShutdownState.get();
    }

    public void shutdown() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            toShutdownState();
            interruptIdleWorkers();
            // 目前未写onShutdown()
        } finally {
            mainLock.unlock();
        }
    }

    public List<Runnable> shutdownNow() {
        XBlockList<Runnable> q = workQueue;
        List<Runnable> taskList = new ArrayList<>();
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            toShutdownState();
            interruptWorkers();
            q.drainToList(taskList);
        } finally {
            mainLock.unlock();
        }
        return taskList;
    }

    private void toShutdownState() {
        isShutdownState.set(true);
    }

    private void interruptIdleWorkers() {
        // 目前考虑效率暂不锁，有复杂需求再扩展
        for (Worker w : workers) {
            Thread t = w.thread;
            if (!t.isInterrupted() && w.rl.tryLock()) {
                try {
                    t.interrupt();
                } finally {
                    w.rl.unlock();
                }
            }
        }
    }

    private void interruptWorkers() {
        for (Worker w : workers) {
            Thread t = w.thread;
            if (t != null && !t.isInterrupted()) {
                t.interrupt();
            }
        }
    }

    private final class Worker implements Runnable {
        /**
         * 任务运行所在的线程
         */
        final Thread thread;
        /**
         * 初始任务
         */
        Runnable firstTask;
        /**
         * 每个线程完成的任务数
         */
        volatile long completedTasks;
        /**
         * worker 内部锁，目前主要用于shutdown 功能
         */
        private final ReentrantLock rl = new ReentrantLock();

        Worker(Runnable firstTask) {
            this.firstTask = firstTask;
            this.thread = getThreadFactory().newThread(this);
        }

        /**
         * 工作线程完成任务的前后，还可方便调用其他方法，这种写法可以很好的扩展
         * a(); firstTask.run(); b();
         */
        @Override
        public void run() {
            runWorker(this);
        }
    }

    final void runWorker(Worker w) {
        Runnable task = w.firstTask;
        w.firstTask = null; // help GC
        try {
            while (task != null || (task = getTask()) != null) {
                w.rl.lock();
                try {
                    task.run();
                } finally {
                    task = null;
                    w.completedTasks++;
                    w.rl.unlock();
                }
            }
        } finally {
            processWorkerExit(w);
        }
    }

    /**
     * 先移除，在看是否是没达到核心线程数，没达到则再创建一个
     */
    private void processWorkerExit(Worker w) {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            workers.remove(w);
            if (!isShutdown() && workerCount.get() - 1 < corePoolSize) {
                addWorker(null);
            } else {
                workerCount.getAndDecrement();
            }
        } finally {
            mainLock.unlock();
        }
    }

    private Runnable getTask() {
        if (isShutdown() && workQueue.isEmpty())
            return null;
        try {
            Runnable task = null;
            if (workerCount.get() > corePoolSize) {
                task = workQueue.poll(keepAliveTime, TimeUnit.NANOSECONDS);
            } else {
                task = workQueue.take();
            }
            if (task != null) {
                return task;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }

    public ThreadFactory getThreadFactory() {
        return threadFactory;
    }

    final void reject(Runnable command, int reason) {
        handler.rejectedPolicy(command, reason);
    }

}
