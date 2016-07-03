package com.longway.core.client;

import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.concurrent.LinkedBlockingQueue;

public class WriteWorker extends HandlerThread {
    private static final String TAG = WriteWorker.class.getSimpleName();
    private static final int MESSAGE_TYPE_STRING = 0x01;
    private static final int MESSAGE_TYPE_BYTE = 0x02;
    private static final String RAW_MSG = "raw_msg";
    private SocketChannelClient mSocketChannelClient;
    private Handler mSender;
    private Looper mLooper;
    private long mLastActiveSendTime = System.currentTimeMillis();
    private LinkedList<MessageInterceptor> messageInterceptors = new LinkedList<>();
    private LinkedList<MessageReceiptHandler> messageReceiptHandlers = new LinkedList<>();
    private LinkedBlockingQueue<byte[]> mPendingMessage = new LinkedBlockingQueue<>();


    public boolean addPendingMessage(byte[] message) {
        return mPendingMessage.offer(message);
    }

    public void sendPendingMessage() {
        byte[] message;
        while ((message = mPendingMessage.poll()) != null) {
            sendMsg(message);
        }
    }

    public void registerMessageInterceptor(MessageInterceptor messageInterceptor) {
        if (messageInterceptor == null) {
            throw new NullPointerException("messageInterceptor==null.");
        }
        synchronized (messageInterceptors) {
            if (!messageInterceptors.contains(messageInterceptor)) {
                messageInterceptors.add(messageInterceptor);
            }
        }
    }

    public void unregisterMessageInterceptor(MessageInterceptor messageInterceptor) {
        if (messageInterceptor == null) {
            throw new NullPointerException("messageInterceptor==null.");
        }
        synchronized (messageInterceptors) {
            if (messageInterceptors.contains(messageInterceptor)) {
                messageInterceptors.remove(messageInterceptor);
            }
        }
    }

    private byte[] notifyInterceptor(byte[] msg) {
        byte[] bytes = msg;
        synchronized (messageInterceptors) {
            Object[] messageInterceptors = this.messageInterceptors.toArray();
            if (messageInterceptors != null) {
                final int len = messageInterceptors.length;
                for (int i = 0; i < len; i++) {
                    bytes = ((MessageInterceptor) messageInterceptors[i]).onInterceptor(bytes);
                }
            }
        }
        if (bytes == null || bytes.length == 0) {
            bytes = msg;
        }
        return bytes;
    }

    public void registerMessageReceiptHandler(MessageReceiptHandler messageReceiptHandler) {
        if (messageReceiptHandler == null) {
            throw new NullPointerException(" messageReceiptHandler==null");
        }
        synchronized (messageReceiptHandler) {
            if (!messageReceiptHandlers.contains(messageReceiptHandler)) {
                messageReceiptHandlers.add(messageReceiptHandler);
            }
        }
    }

    public void unregisterMessageReceiptHandler(MessageReceiptHandler messageReceiptHandler) {
        if (messageReceiptHandler == null) {
            throw new NullPointerException(" messageReceiptHandler==null");
        }
        synchronized (messageReceiptHandler) {
            if (messageReceiptHandlers.contains(messageReceiptHandler)) {
                messageReceiptHandlers.remove(messageReceiptHandler);
            }
        }
    }

    private void notifyMessageReceiptHandlerHandleMessageFail(byte[] msg) {
        synchronized (messageReceiptHandlers) {
            Object[] objects = messageReceiptHandlers.toArray();
            if (objects != null) {
                final int len = objects.length;
                for (int i = 0; i < len; i++) {
                    MessageReceiptHandler messageReceiptHandler = (MessageReceiptHandler) objects[i];
                    if (messageReceiptHandler != null) {
                        messageReceiptHandler.onSendMessageFail(msg);
                    }
                }
            }
        }
    }

    public WriteWorker(String name, SocketChannelClient socketChannelClient) {
        super(name);
        this.mSocketChannelClient = socketChannelClient;
        start();
        Looper looper = getLooper();
        mLooper = looper;
        mSender = new Sender(looper);
    }

    public long getLastActiveSendTime() {
        return mLastActiveSendTime;
    }

    public boolean sendMsg(String msg) {
        return sendMsg(msg.getBytes());
    }

    private void checkMsg(byte[] msg) {
        if (msg == null || msg.length == 0) {
            throw new NullPointerException("msg==null");
        }
    }

    private void checkBound(byte[] msg, int offset, int length) {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be large than equals 0");
        }
        if (length <= 0) {
            throw new IllegalArgumentException("length must be large than 0");
        }
        if (offset > length) {
            throw new IllegalArgumentException("offset must be less than equals length");
        }
        if (offset + length > msg.length) {
            throw new IllegalArgumentException("offset add length must be less than msg.length");
        }
    }

    private void checkByteBuffer(ByteBuffer byteBuffer) {
        if (byteBuffer == null) {
            throw new NullPointerException("byteBuffer==null");
        }
    }

    public boolean sendMsg(byte[] msg, int offset, int length) {
        checkMsg(msg);
        checkBound(msg, offset, length);
        byte[] subMsg = new byte[length];
        System.arraycopy(msg, offset, subMsg, 0, length);
        return sendMsg(msg);
    }

    public boolean sendMsg(ByteBuffer byteBuffer) {
        checkByteBuffer(byteBuffer);
        return sendMsg(byteBuffer.array());
    }


    public boolean sendMsg(byte[] msg) {
        checkMsg(msg);
        if (!mSocketChannelClient.isConnected()) {
            boolean add = addPendingMessage(msg);
            return add;
        } else {
            Message message = Message.obtain(mSender);
            message.what = MESSAGE_TYPE_BYTE;
            message.obj = msg;
            Bundle bundle = new Bundle();
            bundle.putByteArray(RAW_MSG, msg);
            message.setData(bundle);
            message.sendToTarget();
        }
        return true;
    }


    public void pause() {
        if (mLooper != null) {
            mLooper.quit();
        }
    }

    private final class Sender extends Handler {
        public Sender(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            Log.d(TAG, msg.toString());
            final SocketChannelClient socketChannelClient = mSocketChannelClient;
            if (socketChannelClient.isConnected()) {
                try {
                    switch (msg.what) {
                        case MESSAGE_TYPE_STRING:
                            break;
                        case MESSAGE_TYPE_BYTE:
                            socketChannelClient.writeMessageToServer((notifyInterceptor((byte[]) msg.obj)));
                            mLastActiveSendTime = System.currentTimeMillis();
                            break;
                        default:
                            break;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    Bundle bundle = msg.peekData();
                    if (bundle != null) {
                        notifyMessageReceiptHandlerHandleMessageFail(bundle.getByteArray(RAW_MSG));
                    }
                }
            }
        }
    }
}
