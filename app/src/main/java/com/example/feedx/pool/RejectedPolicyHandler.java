package com.example.feedx.pool;

public interface RejectedPolicyHandler {
    void rejectedPolicy(Runnable r, int reason);
}
