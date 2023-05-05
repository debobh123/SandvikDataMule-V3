package com.artclave.sandvikdatamule;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.artclave.sandvikdatamule.storage.FileStorage;
import com.artclave.sandvikdatamule.util.Logger;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

public class AppSettings {

    public enum ServerType {
        Optimine,
        IoTHUB
    }

    public enum TransferMode {
        Automatic,
        WifiHotspot,
        WifiClient
    }

    private final static String TAG = AppSettings.class.getSimpleName();

    public static long getLatestSuccessfulConnectionTime()
    {
        final SharedPreferences sp = getSharedPreferences();
        return sp.getLong("latestSuccessfulConnectionTime", 1463560756000L); // default is "Wed, 18 May 2016 08:39:16 GMT"
    }

    public static void setLatestSuccessfulConnectionTime(long time) {
        final SharedPreferences sp = getSharedPreferences();
        sp.edit().putLong("latestSuccessfulConnectionTime", time).apply();
    }

    public static String getWifiHotspotSSID() {
        final SharedPreferences sp = getSharedPreferences();
        return sp.getString("ssid", "Sandvik");
    }
    public static String getWifiHotspotPSK() {
        final SharedPreferences sp = getSharedPreferences();
        return sp.getString("wpa", "12345678");
    }
    public static String getServerUrl() {
        final SharedPreferences sp = getSharedPreferences();
        return sp.getString("server_url", "http://192.168.1.100");
    }

    public static int getWifiChannel() {
        final SharedPreferences sp = getSharedPreferences();
        String channelStr = sp.getString("wifi_channel", "0");
        int channel = 0;
        try {
            channel = Integer.valueOf(channelStr);
        }
        catch (NumberFormatException fe) { }

        return channel;
    }

    public static ServerType getServerType() {
        final SharedPreferences sp = getSharedPreferences();
        String typeStr = sp.getString("server_type", "0");

        int type = 0;
        try {
            type = Integer.valueOf(typeStr);
        }
        catch (NumberFormatException fe) { }

        return ServerType.values()[type];
    }

    public static TransferMode getTransferMode() {
        final SharedPreferences sp = getSharedPreferences();
        String typeStr = sp.getString("transfer_mode", "0");

        int type = 0;
        try {
            type = Integer.valueOf(typeStr);
        }
        catch (NumberFormatException fe) { }

        return TransferMode.values()[type];
    }

    public static String getBlobStorageAccount() {
        final SharedPreferences sp = getSharedPreferences();
        return sp.getString("blobstorage_account", "testfdcdcuconfig");
    }

    public static String getBlobStorageKey() {
        final SharedPreferences sp = getSharedPreferences();
        return sp.getString("blobstorage_key", "CnLBJUkmUK6FKoxLDyuFSCsuyg/BO2ntjD3BCCsDR1G+nGxdo5e1zQML+9wUkSFWsb9odD0xC6UtLKRf3JhCgg==");
    }

    public static String getUserName() {
        final SharedPreferences sp = getSharedPreferences();
        return sp.getString("username", "ftp");
    }

    public static String getPassWord() {
        final SharedPreferences sp = getSharedPreferences();
        return sp.getString("password", "ftp");
    }

    public static boolean allowAnoymous() {
        final SharedPreferences sp = getSharedPreferences();
        return sp.getBoolean("allow_anonymous", false);
    }

    public static File getChrootDir() {
        final SharedPreferences sp = getSharedPreferences();
        String dirName = sp.getString("chrootDir", "");
        File chrootDir = new File(dirName);
        if (dirName.equals("")) {
            if (FileStorage.instance() == null) return null;
            //if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            chrootDir = new File(FileStorage.instance().getStorageRootPath().getAbsolutePath() + "/Sandvik/FtpRoot/");
            //} else {
            //    chrootDir = new File("/Sandvik/FtpRoot/");
            //}
        }

        FileStorage.CreateFolder(chrootDir.getAbsolutePath());

        if (!chrootDir.isDirectory()) {
            Logger.e(TAG, "getChrootDir: not a directory");
            return null;
        }

        FileStorage.CreateFolder(chrootDir.getAbsolutePath() + "/reports");
        FileStorage.CreateFolder(chrootDir.getAbsolutePath() + "/m2mreports");
        FileStorage.CreateFolder(chrootDir.getAbsolutePath() + "/configurations");
        FileStorage.CreateFolder(chrootDir.getAbsolutePath() + "/swpackages");


        return chrootDir;
    }

    public static String getConfigsDir(String deviceId)
    {
        String path = getChrootDirAsString() + "/configurations/" + deviceId;
        FileStorage.CreateFolder(path);
        return path;
    }

    public static String getChrootDirAsString() {
        try {
            File dirFile = getChrootDir();
            return dirFile != null ? dirFile.getCanonicalPath() : "";
        }
        catch (IOException ie)
        {
            return null;
        }
    }

    public static boolean setChrootDir(String dir) {
        File chrootTest = new File(dir);
        if (!chrootTest.isDirectory() || !chrootTest.canRead())
            return false;
        final SharedPreferences sp = getSharedPreferences();
        sp.edit().putString("chrootDir", dir).apply();
        return true;
    }

    public static int getPortNumber() {
        final SharedPreferences sp = getSharedPreferences();
        // TODO: port is always an number, so store this accordenly
        String portString = sp.getString("portNum", "2121");
        int port = Integer.valueOf(portString);
        // int port = Integer.valueOf("2121");
        return port;
    }

    public static boolean shouldTakeFullWakeLock() {
        final SharedPreferences sp = getSharedPreferences();
        return sp.getBoolean("stayAwake", false);
    }

    public static Set<String> getAutoConnectList() {
        SharedPreferences sp = getSharedPreferences();
        return sp.getStringSet("autoconnect_preference", new TreeSet<>());
    }

    /**
     * @return the SharedPreferences for this application
     */
    private static SharedPreferences getSharedPreferences() {
        final Context context = App.getAppContext();
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

}

