package com.longway.core.client;

import java.util.LinkedList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public class ReconnectedThread implements Runnable {
    private static final String TAG = ReconnectedThread.class.getSimpleName();
    private static final int BASE_TIME = new Random(11).nextInt() + 5;
    private SocketChannelClient mClient;
    private Thread mReconnectedThread;
    private int mCurrentRetry = 0;
    private LinkedList<ReconnectedListener> mListener = new LinkedList<>();
    private AtomicBoolean mRunning = new AtomicBoolean(true);
    private AtomicBoolean mWakeUp = new AtomicBoolean(false);

    public void registerReconnectedListener(ReconnectedListener reconnectedListener) {
        if (reconnectedListener == null) {
            throw new NullPointerException("reconnectedListener==null.");
        }
        synchronized (mListener) {
            if (!mListener.contains(reconnectedListener)) {
                mListener.add(reconnectedListener);
            }
        }
    }

    public void unregisterReconnectedListener(ReconnectedListener reconnectedListener) {
        if (reconnectedListener == null) {
            throw new NullPointerException("reconnectedListener==null.");
        }
        synchronized (mListener) {
            if (mListener.contains(reconnectedListener)) {
                mListener.remove(reconnectedListener);
            }
        }
    }

    public ReconnectedThread(SocketChannelClient socketChannelClient) {
        this.mClient = socketChannelClient;
    }

    public static ReconnectedThread buildReconnectedThread(SocketChannelClient socketChannelClient) {
        return new ReconnectedThread(socketChannelClient);
    }

    public synchronized boolean startReconnected() {
        mCurrentRetry = 0;
        if (mReconnectedThread == null || !mReconnectedThread.isAlive()) {
            mRunning.set(true);
            mReconnectedThread = new Thread(this);
            mReconnectedThread.setName(TAG);
            mReconnectedThread.setDaemon(true);
            mReconnectedThread.start();
        } else {
            if (!mReconnectedThread.isInterrupted()) {
                mReconnectedThread.interrupt();
            }
        }
        return true;
    }

    private int getRetryTime() {
        mCurrentRetry++;
        if (mCurrentRetry > 13) {
            return BASE_TIME * 6 * 5;
        }
        if (mCurrentRetry > 7) {
            return BASE_TIME * 6;
        }
        return BASE_TIME;
    }

    private void notifyReconnectedListener(int second) {
        Object[] reconnectedListeners = mListener.toArray();
        if (reconnectedListeners != null) {
            final int len = reconnectedListeners.length;
            for (int i = 0; i < len; i++) {
                ((ReconnectedListener) reconnectedListeners[i]).retryAttemptAfter(second);
            }
        }
    }

    private void notifyReconnectedListener(String reason) {
        Object[] reconnectedListeners = mListener.toArray();
        if (reconnectedListeners != null) {
            final int len = reconnectedListeners.length;
            for (int i = 0; i < len; i++) {
                ((ReconnectedListener) reconnectedListeners[i]).retryAttemptFail(reason);
            }
        }
    }

    public void pause() {
        if (mReconnectedThread != null && mReconnectedThread.isAlive()) {
            mReconnectedThread.interrupt();
        }
        mRunning.set(false);
    }

    @Override
    public void run() {
        while (mRunning.get() && !mClient.isConnected()) {
            int time = getRetryTime();
            while (!mWakeUp.get() && !mClient.isConnected() && time > 0) {
                time--;
                try {
                    Thread.sleep(1000);
                    notifyReconnectedListener(time);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    mWakeUp.set(true);
                    break;
                }
            }
            try {
                if (!mClient.isConnected()) {
                    mClient.startClient();
                }
            } catch (Exception e) {
                notifyReconnectedListener(e.getLocalizedMessage());
            }
        }
    }
}
