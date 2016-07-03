package com.longway.core.client;


public interface MessageInterceptor {
    byte[] onInterceptor(byte[] msg);
}
