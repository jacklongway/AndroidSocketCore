package com.longway.core.client;

import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;

public class KeepAliveThread implements Runnable {
    private static final String TAG = KeepAliveThread.class.getSimpleName();
    private static final String PING = " ";
    private static final int ACTIVE_INTERNAL = 10 * 1000;
    private SocketChannelClient mSocketChannelClient;
    private Thread mKeepAliveThread;
    private String mPing = PING;
    private AtomicBoolean mRunning = new AtomicBoolean(true);
    private LinkedList<PingListener> mPingListener = new LinkedList<>();

    public void setPing(String ping) {
        this.mPing = ping;
    }

    public void registerPingListener(PingListener pingListener) {
        if (pingListener == null) {
            throw new NullPointerException("pingListener==null.");
        }
        synchronized (mPingListener) {
            if (!mPingListener.contains(pingListener)) {
                mPingListener.add(pingListener);
            }
        }
    }

    public void unregisterPingListener(PingListener pingListener) {
        if (pingListener == null) {
            throw new NullPointerException("pingListener==null.");
        }
        synchronized (mPingListener) {
            if (mPingListener.contains(pingListener)) {
                mPingListener.remove(pingListener);
            }
        }
    }

    private void notifyPingListener(String ping) {
        synchronized (mPingListener) {
            Object[] pingListeners = this.mPingListener.toArray();
            if (pingListeners != null) {
                final int len = pingListeners.length;
                for (int i = 0; i < len; i++) {
                    ((PingListener) pingListeners[i]).ping(ping);
                }
            }
        }
    }

    public KeepAliveThread(SocketChannelClient socketChannelClient) {
        this.mSocketChannelClient = socketChannelClient;
    }

    public void start() {
        mKeepAliveThread = new Thread(this);
        mKeepAliveThread.setName(TAG);
        mKeepAliveThread.setDaemon(true);
        mKeepAliveThread.start();
    }

    public static KeepAliveThread start(SocketChannelClient socketChannelClient) {
        return new KeepAliveThread(socketChannelClient);
    }

    public boolean pause() {
        if (mKeepAliveThread.isAlive()) {
            mKeepAliveThread.interrupt();
        }
        mRunning.set(false);
        return true;
    }

    @Override
    public void run() {
        while (mRunning.get() && mSocketChannelClient.isConnected()) {
            long delay = System.currentTimeMillis() - mSocketChannelClient.getLastActiveSendTime();
            if (delay < ACTIVE_INTERNAL) {
                try {
                    Thread.sleep(ACTIVE_INTERNAL - delay);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                continue;
            }
            if (!mSocketChannelClient.isConnected()) {
                return;
            }
            if (mSocketChannelClient.isConnected()) {
                mSocketChannelClient.sendMsg(mPing); // send ping
                notifyPingListener(mPing);
            }
        }
    }
}
