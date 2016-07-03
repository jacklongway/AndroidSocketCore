package com.longway.core.client;

public interface ConnectListener {
    void connectToServerFail(String reason);

    void connectToServerSuccess(String detail);
}
