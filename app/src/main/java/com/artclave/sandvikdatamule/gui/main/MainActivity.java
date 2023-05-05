package com.artclave.sandvikdatamule.gui.main;

/*
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.artclave.sandvikdatamule.R;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }
}*/
import android.Manifest;
import android.app.ActivityManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.PowerManager;
import android.os.StrictMode;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.artclave.sandvikdatamule.App;
import com.artclave.sandvikdatamule.AppSettings;
import com.artclave.sandvikdatamule.R;
import com.artclave.sandvikdatamule.gui.log.TransferLogController;
import com.artclave.sandvikdatamule.gui.log.TransferLogItem;
import com.artclave.sandvikdatamule.gui.log.TransferLogListView;
//import com.artclave.sandvikdatamule.gui.settings.FsPreferenceActivity;
import com.artclave.sandvikdatamule.service.fdm.FDMservice;
import com.artclave.sandvikdatamule.service.ftp.FsService;
import com.artclave.sandvikdatamule.storage.FileStorage;
import com.artclave.sandvikdatamule.storage.IFileStorageListener;
import com.artclave.sandvikdatamule.storage.OutboxFile;
import com.artclave.sandvikdatamule.util.Logger;
import com.artclave.sandvikdatamule.util.NetUtils;

import java.io.File;
import java.io.FileFilter;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements IFileStorageListener {
    private final static String TAG = MainActivity.class.getSimpleName();

    private static Handler handler;    // Handlers for runnable
    private static boolean isActive = false;

    private static MachineController machineList = null;

    private static TransferLogController transferLogCtrl = null;

    private TextView uiPendingReportsValue;
    private static boolean isCloudTransferActive = false;

    private static TextView connectionTypeLabel;

    private static ImageView cloudTransferStateIcon;
    public static FrameLayout logViewFrame;


    static private final Thread.UncaughtExceptionHandler defaultExpHandler = Thread.getDefaultUncaughtExceptionHandler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_SETTINGS)) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_SETTINGS}, 121);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            FileStorage.createInstance(getApplicationContext().getFilesDir());
        } else {
            FileStorage.createInstance(Environment.getExternalStorageDirectory());
        }

        setContentView(R.layout.mainpage);

        logViewFrame = findViewById(R.id.logViewFrame);

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable e) {
                Logger.e(TAG, "uncaughtException", e);
                Logger.scanLogs();
                defaultExpHandler.uncaughtException(thread, e);
            }
        });
        Logger.i(TAG, "onCreate()");

        // prompt user to disable battery optimizations for databearer.
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!pm.isIgnoringBatteryOptimizations(getPackageName())) {
                //  Prompt the user to disable battery optimization
                Logger.i(TAG, "Battery optimizations are not disabled, prompting user.");
                Toast.makeText(this, R.string.battery_optimization_info_1, Toast.LENGTH_LONG).show();
                try {
                    startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS));
                } catch (ActivityNotFoundException e) {
                    Logger.e(TAG, "This device does not support automated battery optimization disable, take user to settings page.", e);
                    try {
                        startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                        Toast.makeText(this, R.string.battery_optimization_info_2, Toast.LENGTH_LONG).show();
                    } catch (ActivityNotFoundException e2) {
                        Logger.e(TAG, "This device does not support any kind of battery optimization disabling, nothing we can do.", e2);
                        Toast.makeText(this, R.string.battery_optimization_info_3, Toast.LENGTH_LONG).show();
                    }
                }
            } else {
                Logger.i(TAG, "Battery optimizations already disabled.");
            }
        }

        if (machineList == null) {
            machineList = new MachineController();
        }
        if (transferLogCtrl == null) {
            transferLogCtrl = new TransferLogController();
            addExistingFilesToTransferLog();
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("");
        setSupportActionBar(toolbar);

        DataTransferListViewAdapter dataTransferViewAdapter = new DataTransferListViewAdapter(App.getAppContext());
        machineList.setListViewAdapter(this, dataTransferViewAdapter);
        ListView dataTransferList = findViewById(R.id.dataTransferListView);
        dataTransferList.setAdapter(dataTransferViewAdapter);

        TransferLogListView transferLogView = new TransferLogListView(App.getAppContext());
        transferLogCtrl.setParentView(this, transferLogView);
        ListView transferLogList = findViewById(R.id.logListView);
        transferLogList.setAdapter(transferLogView);


        uiPendingReportsValue = findViewById(R.id.pendingReportsValue);
        connectionTypeLabel = findViewById(R.id.connectionTypeHeader);

        FileStorage.instance().addListener(this);

        // Android 6.0 permission for write settings
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.System.canWrite(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                intent.setData(Uri.parse("package:" + MainActivity.this.getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        }

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        cloudTransferStateIcon = findViewById(R.id.cloudConnStateIcon);

        handler = new Handler();

        Logger.d(TAG, "Started Sandvik DataMule ver. " + App.getVersion());
        startfdmService();

        Button logButton = findViewById(R.id.logButton);
        logButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                logViewFrame.setVisibility(View.VISIBLE);

            }
        });

        setCloudTransferActive(isCloudTransferActive);
        refreshConnectionLabel();
        onMachineMetaDataChanged(null);

        isActive = true;
    }


    static public TransferLogController getTransferLogController() {
        return transferLogCtrl;
    }

    private void addExistingFilesToTransferLog() {

        // get all files from outbox and m2moutbox
        ArrayList<File> files = new ArrayList<File>();
        // get files from outbox
        new File(FileStorage.instance().getOutboxPath()).listFiles(new FileFilter() {
                                                                       @Override
                                                                       public boolean accept(File pathname) {
                                                                           if (pathname.isFile()) {
                                                                               files.add(pathname);
                                                                           }
                                                                           return false;
                                                                       }
                                                                   }
        );
        // get files from m2moutbox
        new File(FileStorage.instance().getM2MOutboxPath()).listFiles(new FileFilter() {
                                                                          @Override
                                                                          public boolean accept(File pathname) {
                                                                              if (pathname.isFile()) {
                                                                                  files.add(pathname);
                                                                              }
                                                                              return false;
                                                                          }
                                                                      }
        );

        // iterate received files in outbox and m2moutbox, add to transferlog with Received state.
        for (File file : files) {
            TransferLogItem i = transferLogCtrl.getOrAddFile(file.getName());
            if (i != null) {
                i.setState(TransferLogItem.State.Received);
            }
        }

        // iterate all files from Sent path and add to transferlog with Uploaded state.
        new File(FileStorage.instance().getSentFilesPath()).listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                if (pathname.isFile()) {
                    TransferLogItem i = transferLogCtrl.getOrAddFile(pathname.getName());
                    if (i != null) {
                        i.setState(TransferLogItem.State.Uploaded);
                    }
                }
                return false;
            }
        });

        transferLogCtrl.sortFiles();
        transferLogCtrl.cleanOldFiles();

    }

    @Override
    public void onBackPressed() {
        if (logViewFrame.getVisibility() == View.VISIBLE) {
            logViewFrame.setVisibility(View.GONE);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Logger.v(TAG, "Permission: " + permissions[0] + "was " + grantResults[0]);
            //resume tasks needing this permission
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public void onMachineMetaDataChanged(String machineSerial) {
        try {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    uiPendingReportsValue.setText(String.valueOf(FileStorage.instance().getPendingReportCount()));
                }
            });
        } catch (Exception ignored) {
        } // silently ignore, this may happen occasionally if Activity is closed at the same time we execute this.

        setCloudTransferActive(isCloudTransferActive);
    }

    public static void refreshConnectionLabel() {
        try {
            if (!isActive) return;

            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (AppSettings.getServerType() == AppSettings.ServerType.Optimine) {
                        connectionTypeLabel.setText(R.string.optimine_connection);
                    } else {
                        connectionTypeLabel.setText(R.string.iothub_connection);
                    }
                }
            });
        } catch (Exception ignored) {
        } // silently ignore, this may happen occasionally if Activity is closed at the same time we execute this.
    }

    public static void unableToChangeWifiState() {
        Logger.d(TAG, "Unable to change Wifi state");
        MainActivity.showWarning("You must allow DataBearer to change system settings.");
    }

    public static void noValidClock() {
        Logger.d(TAG, "No valid clock");
        MainActivity.showWarning("Clock time/date is invalid. Connect internet to synchronize or set manually.");
    }

    private static void showWarning(String msg) {
        try {
            if (!isActive) return;

            handler.post(new Runnable() {
                @Override
                public void run() {
                    Toast toast = Toast.makeText(App.getAppContext(),
                            msg, Toast.LENGTH_LONG);
                    toast.setGravity(Gravity.CENTER, 0, 0);
                    toast.show();
                }
            });
        } catch (Exception ignored) {
        } // silently ignore, this may happen occasionally if Activity is closed at the same time we execute this.

    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
               /* Intent intent = new Intent(this, FsPreferenceActivity.class);
                this.startActivity(intent);*/
                break;

            default:
                return super.onOptionsItemSelected(item);
        }

        return true;
    }

    public static void setCloudTransferActive(boolean active) {
        isCloudTransferActive = active;
        try {
            if (!isActive) return;

            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (isCloudTransferActive) {
                        cloudTransferStateIcon.setImageResource(R.drawable.sendreceive_100x100);
                        cloudTransferStateIcon.setVisibility(View.VISIBLE);
                    } else {
                        if (FileStorage.instance().getPendingReportCount() > 0) {
                            cloudTransferStateIcon.setImageResource(R.drawable.pending_black_100x100);
                            cloudTransferStateIcon.setVisibility(View.VISIBLE);
                        } else {
                            cloudTransferStateIcon.setVisibility(View.INVISIBLE);
                        }
                    }
                }
            });
        } catch (Exception ignored) {
        } // silently ignore, this may happen occasionally if Activity is closed at the same time we execute this.
    }

    private static void refreshMachineListUI() {
        try {
            if (!isActive) return;

            handler.post(new Runnable() {
                @Override
                public void run() {
                    machineList.refreshUI();
                }
            });
        } catch (Exception ignored) {
        } // silently ignore, this may happen occasionally if Activity is closed at the same time we execute this.
    }

    public static void refreshTransferLogUI() {
        try {
            if (!isActive) return;

            handler.post(new Runnable() {
                @Override
                public void run() {
                    transferLogCtrl.refreshUI();
                }
            });
        } catch (Exception ignored) {
        } // silently ignore, this may happen occasionally if Activity is closed at the same time we execute this.
    }

    public static void ftpDeviceConnected(String ip) {
        MachineState m = machineList.addMachineIP(ip);
        m.setConnectionState(MachineState.ConnectionState.Idle);
        refreshMachineListUI();
    }

    public static void ftpDeviceDisconnected(String ip) {
        String serial = machineList.getMachineSerial(ip);
        if (serial != null) {
            MachineState m = machineList.getMachine(serial);
            if (m != null) {
                m.setConnectionState(MachineState.ConnectionState.Disconnected);
                refreshMachineListUI();
            }
        }
    }

    public static void ftpFileReceiveStarted(String ip, String filename) {
        OutboxFile.OutboxFileMetaData fileInfo = OutboxFile.parseFileName(filename);
        if (fileInfo.IsValid) {
            MachineState m = machineList.associateMachineSerial(ip, fileInfo.DeviceName);
            m.setConnectionState(MachineState.ConnectionState.Receiving);
            refreshMachineListUI();

            TransferLogItem i = transferLogCtrl.getOrAddFile(filename);
            if (i != null) {
                i.setState(TransferLogItem.State.FtpDownload);
                refreshTransferLogUI();
            }
        }
    }

    public static void ftpFileReceiveCompleted(String ip, String filename, boolean succeeded) {
        OutboxFile.OutboxFileMetaData fileInfo = OutboxFile.parseFileName(filename);
        if (fileInfo.IsValid) {
            MachineState m = machineList.associateMachineSerial(ip, fileInfo.DeviceName);
            m.setConnectionState(MachineState.ConnectionState.Idle);
            refreshMachineListUI();

            TransferLogItem i = transferLogCtrl.getOrAddFile(filename);
            if (i != null) {
                i.setState(TransferLogItem.State.Received);
                refreshTransferLogUI();
            }
        }
    }

    private void updateRunningState() {
        Resources res = getResources();
        if (FsService.isRunning()) {
            // Fill in the FTP server address
            InetAddress address = NetUtils.getLocalInetAddress();
            if (address == null) {
                Logger.v(TAG, "Unable to retrieve wifi ip address");
                return;
            }
            String ipText = "ftp://" + address.getHostAddress() + ":"
                    + AppSettings.getPortNumber() + "/";
            String summary = res.getString(R.string.running_summary_started, ipText);
        }
    }

    /**
     * This receiver will check FTPServer.ACTION* messages and will update the button,
     * running_state, if the server is running and will also display at what url the
     * server is running.
     */
    private final BroadcastReceiver mFsActionsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Logger.v(TAG, "action received: " + intent.getAction());
            // remove all pending callbacks
            handler.removeCallbacksAndMessages(null);
            // action will be ACTION_STARTED or ACTION_STOPPED
            updateRunningState();
            // or it might be ACTION_FAILEDTOSTART
            if (intent.getAction().equals(FsService.ACTION_FAILEDTOSTART)) {
            }
        }
    };


    @Override
    protected void onResume() {
        super.onResume();
        updateRunningState();

        Logger.d(TAG, "onResume: Registering the FTP server actions");
        IntentFilter filter = new IntentFilter();
        filter.addAction(FsService.ACTION_STARTED);
        filter.addAction(FsService.ACTION_STOPPED);
        filter.addAction(FsService.ACTION_FAILEDTOSTART);
        registerReceiver(mFsActionsReceiver, filter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        startfdmService();
        Logger.v(TAG, "onPause: Unregistering the FTPServer actions");
        unregisterReceiver(mFsActionsReceiver);
    }

    @Override
    protected void onDestroy() {
        Logger.i(TAG, "onDestroy()");
        isActive = false;
        FileStorage.instance().removeListener(this);
        startfdmService();
        Logger.scanLogs();
        super.onDestroy();

    }

    @Override
    protected void onStop() {
        super.onStop();
        startfdmService();
    }

    private void startfdmService() {

        if (GetRunningServices()) {
            Logger.d(TAG, "Service State: Running");
            return;
        }
        // Start the service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(new Intent(getBaseContext(), FDMservice.class));
        } else {
            startService(new Intent(getBaseContext(), FDMservice.class));
        }
        Logger.d(TAG, "Service State: Running");
    }

    private void stopfdmService() {
        // We are already running the babyapp service
        if (GetRunningServices()) {
            Logger.d(TAG, "Service State: Running");
            stopService(new Intent(getBaseContext(), FDMservice.class));
        }
    }

    private boolean GetRunningServices() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningServiceInfo> services = manager.getRunningServices(Integer.MAX_VALUE);
        for (ActivityManager.RunningServiceInfo runningServiceInfo : services) {
            if (runningServiceInfo.service.getClassName().equals("com.sandvik.optimine.datamule.service.fdm.FDMservice")) {
                Logger.d(TAG, "FDMservice is already running");
                return true;
            }
        }
        return false;
    }
}
