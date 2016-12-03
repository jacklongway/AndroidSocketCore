package com.longway.core.client;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;

public class ReadWorker implements IReadHandler {
    private static final String TAG = ReadWorker.class.getSimpleName();
    private static final int BUFFER_SIZE = 128;
    private ByteBuffer mBuf = ByteBuffer.allocate(BUFFER_SIZE);
    private ByteArrayOutputStream mOS = new ByteArrayOutputStream(BUFFER_SIZE);
    private LinkedList<MessageHandler> messageHandlers = new LinkedList<>();

    public void registerMessageHandler(MessageHandler messageHandler) {
        if (messageHandler == null) {
            throw new NullPointerException("messageHandler==null.");
        }
        synchronized (messageHandlers) {
            if (!messageHandlers.contains(messageHandler)) {
                messageHandlers.add(messageHandler);
            }
        }
    }

    public void unregisterMessageHandler(MessageHandler messageHandler) {
        if (messageHandler == null) {
            throw new NullPointerException("messageHandler==null.");
        }
        synchronized (messageHandlers) {
            if (messageHandlers.contains(messageHandler)) {
                messageHandlers.remove(messageHandler);
            }
        }
    }

    private void notifyMessageHandler(byte[] message) {
        synchronized (messageHandlers) {
            Object[] messageHandlers = this.messageHandlers.toArray();
            if (messageHandlers != null) {
                final int len = messageHandlers.length;
                for (int i = 0; i < len; i++) {
                    ((MessageHandler) messageHandlers[i]).onReceiveMessage(message);
                }
            }
        }
    }

    public ReadWorker() {

    }

    @Override
    public void handlerRead(SocketChannel socketChannel) throws IOException {
        if (socketChannel.isConnected()) {
            ByteBuffer byteBuffer = this.mBuf;
            ByteArrayOutputStream os = this.mOS;
            int len;
            try {
                while ((len = socketChannel.read(byteBuffer)) > 0) {
                    byteBuffer.flip();
                    os.write(byteBuffer.array(), 0, len);
                    if (!socketChannel.isConnected()) {
                        return;
                    }
                }
                os.flush();
                Log.d(TAG, os.toString());
                notifyMessageHandler(os.toByteArray());
            } catch (IOException e) {
                throw e;
            } finally {
                byteBuffer.clear();
                os.reset();
            }
        }
    }
}
