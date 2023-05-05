package com.artclave.sandvikdatamule.apmanager;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Handler;

import com.android.dx.stock.ProxyBuilder;
import com.artclave.sandvikdatamule.util.Logger;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

final class ApManagerOreo extends ApManager {
    private final static String TAG = ApManagerOreo.class.getSimpleName();
    private static final int TETHERING_WIFI = 0;

    ApManagerOreo(Context context) {
        super(context);
    }

    @Override
    void doEnableAccessPoint(CompletionHandler callback) {
        File outputDir = mContext.getApplicationContext().getCodeCacheDir();
        Object proxy;
        try {
            proxy = ProxyBuilder.forClass(classOnStartTetheringCallback())
                    .dexCache(outputDir).handler(new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            switch (method.getName()) {
                                case "onTetheringStarted":
                                    callback.onCallSucceeded();
                                    break;
                                case "onTetheringFailed":
                                    callback.onCallFailed(CompletionHandler.FailReason.other);
                                    break;
                                default:
                                    ProxyBuilder.callSuper(proxy, method, args);
                            }
                            return null;
                        }
                    }).build();
        } catch (IOException e) {
            e.printStackTrace();
            callback.onCallFailed(CompletionHandler.FailReason.other);
            return;
        }
        ConnectivityManager manager = mContext.getApplicationContext().getSystemService(ConnectivityManager.class);

        Method method;
        try {
            method = manager.getClass().getDeclaredMethod("startTethering", int.class, boolean.class, classOnStartTetheringCallback(), Handler.class);
            if (method == null) {
                Logger.e(TAG, "startTetheringMethod is null");
            } else {
                // Wifi needs to be enabled before tethering because
                // after stopping tethering the system restores previous state i.e. Wifi on
                WifiManager wifimanager = (WifiManager) mContext.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                wifimanager.setWifiEnabled(true);
                method.invoke(manager, TETHERING_WIFI, false, proxy, null);
            }
        } catch (Exception e) {
            e.printStackTrace();
            callback.onCallFailed(CompletionHandler.FailReason.other);
        }
    }

    @Override
    void doDisableAccessPoint(CompletionHandler callback) {
        ConnectivityManager manager = (ConnectivityManager) mContext.getApplicationContext().getSystemService(ConnectivityManager.class);

        try {
            Method method = manager.getClass().getDeclaredMethod("stopTethering", int.class);
            if (method == null) {
                Logger.e(TAG, "stopTetheringMethod is null");
            } else {
                method.invoke(manager, TETHERING_WIFI);
            }
            WifiManager wifimanager = (WifiManager) mContext.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            wifimanager.setWifiEnabled(true);
            callback.onCallSucceeded();
        } catch (Exception e) {
            e.printStackTrace();
            callback.onCallFailed(CompletionHandler.FailReason.other);
        }
    }

    private Class classOnStartTetheringCallback() {
        try {
            return Class.forName("android.net.ConnectivityManager$OnStartTetheringCallback");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

}
