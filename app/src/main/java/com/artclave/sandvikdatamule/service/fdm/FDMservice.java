package com.artclave.sandvikdatamule.service.fdm;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;

import com.artclave.sandvikdatamule.App;
import com.artclave.sandvikdatamule.AppSettings;
import com.artclave.sandvikdatamule.IoTHUBClient;
import com.artclave.sandvikdatamule.R;
import com.artclave.sandvikdatamule.WatchdogClient;
import com.artclave.sandvikdatamule.apmanager.ApManager;
import com.artclave.sandvikdatamule.gui.FsNotification;
import com.artclave.sandvikdatamule.gui.log.TransferLogItem;
import com.artclave.sandvikdatamule.gui.main.MainActivity;
import com.artclave.sandvikdatamule.service.ftp.FsService;
import com.artclave.sandvikdatamule.storage.FileStorage;
import com.artclave.sandvikdatamule.storage.OutboxFile;
import com.artclave.sandvikdatamule.util.Logger;
import com.artclave.sandvikdatamule.util.Util;
import com.loopj.android.http.AsyncHttpResponseHandler;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;


public class FDMservice extends IntentService implements Runnable {
    private final static String TAG = FDMservice.class.getSimpleName();

    // defines the idle timeout in msec for the ftp mode before switching to wifi client mode
    private final static int FTP_IDLE_TIMEOUT = 60000;

    // defines the idle timeout in msec for the wifi client mode before switching to ftp mode
    private final static int WIFI_CLIENT_MODE_IDLE_TIMEOUT = 60000;

    // defines the timeout in msec before retrying to switch to the wifi client mode
    // after a failed attempt
    private final static int RETRY_WIFI_CLIENT_MODE_TIMEOUT = 180000;

    Timer outboxTimer;

    private boolean gotInternetConnection;
    private long wifiClientModeTimeout;
    private boolean latestWifiClientModeFailed = false;
    private long latestWifiClientModeEndTime = 0;
    protected static Thread fdmThread = null;
    private WatchdogClient watchdog;
    static private Thread.UncaughtExceptionHandler defaultExpHandler;
    private ApManager mApManager;
    private int pendingReportCount = 0;

    public FDMservice() {
        super("FDMService");
        mApManager = ApManager.createApManager(App.getAppContext());
        watchdog = new WatchdogClient("FDMWatchdogClient", this);
        Logger.i(TAG, "Constructor.");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new FDMserviceBinder(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        boolean startThread = true;
        int attempts = 2;
        while (fdmThread != null) {
            Logger.w(TAG, "Won't start, server thread exists");
            if (attempts > 0) {
                attempts--;
                Util.sleepIgnoreInterupt(500);
            } else {
                Logger.w(TAG, "Server thread already exists");
                startThread = false;
                break;
            }
        }
        if (startThread) {
            Logger.d(TAG, "Creating server thread");
            fdmThread = new Thread(this);
            fdmThread.start();
        }

        Notification notification = createNotification();
        startForeground(1, notification);

        defaultExpHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable e) {
                Logger.e("FDMService", "uncaughtException", e);
                Logger.scanLogs();
                defaultExpHandler.uncaughtException(thread, e);
            }
        });

        // Registering intent that before came from AndroidManifest
        registerIntentListeners();

        return START_STICKY;
    }

    @Override
    protected void onHandleIntent(Intent intent) {

    }

    @Override
    public void run() {
        Logger.d(TAG, "Server thread running");

        AppSettings.TransferMode transferMode = AppSettings.getTransferMode();
        if (transferMode == AppSettings.TransferMode.WifiClient) {
            disableAccessPoint(null);
        } else // automatic or WifiAp
        {
            enableAccessPoint(null);
        }

        outboxTimer = new Timer("OutboxTimer", true);
        outboxTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                checkOutgoingFiles();
            }
        }, 500, 10000); // first run after 0.5sec, then once per 10sec.
    }

    public void enableAccessPoint(ApManager.CompletionHandler callback) {
        mApManager.enableAccessPoint(
                new ApManager.CompletionHandler() {
                    @Override
                    public void onCallSucceeded() {
                        Logger.i(TAG, "Entering WiFi Access Point mode.");
                        App.getAppContext().sendBroadcast(new Intent(FsService.ACTION_START_FTPSERVER));
                        if (callback != null) {
                            callback.onCallSucceeded();
                        }
                    }

                    @Override
                    public void onCallFailed(ApManager.CompletionHandler.FailReason reason) {
                        Logger.i(TAG, String.format("Entering WiFi Access Point mode failed! Reason: %s", reason));
                        if (reason == ApManager.CompletionHandler.FailReason.noPermission) {
                            Logger.d(TAG, "Cannot enable WiFi Access Point, no write settings permission");
                            MainActivity.unableToChangeWifiState();
                        }
                        if (callback != null) {
                            callback.onCallFailed(reason);
                        }
                    }
                });
    }

    public void disableAccessPoint(ApManager.CompletionHandler callback) {
        Logger.d(TAG, "disableAccessPoint function");

        mApManager.disableAccessPoint(new ApManager.CompletionHandler() {
            @Override
            public void onCallSucceeded() {
                Logger.d(TAG, "Entering WiFi Client mode.");
                App.getAppContext().sendBroadcast(new Intent(FsService.ACTION_STOP_FTPSERVER));
                if (callback != null) {
                    callback.onCallSucceeded();
                }
            }

            @Override
            public void onCallFailed(ApManager.CompletionHandler.FailReason reason) {
                Logger.d(TAG, "Entering WiFi Client mode failed.");
                if (reason == ApManager.CompletionHandler.FailReason.noPermission) {
                    Logger.d(TAG, "Cannot disable WiFi Access Point, no write settings permission");
                    MainActivity.unableToChangeWifiState();
                }
                if (callback != null) {
                    callback.onCallFailed(reason);
                }
            }
        });
    }

    public void checkOutgoingFiles() {
        watchdog.pulse();
        final boolean isWifiApModeEnabled = mApManager.isApOn();

        if (FileStorage.instance() == null) {
            Logger.e(TAG, "FileStorage instance not yet created.");
            return;
        }

        // enable or disable ap if forced from settings UI.
        AppSettings.TransferMode transferMode = AppSettings.getTransferMode();
        if (transferMode == AppSettings.TransferMode.WifiClient && isWifiApModeEnabled) {
            disableAccessPoint(null);
            return;
        } else if (transferMode == AppSettings.TransferMode.WifiHotspot && !isWifiApModeEnabled) {
            enableAccessPoint(null);
            return;
        }

        // check if there are files to be deleted from blob
        if (AppSettings.getServerType() != AppSettings.ServerType.Optimine) {
            if (FsService.isNetworkAvailable()) {
                // try to delete the files from blob.
                String[] toBeRemoved = FileStorage.instance().getBlobFilesToBeRemoved();
                for (String removedFile :
                        toBeRemoved) {
                    if (IoTHUBClient.DeleteFileFromBlob(removedFile)) {
                        FileStorage.instance().removeBlobFromToBeRemovedList(removedFile);
                        watchdog.pulse();
                    } else // delete should fail only when no connection to blob, don't bother with rest of files.
                    {
                        break;
                    }
                }
            }
        }

        // check next outgoing files
        OutboxFile[] files = FileStorage.instance().getNextOutboxFiles();
        if (files != null && files.length > 0) {
            // build a distinct list of machines, we use it after sending all the reports, to
            // download incoming configuration files from the blobstorage.
            ArrayList<String> machines = new ArrayList<String>();
            IoTHUBClient client;
            try {
                client = new IoTHUBClient(AppSettings.getServerUrl());
            } catch (URISyntaxException e) {
                e.printStackTrace();
                Logger.e(TAG, "Invalid server url " + AppSettings.getServerUrl(), e);
                return;
            }
            do {
                pendingReportCount = FileStorage.instance().getPendingReportCount();
                Logger.d(TAG, "pending files count " + pendingReportCount);
                if (FsService.isNetworkAvailable()) {
                    for (OutboxFile file : files) {
                        if (!machines.contains(file.getDeviceName())) {
                            machines.add(file.getDeviceName());
                        }
                        try {
                            Logger.d(TAG, "Trying to send file " + file.getParentFile().getPath() + " from device " + file.getDeviceName());
                            MainActivity.setCloudTransferActive(true);

                            TransferLogItem i = MainActivity.getTransferLogController().getOrAddFile(file.getParentFile().getName());
                            if (i != null) {
                                i.setState(TransferLogItem.State.CloudUpload);
                                MainActivity.refreshTransferLogUI();
                            }

                            client.SendFile(App.getAppContext(), file, new AsyncHttpResponseHandler() {
                                @Override
                                public void onSuccess(int statusCode, cz.msebera.android.httpclient.Header[] headers, byte[] responseBody) {

                                }

                                @Override
                                public void onFailure(int statusCode, cz.msebera.android.httpclient.Header[] headers, byte[] responseBody, Throwable error) {

                                }

                                /*@Override
                                public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                                    MainActivity.setCloudTransferActive(false);
                                    //MainActivity.logViewPrint("File " + file.getParentFile().getName() + " successfully sent to IoTHUB.");
                                    Logger.d(TAG, "File " + file.getParentFile().getName() + " successfully sent to " + AppSettings.getServerType().toString());
                                    AppSettings.setLatestSuccessfulConnectionTime(new Date().getTime());
                                    watchdog.pulse();
                                    gotInternetConnection = true;
                                    wifiClientModeTimeout = new Date().getTime();
                                    pendingReportCount--;
                                    TransferLogItem i = MainActivity.getTransferLogController().getOrAddFile(file.getParentFile().getName());
                                    if (i != null) {
                                        i.setState(TransferLogItem.State.Uploaded);
                                        MainActivity.refreshTransferLogUI();
                                    }
                                    // file is deleted on succesful send.
                                    FileStorage.instance().fileUploadCompleted(file);
                                }*/

                                /*@Override
                                public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                                    MainActivity.setCloudTransferActive(false);
                                    watchdog.pulse();
                                    Logger.e(TAG, "File " + file.getParentFile().getName() + " sent FAILED, statusCode:" + statusCode);
                                    // certain errors means we don't have connection, in that case retry the file.
                                    if (statusCode == 0 || statusCode == 404) {
                                        Logger.e(TAG, "Unable to connect to server, probably using wrong network.");
                                        gotInternetConnection = false;
                                        TransferLogItem i = MainActivity.getTransferLogController().getOrAddFile(file.getParentFile().getName());
                                        if (i != null) {
                                            i.setState(TransferLogItem.State.Received);
                                            MainActivity.refreshTransferLogUI();
                                        }
                                    }
                                    // others mean we are done with the file, it failed.
                                    else {
                                        if (statusCode == 500) {
                                            Logger.w(TAG, "Report content was most likely invalid, we are done with the file.");
                                        }
                                        AppSettings.setLatestSuccessfulConnectionTime(new Date().getTime());
                                        gotInternetConnection = true;
                                        wifiClientModeTimeout = new Date().getTime();

                                        pendingReportCount--;
                                        TransferLogItem i = MainActivity.getTransferLogController().getOrAddFile(file.getParentFile().getName());
                                        if (i != null) {
                                            i.setState(TransferLogItem.State.Failed);
                                            MainActivity.refreshTransferLogUI();
                                        }
                                        // file is deleted on error send.
                                        FileStorage.instance().fileUploadFailed(file);
                                    }
                                }*/
                            });
                        } catch (IOException ie) {
                            gotInternetConnection = false;
                            Logger.e(TAG, "File " + file.getParentFile().getName() + " sent FAILED to IOException:" + ie.toString());
                            ie.printStackTrace();
                            TransferLogItem i = MainActivity.getTransferLogController().getOrAddFile(file.getParentFile().getName());
                            if (i != null) {
                                i.setState(TransferLogItem.State.Received);
                                MainActivity.refreshTransferLogUI();
                            }
                        } catch (IllegalArgumentException iae) {
                            Logger.e(TAG, "File " + file.getParentFile().getName() + " sent FAILED to IllegalArgumentException:" + iae.toString());
                            iae.printStackTrace();
                            TransferLogItem i = MainActivity.getTransferLogController().getOrAddFile(file.getParentFile().getName());
                            if (i != null) {
                                i.setState(TransferLogItem.State.Failed);
                                MainActivity.refreshTransferLogUI();
                            }
                            FileStorage.instance().fileUploadFailed(file);
                        }
                    }
                } else {
                    gotInternetConnection = false;
                    Logger.d(TAG, "checkOutgoingFiles: Network not available.");
                }

                files = FileStorage.instance().getNextOutboxFiles();
            }
            while (gotInternetConnection && files != null && files.length > 0); // keep processing until pending files and connection to IoT hub ok.
            // download incoming config files from blob, only for the machines that just sent a report.
            IoTHUBClient.DownloadNewFilesFromBlob(machines);

        } else {
            Logger.d(TAG, "checkOutgoingFiles: Nothing to send.");
        }
        MainActivity.setCloudTransferActive(false);

        pendingReportCount = FileStorage.instance().getPendingReportCount();

        // check if need to toggle wifi state to client/ap.
        if (AppSettings.getTransferMode() == AppSettings.TransferMode.Automatic) {
            if (pendingReportCount > 0 && isWifiApModeEnabled && !gotInternetConnection) {
                Logger.d(TAG, "something to send change to wifi client.");
                long apModeElapsed = new Date().getTime() - latestWifiClientModeEndTime;
                Logger.d(TAG, "apModeElapse " + apModeElapsed);
                if (!latestWifiClientModeFailed || apModeElapsed > RETRY_WIFI_CLIENT_MODE_TIMEOUT) {
                    Logger.d(TAG, "apModeElapse >" + apModeElapsed + " > " + RETRY_WIFI_CLIENT_MODE_TIMEOUT);
                    long ftpIdleTime = new Date().getTime() - FileStorage.instance().getLatestFtpActivityTime();
                    Logger.d(TAG, "aftpIdletime " + ftpIdleTime);
                    if (ftpIdleTime > FTP_IDLE_TIMEOUT) {
                        Logger.d(TAG, "Trying to disable AP.");
                       /* disableAccessPoint(new CompletionHandler() {
                            @Override
                            public void onCallSucceeded() {
                                Logger.d(TAG, "aftpIdletime >" + ftpIdleTime + " > " + FTP_IDLE_TIMEOUT);
                                wifiClientModeTimeout = new Date().getTime();
                            }

                            @Override
                            public void onCallFailed(FailReason reason) {
                                Logger.d(TAG, "Disabling AP has failed.");
                            }
                        });*/
                    }
                }
            } else if (!isWifiApModeEnabled && Util.hasValidClock()) {
                Logger.d(TAG, "nothing to send change to hotspot");
                long elapsed = new Date().getTime() - wifiClientModeTimeout;
                Logger.d(TAG, "elapsed " + elapsed);
                // if all reports sent or no internet connection in a defined timeout, go back to AP mode.
                if (pendingReportCount == 0 || (!gotInternetConnection && elapsed > WIFI_CLIENT_MODE_IDLE_TIMEOUT)) {
                    Logger.d(TAG, "Trying to enable AP.");
                    /*enableAccessPoint(new CompletionHandler() {
                        @Override
                        public void onCallSucceeded() {
                            Logger.d(TAG, "elapsed > " + elapsed + " > " + WIFI_CLIENT_MODE_IDLE_TIMEOUT);
                            latestWifiClientModeFailed = !gotInternetConnection;
                            latestWifiClientModeEndTime = new Date().getTime();
                            Logger.d(TAG, "latestWifiClientModeEndTime " + latestWifiClientModeEndTime);
                        }

                        @Override
                        public void onCallFailed(FailReason reason) {
                            Logger.d(TAG, "Enabling AP has failed.");
                        }
                    });*/
                }
            }
        }
    }

    @Override
    public void onDestroy() {
        Logger.d(TAG, "FDMservice Destroyed");
        Logger.scanLogs();

        super.onDestroy();

    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setAction("android.intent.action.MAIN");
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, 0);

        int resId = getResources().getIdentifier("optimine_icon", "drawable", getPackageName());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(FsNotification.CHANNEL_ID,
                    "DataBearer", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Sandvik DataBearer notifications");
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }

        return new NotificationCompat.Builder(this, FsNotification.CHANNEL_ID)
                .setTicker(getString(R.string.app_name))
                .setContentText(getString(R.string.app_name))
                .setContentIntent(pendingIntent)
                .setSmallIcon(resId)
                .setDefaults(Notification.FLAG_FOREGROUND_SERVICE | Notification.FLAG_ONGOING_EVENT)
                .setOngoing(true).build();
    }

    private void registerIntentListeners() {
        // Registering FTP server intent listener
        BroadcastReceiver br = new RequestStartStopReceiver();
        IntentFilter ftpIntentFilter = new IntentFilter();
        ftpIntentFilter.addAction(FsService.ACTION_START_FTPSERVER);
        ftpIntentFilter.addAction(FsService.ACTION_STOP_FTPSERVER);
        this.registerReceiver(br, ftpIntentFilter);

        FsNotification fsn = new FsNotification();
        IntentFilter fsIntentFilter = new IntentFilter();
        fsIntentFilter.addAction(FsService.ACTION_STARTED);
        fsIntentFilter.addAction(FsService.ACTION_STOPPED);
        fsIntentFilter.addAction(FsService.ACTION_FAILEDTOSTART);
        this.registerReceiver(fsn, fsIntentFilter);
    }
}
