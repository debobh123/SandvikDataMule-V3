package com.artclave.sandvikdatamule.apmanager;

import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;

import com.artclave.sandvikdatamule.AppSettings;
import com.artclave.sandvikdatamule.util.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

class ApManagerPreOreo extends ApManager {

    private final static String TAG = ApManagerPreOreo.class.getSimpleName();

    ApManagerPreOreo(Context context) {
        super(context);
    }

    @Override
    void doEnableAccessPoint(CompletionHandler callback) {
        try {
            WifiManager wifimanager = (WifiManager) mContext.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiConfiguration wificonfiguration = null;

            wifimanager.setWifiEnabled(false);

            Method getConfigMethod = wifimanager.getClass().getMethod("getWifiApConfiguration");
            WifiConfiguration wifiConfig = (WifiConfiguration) getConfigMethod.invoke(wifimanager);
            wifiConfig.SSID = AppSettings.getWifiHotspotSSID();
            wifiConfig.preSharedKey = AppSettings.getWifiHotspotPSK();

            // try to set wifi channel, some phones use apChannel and some channel field. does not work at all with some.
            int channel = AppSettings.getWifiChannel();
            try {
                Field wcFreq = WifiConfiguration.class.getField("apChannel");
                wcFreq.setInt(wifiConfig, channel);
                Logger.i(TAG, "WiFi Channel set to value " + channel + " using 'apChannel' field.");
            } catch (Exception e) {
                Logger.i(TAG, "Unable to set wifi channel with field 'apChannel', it happens on some devices trying field 'channel' next.");
                try {
                    Field wcFreq = WifiConfiguration.class.getField("channel");
                    wcFreq.setInt(wifiConfig, channel);
                    Logger.i(TAG, "WiFi Channel set to value " + channel + " using 'channel' field.");
                } catch (Exception e2) {
                    Logger.e(TAG, "Unable to set wifi channel with field 'channel', give up and use the default channel.", e2);
                }
            }

            Method setConfigMethod = wifimanager.getClass().getMethod("setWifiApConfiguration", WifiConfiguration.class);
            setConfigMethod.invoke(wifimanager, wifiConfig);

            Method method = wifimanager.getClass().getMethod("setWifiApEnabled", WifiConfiguration.class, boolean.class);
            method.invoke(wifimanager, wificonfiguration, true);
            callback.onCallSucceeded();
        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
            callback.onCallFailed(CompletionHandler.FailReason.noPermission);
        } catch (Throwable e) {
            e.printStackTrace();
            Logger.d(TAG, "Error at enabling access point");
            callback.onCallFailed(CompletionHandler.FailReason.other);
        }
    }

    @Override
    void doDisableAccessPoint(CompletionHandler callback) {
        Logger.d("ApManager:", "disableAccessPoint function");
        try {
            WifiManager wifimanager = (WifiManager) mContext.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiConfiguration wificonfiguration = null;

            wifimanager.setWifiEnabled(false);
            Logger.d(TAG, "put Hotspot off");
            Method method = wifimanager.getClass().getMethod("setWifiApEnabled", WifiConfiguration.class, boolean.class);
            method.invoke(wifimanager, wificonfiguration, false);
            wifimanager.setWifiEnabled(true);
            callback.onCallSucceeded();
        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
            callback.onCallFailed(CompletionHandler.FailReason.noPermission);
        } catch (Throwable e) {
            e.printStackTrace();
            Logger.d(TAG, "Error at disabling access point");
            callback.onCallFailed(CompletionHandler.FailReason.other);
        }
    }
}
