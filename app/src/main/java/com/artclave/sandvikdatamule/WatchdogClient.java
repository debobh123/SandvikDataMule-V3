package com.artclave.sandvikdatamule;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;

import com.artclave.sandvikdatamule.util.Logger;


/**
 * Created by JuhaM on 13.5.2016.
 */
public class WatchdogClient {
    public static final int MSG_RESTART_WATCHDOG_TIMER = 1;

    private Messenger mService = null;
    private ServiceConnection mConnection = null;

    private boolean mBounding = false;
    private boolean mBound = false;
    private Context c;
    static public String name;


    public WatchdogClient(String name, Context c)
    {
        this.c = c;
        this.name = name;
    }

    public void pulse()
    {
        if (!sendPulse()) {
            connect();
        }
    }

    private void connect()
    {
        if (!mBound && !mBounding)
        {
            Logger.d(name, "Connecting to service.");

            mBounding = true;
            mConnection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    mService = new Messenger(service);
                    mBounding = false;
                    mBound = true;
                    Logger.d(WatchdogClient.name, "Service connection succeeded.");
                    sendPulse();
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    mService= null;
                    mBounding = false;
                    mBound = false;
                    Logger.w(WatchdogClient.name, "Service connection FAILED.");
                }
            };

            Intent wdServiceIntent = new Intent();
            wdServiceIntent.setComponent(new ComponentName("com.sandvik.optimine.mulewatchdog", "com.sandvik.optimine.mulewatchdog.WatchdogService"));

            c.bindService(wdServiceIntent, mConnection, Context.BIND_IMPORTANT);
        }
    }

    private boolean sendPulse()
    {
        if (!mBound) return false;

        Message msg = Message.obtain(null, MSG_RESTART_WATCHDOG_TIMER, 0, 0);
        try {
            mService.send(msg);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Logger.d(name, "Pulse sent.");
        return true;
    }

}

