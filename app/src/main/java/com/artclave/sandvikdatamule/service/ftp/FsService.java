package com.artclave.sandvikdatamule.service.ftp;


import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;

import com.artclave.sandvikdatamule.AppSettings;
import com.artclave.sandvikdatamule.storage.FileStorage;
import com.artclave.sandvikdatamule.util.Logger;
import com.artclave.sandvikdatamule.util.NetUtils;
import com.artclave.sandvikdatamule.util.Util;

import org.apache.ftpserver.ConnectionConfigFactory;
import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.Authority;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.Ftplet;
import org.apache.ftpserver.ftplet.UserManager;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.message.MessageResource;
import org.apache.ftpserver.message.MessageResourceFactory;
import org.apache.ftpserver.usermanager.impl.BaseUser;
import org.apache.ftpserver.usermanager.impl.WritePermission;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class FsService extends Service implements Runnable {
    private static final String TAG = FsService.class.getSimpleName();

    // Service will (global) broadcast when server start/stop
    static public final String ACTION_STARTED = "com.artclave.sandvikdatamule.FTPSERVER_STARTED";
    static public final String ACTION_STOPPED = "com.artclave.sandvikdatamule.FTPSERVER_STOPPED";
    static public final String ACTION_FAILEDTOSTART = "com.artclave.sandvikdatamule.FTPSERVER_FAILEDTOSTART";

    // RequestStartStopReceiver listens for these actions to start/stop this server
    static public final String ACTION_START_FTPSERVER = "com.artclave.sandvikdatamule.ACTION_START_FTPSERVER";
    static public final String ACTION_STOP_FTPSERVER = "com.artclave.sandvikdatamule.ACTION_STOP_FTPSERVER";

    protected static Thread serverThread = null;
    protected boolean shouldExit = false;

    protected ServerSocket listenSocket;

    // The server thread will check this often to look for incoming
    // connections. We are forced to use non-blocking accept() and polling
    // because we cannot wait forever in accept() if we want to be able
    // to receive an exit signal and cleanly exit.
    public static final int WAKE_INTERVAL_MS = 1000; // milliseconds

    private FtpServer server = null;
    private FtpServerFactory serverFactory = null;
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock = null;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        shouldExit = false;
        int attempts = 4;
        // The previous server thread may still be cleaning up, wait for it to finish.
        while (serverThread != null) {
            Logger.w(TAG, "Won't start, server thread exists");
            if (attempts > 0) {
                attempts--;
                Util.sleepIgnoreInterupt(500);
            } else {
                Logger.w(TAG, "Server thread already exists");
                return START_STICKY;
            }
        }
        Logger.d(TAG, "Creating server thread");
        serverThread = new Thread(this);
        serverThread.start();
        return START_STICKY;
    }

    public static boolean isRunning() {
        // return true if and only if a server Thread is running
        if (serverThread == null) {
            Logger.d(TAG, "Server is not running (null serverThread)");
            return false;
        }
        if (!serverThread.isAlive()) {
            Logger.d(TAG, "serverThread non-null but !isAlive()");
            return false;
        } else {
            Logger.d(TAG, "Server is alive");
        }
        return true;
    }

    @Override
    public void onDestroy() {
        Logger.i(TAG, "onDestroy() Stopping server");
        shouldExit = true;
        if (serverThread == null) {
            Logger.w(TAG, "Stopping with null serverThread");
            return;
        }
        serverThread.interrupt();
        try {
            serverThread.join(10000); // wait 10 sec for server thread to finish
        } catch (InterruptedException e) {
        }
        if (serverThread.isAlive()) {
            Logger.w(TAG, "Server thread failed to exit");
            // it may still exit eventually if we just leave the shouldExit flag set
        } else {
            Logger.d(TAG, "serverThread join()ed ok");
            serverThread = null;
        }
        try {
            if (listenSocket != null) {
                Logger.i(TAG, "Closing listenSocket");
                listenSocket.close();
            }
        } catch (IOException e) {
        }

        if (wifiLock != null) {
            Logger.d(TAG, "onDestroy: Releasing wifi lock");
            wifiLock.release();
            wifiLock = null;
        }
        if (wakeLock != null) {
            Logger.d(TAG, "onDestroy: Releasing wake lock");
            wakeLock.release();
            wakeLock = null;
        }
        Logger.d(TAG, "FTPServerService.onDestroy() finished");
    }

    @Override
    public void run() {
        Logger.d(TAG, "Server thread running");

        int waitNetwork = 0;
        // wait a bit if accesspoint is not up yet.
        while (!NetUtils.isConnectedToLocalNetwork()) {

            if (waitNetwork++ > 10) {
                Logger.w(TAG, "run: There is no local network, bailing out");
                stopSelf();
                sendBroadcast(new Intent(ACTION_FAILEDTOSTART));
                return;
            }
            SystemClock.sleep(1000);
        }

        // @TODO: when using ethernet, is it needed to take wifi lock?
        takeWifiLock();
        takeWakeLock();

        // A socket is open now, so the FTP server is started, notify rest of world
        Logger.i(TAG, "Ftp Server up and running, broadcasting ACTION_STARTED");
        sendBroadcast(new Intent(ACTION_STARTED));

        while (!shouldExit) {
            if (serverFactory == null || server == null || server.isStopped()) {
                try {
                    serverFactory = new FtpServerFactory();
                    MessageResource msg = new MessageResourceFactory().createMessageResource();
                    FtpServerCustomMessageResource customMsgs = new FtpServerCustomMessageResource(msg);
                    serverFactory.setMessageResource(customMsgs);

                    UserManager usermgr = serverFactory.getUserManager();
                    BaseUser adminUser = new BaseUser();
                    adminUser.setName(AppSettings.getUserName());
                    adminUser.setPassword(AppSettings.getPassWord());
                    adminUser.setEnabled(true);
                    adminUser.setHomeDirectory(AppSettings.getChrootDirAsString());

                    List<Authority> auth = new ArrayList<>();
                    auth.add(new WritePermission());
                    adminUser.setAuthorities(auth);

                    usermgr.save(adminUser);

                    // add diag user.
                    BaseUser diagUser = new BaseUser();
                    diagUser.setName("diag");
                    diagUser.setPassword("diag");
                    diagUser.setEnabled(true);
                    diagUser.setHomeDirectory(FileStorage.instance().getStorageRootPath().getAbsolutePath());
                    diagUser.setAuthorities(auth);
                    usermgr.save(diagUser);


                    if (AppSettings.allowAnoymous()) {
                        ConnectionConfigFactory connectionConfigFactory = new ConnectionConfigFactory();
                        connectionConfigFactory.setAnonymousLoginEnabled(true);
                        serverFactory.setConnectionConfig(connectionConfigFactory.createConnectionConfig());

                        BaseUser anon = new BaseUser();
                        anon.setName("anonymous");
                        anon.setEnabled(true);
                        anon.setHomeDirectory(AppSettings.getChrootDirAsString());
                        usermgr.save(anon);
                    }

                    HashMap<String, Ftplet> notifications = new HashMap<>();
                    notifications.put("notifications", new FtpNotifications());
                    serverFactory.setFtplets(notifications);

                    ListenerFactory factory = new ListenerFactory();
                    // set the port of the listener
                    factory.setPort(AppSettings.getPortNumber());
                    // replace the default listener
                    serverFactory.addListener("default", factory.createListener());
                    // start the server
                    server = serverFactory.createServer();
                    server.start();
                } catch (FtpException fe) {
                }
            }
            try {
                // TODO: think about using ServerSocket, and just closing
                // the main socket to send an exit signal
                Thread.sleep(WAKE_INTERVAL_MS);
            } catch (InterruptedException e) {
                Logger.d(TAG, "Thread interrupted");
            }
        }

        if (server != null) {
            server.stop();
        }

        shouldExit = false; // we handled the exit flag, so reset it to acknowledge
        Logger.d(TAG, "Exiting cleanly, returning from run()");

        stopSelf();
        sendBroadcast(new Intent(ACTION_STOPPED));
    }

/*
*
     * Takes the wake lock
     * <p>
     * Many devices seem to not properly honor a PARTIAL_WAKE_LOCK, which should prevent
     * CPU throttling. For these devices, we have a option to force the phone into a full
     * wake lock.
*/


    private void takeWakeLock() {
        if (wakeLock == null) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (AppSettings.shouldTakeFullWakeLock()) {
                Logger.d(TAG, "takeWakeLock: Taking full wake lock");
                wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, TAG);
            } else {
                Logger.d(TAG, "maybeTakeWakeLock: Taking parial wake lock");
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
            }
            wakeLock.setReferenceCounted(false);
        }
        wakeLock.acquire();
    }

    private void takeWifiLock() {
        Logger.d(TAG, "takeWifiLock: Taking wifi lock");
        if (wifiLock == null) {
            WifiManager manager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            wifiLock = manager.createWifiLock(TAG);
            wifiLock.setReferenceCounted(false);
        }
        wifiLock.acquire();
    }


/**
     * Checks if we are connected to any network, WiFI, Ethernet or GSM.
     *
     * @return*/


    public static boolean isNetworkAvailable() {
        if (AppSettings.getServerType() == AppSettings.ServerType.Optimine) {
            return NetUtils.isConnectedUsingWifi();
        } else {
            return NetUtils.isNetworkAvailable();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        Logger.d(TAG, "user has removed my activity, we got killed! restarting...");
        Intent restartService = new Intent(getApplicationContext(), this.getClass());
        restartService.setPackage(getPackageName());
        PendingIntent restartServicePI = PendingIntent.getService(
                getApplicationContext(), 1, restartService, PendingIntent.FLAG_ONE_SHOT);
        AlarmManager alarmService = (AlarmManager) getApplicationContext()
                .getSystemService(Context.ALARM_SERVICE);
        alarmService.set(AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 2000, restartServicePI);
    }

}

