package com.artclave.sandvikdatamule.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;

import com.artclave.sandvikdatamule.App;

import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

public final class NetUtils {
    private final static String TAG = NetUtils.class.getSimpleName();

    /**
     * Gets the local ip address
     *
     * @return local ip address or null if not found
     */
    public static InetAddress getLocalInetAddress() {
        if (!isConnectedToLocalNetwork()) {
            Logger.e(TAG, "getLocalInetAddress called and no connection");
            return null;
        }

        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface
                    .getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface netInterface = networkInterfaces.nextElement();
                if ((netInterface.getName().contains("wlan")) || (netInterface.getName().contains("ap"))) {
                    Enumeration<InetAddress> addresses = netInterface.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        InetAddress address = addresses.nextElement();
                        if (!address.isLoopbackAddress() && !address.isLinkLocalAddress() && address instanceof Inet4Address)
                            return address;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Checks if we are connected to any network, WiFI, Ethernet or GSM.
     *
     * @return true if connected to any network
     */
    public static boolean isNetworkAvailable() {
        // TODO: fix this
        // THIS CODE CHECKS FOR ANY NETWORK INCLUDING GSM
        Context context = App.getAppContext();
        ConnectivityManager connectivityManager
                = (ConnectivityManager) context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();

        return activeNetworkInfo != null;
    }

    /**
     * Checks to see if we are connected to a local network, for instance wifi or ethernet
     *
     * @return true if connected to a local network
     */
    public static boolean isConnectedToLocalNetwork() {
        boolean connected = false;
        Context context = App.getAppContext();
        ConnectivityManager cm = (ConnectivityManager) context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        connected = ni != null
                && ni.isConnected()
                && (ni.getType() & (ConnectivityManager.TYPE_WIFI | ConnectivityManager.TYPE_ETHERNET)) != 0;
        if (!connected) {
            Logger.d(TAG, "isConnectedToLocalNetwork: see if it is an WIFI AP");
            WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            try {
                Method method = wm.getClass().getDeclaredMethod("isWifiApEnabled");
                connected = (Boolean) method.invoke(wm);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return connected;
    }

    /**
     * Checks to see if we are connected using wifi
     *
     * @return true if connected using wifi
     */
    public static boolean isConnectedUsingWifi() {
        Context context = App.getAppContext();
        ConnectivityManager cm = (ConnectivityManager) context.getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        return ni != null && ni.isConnected()
                && ni.getType() == ConnectivityManager.TYPE_WIFI;
    }


}

