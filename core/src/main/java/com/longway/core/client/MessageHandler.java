package com.longway.core.client;


public interface MessageHandler {
    void onReceiveMessage(byte[] message);
}
