package com.longway.core.client;

public interface ReconnectedListener {
    void retryAttemptAfter(int second);

    void retryAttemptFail(String reason);
}
