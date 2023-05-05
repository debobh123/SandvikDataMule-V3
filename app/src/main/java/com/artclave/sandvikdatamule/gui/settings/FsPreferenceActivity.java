package com.artclave.sandvikdatamule.gui.settings;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.TwoStatePreference;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatDelegate;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.artclave.sandvikdatamule.App;
import com.artclave.sandvikdatamule.AppSettings;
import com.artclave.sandvikdatamule.IoTHUBClient;
import com.artclave.sandvikdatamule.R;
import com.artclave.sandvikdatamule.gui.FolderPickerDialogBuilder;
import com.artclave.sandvikdatamule.gui.main.MainActivity;
import com.artclave.sandvikdatamule.service.ftp.FsService;
import com.artclave.sandvikdatamule.util.Logger;
import com.artclave.sandvikdatamule.util.NetUtils;

import net.vrallev.android.cat.Cat;

import java.io.File;
import java.net.InetAddress;
import java.util.List;

/**
 * This activity allows the users to change the settings for the application*/


public class FsPreferenceActivity extends PreferenceActivity implements
        OnSharedPreferenceChangeListener {

    private AppCompatDelegate mDelegate;

    private static String TAG = FsPreferenceActivity.class.getSimpleName();

    private EditTextPreference mPassWordPref;
    private Handler mHandler = new Handler();

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        LinearLayout root = (LinearLayout) findViewById(android.R.id.list).getParent().getParent().getParent();
        Toolbar bar = (Toolbar) LayoutInflater.from(this).inflate(R.layout.settings_toolbar, root, false);
        root.addView(bar, 0); // insert at top
        bar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Logger.d(TAG, "created");


        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences);

        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        Resources resources = getResources();

        TwoStatePreference runningPref = findPref("running_switch");
        updateRunningState();
        runningPref.setOnPreferenceChangeListener((preference, newValue) -> {
            if ((Boolean) newValue) {
                startServer();
            } else {
                stopServer();
            }
            return true;
        });

        updateSummary();
        updateHotspotInfo();
        updateServerInfo();

        EditTextPreference ssidPref = findPref("ssid");
        ssidPref.setOnPreferenceChangeListener((preference, newValue) -> {


            return true;
        });

        EditTextPreference usernamePref = findPref("username");
        usernamePref.setOnPreferenceChangeListener((preference, newValue) -> {
            String newUsername = (String) newValue;
            if (preference.getSummary().equals(newUsername))
                return false;
            if (!newUsername.matches("[a-zA-Z0-9]+")) {
                Toast.makeText(FsPreferenceActivity.this,
                        R.string.username_validation_error, Toast.LENGTH_LONG).show();
                return false;
            }
            stopServer();
            return true;
        });

        mPassWordPref = findPref("password");
        mPassWordPref.setOnPreferenceChangeListener((preference, newValue) -> {
            stopServer();
            return true;
        });

        DynamicMultiSelectListPreference autoconnectListPref = findPref("autoconnect_preference");
        autoconnectListPref.setOnPopulateListener(
                pref -> {
                    Cat.d("autoconnect populate listener");

                    WifiManager wifiManager = (WifiManager) this.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                        return;
                    }
                    List<WifiConfiguration> configs = wifiManager.getConfiguredNetworks();
                    if (configs == null) {
                        Cat.e("Unable to receive wifi configurations, bark at user and bail");
                        Toast.makeText(this,
                                R.string.autoconnect_error_enable_wifi_for_access_points,
                                Toast.LENGTH_LONG)
                                .show();
                        return;
                    }
                    CharSequence[] ssids = new CharSequence[configs.size()];
                    CharSequence[] niceSsids = new CharSequence[configs.size()];
                    for (int i = 0; i < configs.size(); ++i) {
                        ssids[i] = configs.get(i).SSID;
                        String ssid = configs.get(i).SSID;
                        if (ssid.length() > 2 && ssid.startsWith("\"") && ssid.endsWith("\"")) {
                            ssid = ssid.substring(1, ssid.length() - 1);
                        }
                        niceSsids[i] = ssid;
                    }
                    pref.setEntries(niceSsids);
                    pref.setEntryValues(ssids);
                });

        EditTextPreference portnum_pref = findPref("portNum");
        portnum_pref.setSummary(sp.getString("portNum",
                resources.getString(R.string.portnumber_default)));
        portnum_pref.setOnPreferenceChangeListener((preference, newValue) -> {
            String newPortnumString = (String) newValue;
            if (preference.getSummary().equals(newPortnumString))
                return false;
            int portnum = 0;
            try {
                portnum = Integer.parseInt(newPortnumString);
            } catch (Exception e) {
            }
            if (portnum <= 0 || 65535 < portnum) {
                Toast.makeText(FsPreferenceActivity.this,
                        R.string.port_validation_error, Toast.LENGTH_LONG).show();
                return false;
            }
            preference.setSummary(newPortnumString);
            stopServer();
            return true;
        });

        EditTextPreference serverUrl_pref = findPref("server_url");
        serverUrl_pref.setOnPreferenceChangeListener(((preference, newValue) -> {
            if (!IoTHUBClient.isValidUrlBase((String)newValue)) {
                if (AppSettings.getServerType() == AppSettings.ServerType.Optimine) {
                    Toast.makeText(FsPreferenceActivity.this, "Optimine Server URL must be valid url without port or path.", Toast.LENGTH_LONG).show();
                }
                else
                {
                    Toast.makeText(FsPreferenceActivity.this, "Server URL must be valid url address.", Toast.LENGTH_LONG).show();
                }
                return false;
            }
            return true;
        }));

        Preference chroot_pref = findPref("chrootDir");
        chroot_pref.setSummary(AppSettings.getChrootDirAsString());
        chroot_pref.setOnPreferenceClickListener(preference -> {
            AlertDialog folderPicker = new FolderPickerDialogBuilder(this, AppSettings.getChrootDir())
                    .setSelectedButton(R.string.select, path -> {
                        if (preference.getSummary().equals(path))
                            return;
                        if (!AppSettings.setChrootDir(path))
                            return;
                        // TODO: this is a hotfix, create correct resources, improve UI/UX
                        final File root = new File(path);
                        if (!root.canRead()) {
                            Toast.makeText(this,
                                    "Notice that we can't read/write in this folder.",
                                    Toast.LENGTH_LONG).show();
                        } else if (!root.canWrite()) {
                            Toast.makeText(this,
                                    "Notice that we can't write in this folder, reading will work. Writing in subfolders might work.",
                                    Toast.LENGTH_LONG).show();
                        }

                        preference.setSummary(path);
                        stopServer();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .create();
            folderPicker.show();
            return true;
        });

        final CheckBoxPreference wakelock_pref = findPref("stayAwake");
        wakelock_pref.setOnPreferenceChangeListener((preference, newValue) -> {
            stopServer();
            return true;
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PreferenceScreen hotspotSetting = findPref("hotspot_set");
            hotspotSetting.setEnabled(false);
            hotspotSetting.setSummary(R.string.hotspotsettings_android_oreo_info);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        updateRunningState();

        Logger.d(TAG, "onResume: Register the preference change listner");
        SharedPreferences sp = getPreferenceScreen().getSharedPreferences();
        sp.registerOnSharedPreferenceChangeListener(this);

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

        Logger.v(TAG, "onPause: Unregistering the FTPServer actions");
        unregisterReceiver(mFsActionsReceiver);

        Logger.d(TAG, "onPause: Unregistering the preference change listener");
        SharedPreferences sp = getPreferenceScreen().getSharedPreferences();
        sp.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
        Logger.v(TAG, "onSharedPreferenceChanged");
        updateSummary();
        updateHotspotInfo();
        updateServerInfo();
    }

    private void startServer() {
        sendBroadcast(new Intent(FsService.ACTION_START_FTPSERVER));
    }

    private void stopServer() {
        sendBroadcast(new Intent(FsService.ACTION_STOP_FTPSERVER));
    }

    private void updateSummary() {

        String username = AppSettings.getUserName();
        String password = AppSettings.getPassWord();

        Logger.v(TAG, "Updating login summary");
        PreferenceScreen loginPreference = findPref("login");
        loginPreference.setSummary(username + " : " + transformPassword(password));
        ((BaseAdapter) loginPreference.getRootAdapter()).notifyDataSetChanged();

        EditTextPreference usernamePref = findPref("username");
        usernamePref.setSummary(username);

        EditTextPreference passWordPref = findPref("password");
        passWordPref.setSummary(transformPassword(password));
    }

    private void updateHotspotInfo() {

        String ssid = AppSettings.getWifiHotspotSSID();
        String wpa = AppSettings.getWifiHotspotPSK();

        Logger.v(TAG, "Updating login summary");
        PreferenceScreen hotspotPreference = findPref("hotspot_set");
        hotspotPreference.setSummary(ssid + " : " + transformPassword(wpa));
        ((BaseAdapter) hotspotPreference.getRootAdapter()).notifyDataSetChanged();

        EditTextPreference ssidPref = findPref("ssid");
        ssidPref.setSummary(ssid);

        EditTextPreference wpaPref = findPref("wpa");
        wpaPref.setSummary(transformPassword(wpa));
    }

    private void updateServerInfo() {

        ListPreference serverTypePref = findPref("server_type");
        String serverType = AppSettings.getServerType().toString();
        serverTypePref.setSummary(serverType);

        EditTextPreference ssidPref = findPref("server_url");
        ssidPref.setSummary(AppSettings.getServerUrl());

        MainActivity.refreshConnectionLabel();
    }


    private void updateRunningState() {
        Resources res = getResources();
        TwoStatePreference runningPref = findPref("running_switch");
        if (FsService.isRunning()) {
            runningPref.setChecked(true);
            // Fill in the FTP server address
            InetAddress address = NetUtils.getLocalInetAddress();
            if (address == null) {
                Logger.v(TAG, "Unable to retrieve wifi ip address");
                runningPref.setSummary(R.string.cant_get_url);
                sendBroadcast(new Intent(FsService.ACTION_STOP_FTPSERVER));
                return;
            }
            String iptext = "ftp://" + address.getHostAddress() + ":"
                    + AppSettings.getPortNumber() + "/";
            String summary = res.getString(R.string.running_summary_started, iptext);
            runningPref.setSummary(summary);
        } else {
            runningPref.setChecked(false);
            runningPref.setSummary(R.string.running_summary_stopped);
        }
    }

/**
     * This receiver will check FTPServer.ACTION* messages and will update the button,
     * running_state, if the server is running and will also display at what url the
     * server is running.*/


    BroadcastReceiver mFsActionsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Logger.v(TAG, "action received: " + intent.getAction());
            // remove all pending callbacks
            mHandler.removeCallbacksAndMessages(null);
            // action will be ACTION_STARTED or ACTION_STOPPED
            updateRunningState();
            // or it might be ACTION_FAILEDTOSTART
            final TwoStatePreference runningPref = findPref("running_switch");
            if (intent.getAction().equals(FsService.ACTION_FAILEDTOSTART)) {
                runningPref.setChecked(false);
                mHandler.postDelayed(
                        () -> runningPref.setSummary(R.string.running_summary_failed),
                        100);
                mHandler.postDelayed(
                        () -> runningPref.setSummary(R.string.running_summary_stopped),
                        5000);
            }
        }
    };

    static private String transformPassword(String password) {
        Context context = App.getAppContext();
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        Resources res = context.getResources();
        String showPasswordString = res.getString(R.string.show_password_default);
        boolean showPassword = showPasswordString.equals("true");
        showPassword = sp.getBoolean("show_password", showPassword);
        if (showPassword)
            return password;
        else {
            StringBuilder sb = new StringBuilder(password.length());
            for (int i = 0; i < password.length(); ++i)
                sb.append('*');
            return sb.toString();
        }
    }

    private AppCompatDelegate getDelegate() {
        if (mDelegate == null) {
            mDelegate = AppCompatDelegate.create(this, null);
        }
        return mDelegate;
    }

    @SuppressWarnings({"unchecked", "deprecation"})
    protected <T extends Preference> T findPref(CharSequence key) {
        return (T) this.findPreference(key);
    }

}

