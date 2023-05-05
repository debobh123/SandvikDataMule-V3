package com.artclave.sandvikdatamule.service.ftp;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;

import com.artclave.sandvikdatamule.App;
import com.artclave.sandvikdatamule.storage.FileStorage;
import com.artclave.sandvikdatamule.AppSettings;
import com.artclave.sandvikdatamule.util.Logger;

import java.io.File;
import java.io.IOException;
import java.util.Timer;


/**
 * This media rescanner runs in the background. The rescan might
 * not happen immediately.
 *
 *
 */
public enum MediaUpdater {
    INSTANCE;

    private final static String TAG = MediaUpdater.class.getSimpleName();

    // the system broadcast to remount the media is only done after a little while (5s)
    private static Timer sTimer = new Timer();

    private static class ScanCompletedListener implements
            MediaScannerConnection.OnScanCompletedListener {
        @Override
        public void onScanCompleted(String path, Uri uri) {
            Logger.i(TAG, "Scan completed: " + path + " : " + uri);
        }

    }

    public static void notifyFileCreated(String path) {
        Logger.d(TAG, "Notifying others about new file: " + path);
        Context context = App.getAppContext();
        MediaScannerConnection.scanFile(context, new String[]{path}, null, new MediaScannerConnection.OnScanCompletedListener()
                {
                    public void onScanCompleted(String path, Uri uri) {
                        String filePath = null;
                        try {
                            filePath = new File(path).getCanonicalPath();
                        }
                        catch (IOException e) { e.printStackTrace();}

                        String chrootReportsPath = AppSettings.getChrootDirAsString() + "/reports";
                        String chrootReportsM2MPath = AppSettings.getChrootDirAsString() + "/m2mreports";
                        if (filePath.startsWith(chrootReportsPath)) {
                            Logger.d(TAG, "New report file received, moving to outbox.");
                            FileStorage.instance().moveToOutbox(path);
                        }
                        else if (filePath.startsWith(chrootReportsM2MPath)) {
                            if (AppSettings.getServerType() == AppSettings.ServerType.Optimine) {
                                Logger.d(TAG, "New m2mreport file received, moving to outbox.");
                                FileStorage.instance().moveToM2MOutbox(path);
                            }
                            else
                            {
                                Logger.d(TAG, "Ignoring received m2mreport, not optimine server: " + path);
                            }
                        }
                        else
                        {
                            Logger.d(TAG, "Unknown file added: " + path);
                        }
                    }
                }
        );
    }

    /**
     * This is called when file is deleted via ftp server. Scans with android mediascanner and
     * if this is dcu config that is deleted, removes the file from blob storage.
     *
     * This should ONLY be called when file is deleted via ftp server! Not when file is deleted locally from this app.
     * @param path
     */
    public static void notifyFileDeleted(String path) {

        // if this was config file, delete it from blob when we got the connection.
        String absolutePath = new File(path).getAbsolutePath();
        if (absolutePath.startsWith(AppSettings.getChrootDirAsString() + "/configurations") ||
                absolutePath.startsWith(AppSettings.getChrootDirAsString() + "/swpackages")) {
            FileStorage.instance().addBlobFileToBeRemoved(path);
        }

        Logger.d(TAG, "Notifying others about deleted file: " + path);
        // on newer devices, we hope that this works correctly:
        Context context = App.getAppContext();
        MediaScannerConnection.scanFile(context, new String[]{path}, null,
                new ScanCompletedListener());
    }
}

