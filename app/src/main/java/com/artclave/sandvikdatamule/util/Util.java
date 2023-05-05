package com.artclave.sandvikdatamule.util;

import com.artclave.sandvikdatamule.AppSettings;
import com.artclave.sandvikdatamule.gui.main.MainActivity;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

abstract public class Util {
    final static String TAG = Util.class.getSimpleName();

    public static byte byteOfInt(int value, int which) {
        int shift = which * 8;
        return (byte) (value >> shift);
    }

    public static boolean hasValidClock()
    {
        boolean ret = (new Date().getTime() > AppSettings.getLatestSuccessfulConnectionTime());
        if (!ret)
        {
            Logger.w("Util", "No valid clock, refusing to enter WiFi access point mode.");
            MainActivity.noValidClock();
        }
        return ret;
    }

    public static String ipToString(int addr, String sep) {
        // myLog.l(Logger.DEBUG, "IP as int: " + addr);
        if (addr > 0) {
            StringBuffer buf = new StringBuffer();
            buf.append(byteOfInt(addr, 0)).append(sep).append(byteOfInt(addr, 1))
                    .append(sep).append(byteOfInt(addr, 2)).append(sep)
                    .append(byteOfInt(addr, 3));
            Logger.d(TAG, "ipToString returning: " + buf.toString());
            return buf.toString();
        } else {
            return null;
        }
    }

    public static InetAddress intToInet(int value) {
        byte[] bytes = new byte[4];
        for (int i = 0; i < 4; i++) {
            bytes[i] = byteOfInt(value, i);
        }
        try {
            return InetAddress.getByAddress(bytes);
        } catch (UnknownHostException e) {
            // This only happens if the byte array has a bad length
            return null;
        }
    }

    public static String ipToString(int addr) {
        if (addr == 0) {
            // This can only occur due to an error, we shouldn't blindly
            // convert 0 to string.
            Logger.e(TAG, "ipToString won't convert value 0");
            return null;
        }
        return ipToString(addr, ".");
    }

    public static String[] concatStrArrays(String[] a1, String[] a2) {
        String[] retArr = new String[a1.length + a2.length];
        System.arraycopy(a1, 0, retArr, 0, a1.length);
        System.arraycopy(a2, 0, retArr, a1.length, a2.length);
        return retArr;
    }

    public static void sleepIgnoreInterupt(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
        }
    }

    /**
     * Creates a SimpleDateFormat in the formatting used by ftp sever/client.
     */
    private static SimpleDateFormat createSimpleDateFormat() {
        SimpleDateFormat df = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        return df;
    }

    public static String getFtpDate(long time) {
        SimpleDateFormat df = createSimpleDateFormat();
        return df.format(new Date(time));
    }

    public static Date parseDate(String time) throws ParseException {
        SimpleDateFormat df = createSimpleDateFormat();
        return df.parse(time);
    }
}

