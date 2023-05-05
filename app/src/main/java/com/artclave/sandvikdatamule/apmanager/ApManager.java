package com.artclave.sandvikdatamule.apmanager;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;

import com.artclave.sandvikdatamule.util.Logger;
import com.artclave.sandvikdatamule.util.NetUtils;

import java.lang.reflect.Method;

/*

    ApManager is responsible to offer API for managing Wifi access point of the Android device.

 */

public abstract class ApManager {
    private final static String TAG = ApManager.class.getSimpleName();

    public abstract static class CompletionHandler {

        public enum FailReason {
            noPermission,
            inconsistentState,
            other
        }

        /**
         * Called when method call succeeded.
         */
        public abstract void onCallSucceeded();

        /**
         * Called when method call failed.
         */
        public abstract void onCallFailed(FailReason reason);

    }

    public abstract static class CompletionHandlerWithSameCallback extends CompletionHandler {
        public abstract void callback();

        @Override
        public void onCallSucceeded() {
            callback();
        }

        @Override
        public void onCallFailed(FailReason reason) {
            callback();
        }
    }

    Context mContext;

    ApManager(Context context) {
        mContext = context;
    }

    public static ApManager createApManager(Context applicationContext) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return new ApManagerOreo(applicationContext);
        } else {
            return new ApManagerPreOreo(applicationContext);
        }
    }

    // check whether wifi access point is on or off
    public boolean isApOn() {
        if (_isApOn()) {
            if (NetUtils.getLocalInetAddress() == null) {
                Logger.e(TAG, "No local ip address while AP is on!");
                return false;
            }
            return true;
        }
        return false;
    }

    public void enableAccessPoint(CompletionHandler callback) {
        if (!isPermissionOk()) {
            callback.onCallFailed(CompletionHandler.FailReason.noPermission);
            return;
        }
        doEnableAccessPoint(new CompletionHandler() {
            @Override
            public void onCallSucceeded() {
                // we have to delay calling callback until enabling the access point
                // is fully finished
                final int maxIteration = 20;
                Logger.d(TAG, "enabling access point: waiting for finish...");
                for (int i = 0; i < maxIteration; i++) {
                    if (!isApOn()) {
                        SystemClock.sleep(1000);
                        continue;
                    }
                    Logger.d(TAG, "enabling access point: done");
                    callback.onCallSucceeded();
                    return;
                }
                disableAccessPoint(new CompletionHandlerWithSameCallback() {
                    @Override
                    public void callback() {
                        callback.onCallFailed(FailReason.inconsistentState);
                    }
                });
            }

            @Override
            public void onCallFailed(FailReason reason) {
                callback.onCallFailed(reason);
            }
        });
    }

    public void disableAccessPoint(CompletionHandler callback) {
        if (!isPermissionOk()) {
            callback.onCallFailed(CompletionHandler.FailReason.noPermission);
            return;
        }
        doDisableAccessPoint(callback);
    }

    abstract void doEnableAccessPoint(CompletionHandler callback);

    abstract void doDisableAccessPoint(CompletionHandler callback);

    private boolean isPermissionOk() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.System.canWrite(mContext);
        }
        return true;
    }

    private boolean _isApOn() {
        WifiManager wifimanager = (WifiManager) mContext.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        try {
            Method method = wifimanager.getClass().getDeclaredMethod("isWifiApEnabled");
            method.setAccessible(true);
            return (Boolean) method.invoke(wifimanager);
        } catch (Throwable ignored) {
        }
        return false;
    }
}

