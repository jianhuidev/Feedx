package com.example.feedx.pool;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class XBlockList<E> {

    class Node<E> {
        E value;
        Node<E> next;

        public Node(E x) { value = x; }
    }

    private final int capacity;

    private final AtomicInteger count = new AtomicInteger(0);

    private Node<E> head;

    private Node<E> last;

    private final ReentrantLock takeLock = new ReentrantLock();

    private final Condition notEmpty = takeLock.newCondition();

    private final ReentrantLock putLock = new ReentrantLock();

    private final Condition notFull = putLock.newCondition();

    public XBlockList() {
        this(Integer.MAX_VALUE);
    }

    public XBlockList(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException();
        this.capacity = capacity;
        head = new Node<E>(null);
        last = head;
    }

    public void put(E e) throws InterruptedException {
        if (e == null) throw new NullPointerException();
        // 约定所有的put/take操作都会预先设置本地变量
        int c = -1;
        Node<E> node = new Node<>(e);
        final ReentrantLock putLock = this.putLock;
        final AtomicInteger count = this.count;

        putLock.lockInterruptibly(); // 可中断的获取锁
        try {
            while (count.get() == capacity) {
                notFull.await();
            }
            enqueue(node);
            c = count.getAndIncrement();
            // 那么当前元素的个数为 c + 1
            if (c + 1 < capacity) {
                // 当前元素的总个数还没到达容量，那么就尝试唤醒生产者线程，这样生产更快
                notFull.signal();
            }
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        } finally {
            //完成对锁的释放
            putLock.unlock();
        }
        // 生产一个，唤醒消费者线程
        if (c == 0) {
            signalNotEmpty();
        }
    }

    public boolean offer(E e) {
        if (e == null) throw new NullPointerException();
        final AtomicInteger count = this.count;
        if (count.get() == capacity) {
            return false;
        }
        int c = -1;
        Node<E> node = new Node<>(e);
        final ReentrantLock putLock = this.putLock;
        putLock.lock();
        try {
            if (count.get() < capacity) {
                enqueue(node);
                c = count.getAndIncrement();
                if (c + 1 < capacity)
                    notFull.signal();
            }
        } finally {
            putLock.unlock();
        }

        if (c == 0)
            signalNotEmpty();
        return c + 1> 0;
    }

    public E take() throws InterruptedException {
        E x;
        int c = -1;
        final AtomicInteger count = this.count;
        final ReentrantLock takeLock = this.takeLock;
        takeLock.lockInterruptibly();
        try {
            while (count.get() == 0) {
                notEmpty.await();
            }
            x = dequeue();
            c = count.getAndDecrement();
            // 若现在现在还有，就执行唤醒消费者线程逻辑，使消费的更快
            if (c - 1 > 0)
                notEmpty.signal();
        } finally {
            takeLock.unlock();
        }
        // 空出一个位置，唤醒生产者线程
        if (c == capacity) {
            signalNotFull();
        }
        return x;
    }

    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        E x = null;
        int c = -1;
        long nanos = unit.toNanos(timeout);
        final AtomicInteger count = this.count;
        final ReentrantLock takeLock = this.takeLock;
        takeLock.lockInterruptibly();
        try {
            while (count.get() == 0) {
                if (nanos <= 0L)
                    return null;
                nanos = notEmpty.awaitNanos(nanos);
            }
            x = dequeue();
            c = count.getAndDecrement();
            if (c -1 > 0)
                notEmpty.signal();
        } finally {
            takeLock.unlock();
        }
        if (c == capacity) {
            signalNotFull();
        }
        return x;
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public int size() {
        return count.get();
    }

    /**
     * 入队，在队尾链上节点
     */
    private void enqueue(Node<E> node) {
        last.next = node;
        last = node;
    }

    private E dequeue() {
        Node<E> h = head; // 需要注意head 为null ，这里头指针只起索引作用
        Node<E> first = h.next;
        h.next = h; // help GC
        head = first;
        E x = first.value;
        first.value = null;
        return x;
    }

    private void signalNotEmpty() {
        final ReentrantLock takeLock = this.takeLock;
        takeLock.lock();
        try {
            notEmpty.signal();
        } finally {
            takeLock.unlock();
        }
    }

    private void signalNotFull() {
        final ReentrantLock putLock = this.putLock;
        putLock.lock();
        try {
            notFull.signal();
        } finally {
            putLock.unlock();
        }
    }

    /**
     * 将队列的内容放到list ，并且队列清空了队列
     */
    public void drainToList(List<E> list) {
        if (list == null)
            throw new NullPointerException();
        final AtomicInteger count = this.count;
        fullyLock();
        try {
            Node<E> h = head;
            Node<E> p = h.next;
            while (p != null) {
                list.add(p.value);
                p.value = null;
                h.next = h;
                h = p;
                p = p.next;
            }
            count.set(0);
        } finally {
            fullyUnlock();
        }
    }

    void fullyLock() {
        putLock.lock();
        takeLock.lock();
    }

    void fullyUnlock() {
        takeLock.unlock();
        putLock.unlock();
    }

}
