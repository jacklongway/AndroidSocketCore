package com.longway.core.server;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class SocketChannelServer implements Runnable {
    private static final String TAG = SocketChannelServer.class.getSimpleName();
    private int mPort;
    private ServerSocketChannel mServerSocketChannel;
    private AtomicBoolean mRunning = new AtomicBoolean(true);
    private ByteBuffer mByteBuffer = ByteBuffer.allocate(128);
    private ByteArrayOutputStream mOS = new ByteArrayOutputStream(128);
    private Thread mThread;
    private ReadWorker mReadWorker;

    private void enforceReadWorker() {
        synchronized (ReadWorker.class) {
            if (mReadWorker == null) {
                mReadWorker = new ReadWorker();
            }
        }
    }

    public SocketChannelServer(int port) {
        this.mPort = port;
        mThread = new Thread(this);
        mThread.start();
    }

    public static SocketChannelServer start(int port) {
        return new SocketChannelServer(port);
    }

    public void pause() {
        if (mRunning.compareAndSet(true, false)) {
            mThread.interrupt();
        }
    }

    @Override
    public void run() {
        try {
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.socket().bind(new InetSocketAddress(mPort));
            mServerSocketChannel = serverSocketChannel;
            Selector selector = Selector.open();
            SelectionKey selectionKey = serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
            Log.d(TAG, selectionKey.toString());
            while (mRunning.get()) {
                int n = selector.select();
                if (n == 0) {
                    continue;
                }
                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = keys.iterator();
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    if (key.isAcceptable()) {
                        Log.d(TAG, "key is acceptable");
                        ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
                        SocketChannel socketChannel = ssc.accept();
                        if (socketChannel == null) {
                            continue;
                        }
                        SessionManager.getInstance().addSession(socketChannel.toString(), socketChannel);
                        socketChannel.configureBlocking(false);
                        socketChannel.register(selector, SelectionKey.OP_READ);
                    } else if (key.isConnectable()) {
                        Log.d(TAG, "key isConnectable");
                    } else if (key.isReadable()) {
                        Log.d(TAG, "key isReadable");
                        SocketChannel socketChannel = (SocketChannel) key.channel();
                        if (!socketChannel.isConnected()) {
                            SessionManager.getInstance().removeSession(socketChannel.toString());
                            key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
                            continue;
                        }
                        //handleRead(socketChannel);
                        enforceReadWorker();
                        mReadWorker.readMessage(socketChannel);
                        key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                    } else if (key.isWritable()) {
                        Log.d(TAG, "key isWritable");
                        //SocketChannel socketChannel = (SocketChannel) key.channel();
                        //socketChannel.write(ByteBuffer.wrap("hello client".getBytes()));
                        Log.d(TAG, "write");
                        key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
                    } else if (key.isValid()) {
                        Log.d(TAG, "key isValid");
                    }
                    iterator.remove();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                mServerSocketChannel.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleRead(SocketChannel socketChannel) {
        ByteBuffer byteBuffer = this.mByteBuffer;
        byteBuffer.clear();
        ByteArrayOutputStream os = mOS;
        os.reset();
        int len;
        try {
            while ((len = socketChannel.read(byteBuffer)) > 0) {
                byteBuffer.flip();
                os.write(byteBuffer.array(), 0, len);
            }
            os.flush();
            String content = os.toString();
            Log.d(TAG, content);
            //socketChannel.write(ByteBuffer.wrap(os.toByteArray()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
