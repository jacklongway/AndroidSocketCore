package com.longway.core.server;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
public class ReadWorker {
    private static final String TAG = ReadWorker.class.getSimpleName();
    private static final int BUFFER_SIZE = 128;
    private ByteBuffer mBuf = ByteBuffer.allocate(BUFFER_SIZE);
    private ByteArrayOutputStream mOS = new ByteArrayOutputStream(BUFFER_SIZE);

    public void readMessage(SocketChannel socketChannel) {
        ByteBuffer byteBuffer = this.mBuf;
        ByteArrayOutputStream byteArrayOutputStream = this.mOS;
        int len;
        try {
            while ((len = socketChannel.read(mBuf)) > 0) {
                byteBuffer.flip();
                byteArrayOutputStream.write(byteBuffer.array(), 0, len);
            }
            byteArrayOutputStream.flush();
            String msg = byteArrayOutputStream.toString();
            Log.d(TAG, msg);
            broadCastMessage(byteArrayOutputStream.toByteArray());
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            byteBuffer.clear();
            byteArrayOutputStream.reset();
        }
    }

    private void broadCastMessage(byte[] msg) {
        ArrayList<SocketChannel> sessions = SessionManager.getInstance().getAllSessions();
        for (SocketChannel session : sessions) {
            try {
                session.write(ByteBuffer.wrap(msg));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
