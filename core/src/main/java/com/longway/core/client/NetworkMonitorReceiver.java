package com.longway.core.client;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.util.Log;

import java.util.LinkedList;

public class NetworkMonitorReceiver extends BroadcastReceiver {
    private static final String TAG = NetworkMonitorReceiver.class.getSimpleName();
    private static final String REASON = "unknown";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (ConnectivityManager.CONNECTIVITY_ACTION.equalsIgnoreCase(action)) {
            boolean lost = intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);
            if (lost) {
                return;
            }
            String reason = intent.getStringExtra(ConnectivityManager.EXTRA_REASON);
            if (reason == null) {
                reason = REASON;
            }
            int type = intent.getIntExtra(ConnectivityManager.EXTRA_NETWORK_TYPE, -1);
            Log.e(TAG, "connect:" + (type != -1) + ",type:" + type + ",reason:" + reason);
            notifyNetworkMonitor(type != -1, type, reason);
        }
    }

    public static NetworkMonitorReceiver registerNetworkMonitorReceiver(Context context) {
        NetworkMonitorReceiver networkMonitorReceiver = new NetworkMonitorReceiver();
        IntentFilter intentFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        context.registerReceiver(networkMonitorReceiver, intentFilter);
        return networkMonitorReceiver;
    }

    public static void unRegisterNetworkMonitorReceiver(Context context, NetworkMonitorReceiver networkMonitorReceiver) {
        context.unregisterReceiver(networkMonitorReceiver);
    }


    private void notifyNetworkMonitor(boolean success, int type, String reason) {
        synchronized (mObservable) {
            Object[] networkMonitors = mObservable.toArray();
            if (networkMonitors != null) {
                final int len = networkMonitors.length;
                for (int i = 0; i < len; i++) {
                    NetworkMonitor networkMonitor = (NetworkMonitor) networkMonitors[i];
                    if (success) {
                        networkMonitor.onConnect(type);
                    } else {
                        networkMonitor.onDisconnected(reason);
                    }
                }
            }
        }
    }

    private static final LinkedList<NetworkMonitor> mObservable = new LinkedList();

    public static void registerNetworkMonitor(NetworkMonitor networkMonitor) {
        if (networkMonitor == null) {
            throw new NullPointerException("networkMonitor==null.");
        }
        synchronized (mObservable) {
            if (!mObservable.contains(networkMonitor)) {
                mObservable.add(networkMonitor);
            }
        }
    }

    public static void unregisterNetworkMonitor(NetworkMonitor networkMonitor) {
        if (networkMonitor == null) {
            throw new NullPointerException("networkMonitor==null.");
        }
        synchronized (mObservable) {
            if (mObservable.contains(networkMonitor)) {
                mObservable.remove(networkMonitor);
            }
        }
    }
}
