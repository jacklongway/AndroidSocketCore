package com.longway.core.client;

import android.app.Application;
import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class SocketChannelClient implements Runnable, NetworkMonitor {
    private static final String TAG = SocketChannelClient.class.getSimpleName();
    private String mHost;
    private int mPort;
    private WriteWorker mWriteWorker;
    private IReadHandler mReadHandler;
    private KeepAliveThread mKeepAlive;
    private SocketChannel mSocketChannelClient;
    private NetworkMonitorReceiver mNetworkMonitorReceiver;
    private Context mContext;
    private ReconnectedThread mReconnectedThread;
    private LinkedList<ConnectListener> mConnectListeners = new LinkedList<>();
    private AtomicBoolean mDestroy = new AtomicBoolean(false);
    private AtomicBoolean mConnecting = new AtomicBoolean(false);

    /**
     * set custom messageHandler
     */
    public SocketChannelClient setMessageHandler(IReadHandler readHandler) {
        if (readHandler == null) {
            throw new NullPointerException("readHandler==null");
        }
        this.mReadHandler = readHandler;
        return this;
    }

    public SocketChannelClient registerConnectListener(ConnectListener connectListener) {
        if (connectListener == null) {
            throw new NullPointerException("connectListener==null.");
        }
        synchronized (mConnectListeners) {
            if (!mConnectListeners.contains(connectListener)) {
                mConnectListeners.add(connectListener);
            }
        }
        return this;
    }

    public SocketChannelClient unregisterConnectListener(ConnectListener connectListener) {
        if (connectListener == null) {
            throw new NullPointerException("connectListener==null.");
        }
        synchronized (mConnectListeners) {
            if (mConnectListeners.contains(connectListener)) {
                mConnectListeners.remove(connectListener);
            }
        }
        return this;
    }

    private void notifyConnectListenerSuccess(String detail) {
        synchronized (mConnectListeners) {
            Object[] connectListeners = this.mConnectListeners.toArray();
            if (connectListeners != null) {
                final int len = connectListeners.length;
                for (int i = 0; i < len; i++) {
                    ((ConnectListener) connectListeners[i]).connectToServerSuccess(detail);
                }
            }
        }
    }


    private void notifyConnectListenerFail(String reason) {
        synchronized (mConnectListeners) {
            Object[] connectListeners = this.mConnectListeners.toArray();
            if (connectListeners != null) {
                final int len = connectListeners.length;
                for (int i = 0; i < len; i++) {
                    ((ConnectListener) connectListeners[i]).connectToServerFail(reason);
                }
            }
        }
    }


    public SocketChannelClient registerNetMonitor(NetworkMonitor networkMonitor) {
        NetworkMonitorReceiver.registerNetworkMonitor(networkMonitor);
        return this;
    }

    public SocketChannelClient unregisterMonitor(NetworkMonitor networkMonitor) {
        NetworkMonitorReceiver.unregisterNetworkMonitor(networkMonitor);
        return this;
    }

    public SocketChannelClient registerReconnectedListener(ReconnectedListener reconnectedListener) {
        mReconnectedThread.registerReconnectedListener(reconnectedListener);
        return this;
    }

    public SocketChannelClient unregisterReconnectedListener(ReconnectedListener reconnectedListener) {
        mReconnectedThread.unregisterReconnectedListener(reconnectedListener);
        return this;
    }

    public SocketChannelClient registerMessageHandler(MessageHandler messageHandler) {
        if (mReadHandler instanceof ReadWorker) {
            ReadWorker readWorker = (ReadWorker) mReadHandler;
            readWorker.registerMessageHandler(messageHandler);
        }
        return this;
    }

    public SocketChannelClient unregisterMessageHandler(MessageHandler messageHandler) {
        if (mReadHandler instanceof ReadWorker) {
            ReadWorker readWorker = (ReadWorker) mReadHandler;
            readWorker.unregisterMessageHandler(messageHandler);
        }
        return this;
    }

    public SocketChannelClient registerMessageInterceptor(MessageInterceptor messageInterceptor) {
        mWriteWorker.registerMessageInterceptor(messageInterceptor);
        return this;
    }

    public SocketChannelClient unregisterMessageInterceptor(MessageInterceptor messageInterceptor) {
        mWriteWorker.unregisterMessageInterceptor(messageInterceptor);
        return this;
    }

    public SocketChannelClient registerPingListener(PingListener pingListener) {
        mKeepAlive.registerPingListener(pingListener);
        return this;
    }

    public SocketChannelClient setPing(String ping) {
        mKeepAlive.setPing(ping);
        return this;
    }

    public SocketChannelClient unregisterPingListener(PingListener pingListener) {
        mKeepAlive.unregisterPingListener(pingListener);
        return this;
    }

    public SocketChannelClient registerMessageReceiptHandler(MessageReceiptHandler messageReceiptHandler) {
        mWriteWorker.registerMessageReceiptHandler(messageReceiptHandler);
        return this;
    }

    public SocketChannelClient unregisterMessageReceiptHandler(MessageReceiptHandler messageReceiptHandler) {
        mWriteWorker.unregisterMessageReceiptHandler(messageReceiptHandler);
        return this;
    }


    /**
     *
     * @param context 上下文
     * @param host 主机
     * @param port 端口
     */
    public SocketChannelClient(Context context, String host, int port) {
        if (context == null) {
            throw new NullPointerException("context==null");
        }
        if (TextUtils.isEmpty(host)) {
            throw new NullPointerException("host==null");
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("port must be between 0 and 65535");
        }
        if (!(context instanceof Application)) {
            mContext = context.getApplicationContext();
        }
        // fix bug
        if (mContext == null) {
            mContext = context;
        }
        this.mHost = host;
        this.mPort = port;
        registerNetworkMonitor();
        enforceWriteWorker();
        enforceReadHandler();
        enforceKeepAlive();
        enforceReconnectedThread();
    }

    public static SocketChannelClient newClient(Context context, String host, int port) {
        return new SocketChannelClient(context, host, port);
    }

    public SocketChannelClient start() {
        new Thread(this).start();
        return this;
    }

    public boolean isConnected() {
        return mSocketChannelClient != null && mSocketChannelClient.isConnected();
    }

    public boolean sendMsg(String msg) {
        return mWriteWorker.sendMsg(msg);
    }

    public boolean sendMsg(byte[] msg) {
        return mWriteWorker.sendMsg(msg);
    }

    public boolean sendMsg(byte[] msg, int offset, int length) {
        return mWriteWorker.sendMsg(msg, offset, length);
    }

    public boolean sendMsg(ByteBuffer msg) {
        return mWriteWorker.sendMsg(msg);
    }

    void writeMessageToServer(byte[] msg) throws IOException {
        if (isConnected()) {
            mSocketChannelClient.write(ByteBuffer.wrap(msg));
        }
    }

    public long getLastActiveSendTime() {
        return mWriteWorker.getLastActiveSendTime();
    }

    private void enforceReadHandler() {
        synchronized (ReadWorker.class) {
            if (mReadHandler == null) {
                mReadHandler = new ReadWorker();
            }
        }
    }

    private void enforceWriteWorker() {
        synchronized (WriteWorker.class) {
            if (mWriteWorker == null) {
                mWriteWorker = new WriteWorker(WriteWorker.class.getSimpleName(), this);
            }
        }
    }

    private void enforceKeepAlive() {
        synchronized (KeepAliveThread.class) {
            if (mKeepAlive == null) {
                mKeepAlive = new KeepAliveThread(this);
            }
        }
    }

    private void registerNetworkMonitor() {
        synchronized (NetworkMonitorReceiver.class) {
            if (mNetworkMonitorReceiver == null) {
                NetworkMonitorReceiver.registerNetworkMonitor(this);
                mNetworkMonitorReceiver = NetworkMonitorReceiver.registerNetworkMonitorReceiver(mContext);
            }
        }
    }

    @Override
    public void run() {
        startClient();
    }

    void startClient() {
        if (!mDestroy.get() && mConnecting.compareAndSet(false, true)) {
            if (isConnected()) {
                return;
            }
            try {
                SocketChannel socketChannel = SocketChannel.open();
                socketChannel.configureBlocking(false);
                socketChannel.connect(new InetSocketAddress(mHost, mPort));
                int count = 0;
                while (!socketChannel.finishConnect()) {
                    if (count >= 5) {
                        mConnecting.set(false);
                        return;
                    }
                    count++;
                    Log.e(TAG, "connecting....");
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                notifyConnectListenerSuccess("connect to " + mHost + ":" + mPort + " success.");
                mSocketChannelClient = socketChannel;
                mWriteWorker.sendPendingMessage();
                Selector selector = Selector.open();
                socketChannel.register(selector, SelectionKey.OP_READ);
                mKeepAlive.start();
                while (!mDestroy.get() && isConnected()) {
                    int n = selector.select();
                    Log.d(TAG, "operations:" + n);
                    if (n == 0) {
                        continue;
                    }
                    Set<SelectionKey> keys = selector.selectedKeys();
                    Iterator<SelectionKey> iterator = keys.iterator();
                    while (iterator.hasNext()) {
                        SelectionKey selectionKey = iterator.next();
                        if (selectionKey.isReadable()) {
                            Log.d(TAG, "key isReadable");
                            mReadHandler.handlerRead((SocketChannel) selectionKey.channel());
                        } else if (selectionKey.isWritable()) {
                            Log.d(TAG, "key isWritable");
                        } else if (selectionKey.isConnectable()) {
                            Log.d(TAG, "isConnectable");
                            //selectionKey.interestOps(SelectionKey.OP_READ);
                        }
                        iterator.remove();
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "socket exception:" + e.getMessage());
                if (!mDestroy.get()) {
                    mConnecting.set(false);
                    mSocketChannelClient = null;
                    notifyConnectListenerFail(e.getLocalizedMessage());
                    startReconnect();
                }
            }
        }
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }

    public synchronized void close() {
        try {
            if (mSocketChannelClient != null && mSocketChannelClient.isConnected()) {
                mDestroy.set(true);
                NetworkMonitorReceiver.unregisterNetworkMonitor(this);
                NetworkMonitorReceiver.unRegisterNetworkMonitorReceiver(mContext, mNetworkMonitorReceiver);
                if (mWriteWorker != null) {
                    mWriteWorker.pause();
                    mWriteWorker = null;
                }
                if (mKeepAlive != null) {
                    mKeepAlive.pause();
                    mKeepAlive = null;
                }
                if (mReconnectedThread != null) {
                    mReconnectedThread.pause();
                    mReconnectedThread = null;
                }
                mSocketChannelClient.close();
                mSocketChannelClient = null;

            }
        } catch (IOException e) {
            e.printStackTrace();
            mSocketChannelClient = null;
        }
    }

    private void startReconnect() {
        if (!isConnected()) {
            mReconnectedThread.startReconnected();
        }
    }

    private void enforceReconnectedThread() {
        synchronized (ReconnectedThread.class) {
            if (mReconnectedThread == null) {
                mReconnectedThread = ReconnectedThread.buildReconnectedThread(this);
            }
        }
    }

    @Override
    public void onConnect(int type) {
        startReconnect();
    }

    @Override
    public void onDisconnected(String reason) {
        startReconnect();
    }
}
