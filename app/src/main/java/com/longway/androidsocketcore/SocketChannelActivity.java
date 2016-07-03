package com.longway.androidsocketcore;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

import com.longway.core.R;
import com.longway.core.client.ConnectListener;
import com.longway.core.client.MessageHandler;
import com.longway.core.client.MessageInterceptor;
import com.longway.core.client.MessageReceiptHandler;
import com.longway.core.client.NetworkMonitor;
import com.longway.core.client.PingListener;
import com.longway.core.client.ReconnectedListener;
import com.longway.core.client.SocketChannelClient;


/*********************************
 * Created by longway on 16/6/23 下午3:32.
 * packageName:com.longway.multiprocess.socket.socketChannel
 * projectName:demo
 * Email:longway1991117@sina.com
 ********************************/
public class SocketChannelActivity extends Activity {
    private static final String TAG = SocketChannelActivity.class.getSimpleName();
    private SocketChannelClient mClient;
    private EditText mContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_socket_channel);
        mContent = (EditText) findViewById(R.id.content);
        mClient = SocketChannelClient.newClient(getApplicationContext(), "192.168.1.101", 8080);
        mClient.registerMessageHandler(new MessageHandler() {
            @Override
            public void onReceiveMessage(byte[] message) {
                Log.d(TAG, new String(message));
            }
        });
        mClient.registerConnectListener(new ConnectListener() {
            @Override
            public void connectToServerFail(String reason) {
                Log.d(TAG, reason);
            }

            @Override
            public void connectToServerSuccess(String detail) {
                Log.d(TAG, detail);
            }
        });
        mClient.registerReconnectedListener(new ReconnectedListener() {
            @Override
            public void retryAttemptAfter(int second) {
                Log.d(TAG, second + "");
            }

            @Override
            public void retryAttemptFail(String reason) {
                Log.d(TAG, reason);
            }
        });
        mClient.registerMessageInterceptor(new MessageInterceptor() {
            @Override
            public byte[] onInterceptor(byte[] msg) {
                Log.d(TAG, new String(msg));
                return msg;
            }
        });
        mClient.registerNetMonitor(new NetworkMonitor() {
            @Override
            public void onConnect(int type) {
                Log.d(TAG, type + "");
            }

            @Override
            public void onDisconnected(String reason) {
                Log.d(TAG, reason);
            }
        });
        mClient.registerMessageReceiptHandler(new MessageReceiptHandler() {
            @Override
            public void onSendMessageFail(byte[] msg) {
                Log.e(TAG, "onSendMessageFail: " + msg.length);
            }
        });
        mClient.registerPingListener(new PingListener() {
            @Override
            public void ping(String ping) {
                Log.d(TAG, ping);
            }
        });
        mClient.setPing(" ");
        mClient.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mClient.close();
    }

    public void send(View view) {
        mClient.sendMsg(mContent.getText().toString());
    }
}
