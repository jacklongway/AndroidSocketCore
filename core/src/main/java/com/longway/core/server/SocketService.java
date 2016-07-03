package com.longway.core.server;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;


public class SocketService extends Service {
    private SocketChannelServer mServer;

    @Override
    public void onCreate() {
        super.onCreate();
        mServer = SocketChannelServer.start(8080);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        mServer.pause();
        super.onDestroy();
    }
}
