package com.longway.core.client;

public interface MessageReceiptHandler {
    void onSendMessageFail(byte[] msg);
}
