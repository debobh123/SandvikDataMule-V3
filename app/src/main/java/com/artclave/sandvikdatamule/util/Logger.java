package com.artclave.sandvikdatamule.util;

import android.media.MediaScannerConnection;
import android.util.Log;

import com.artclave.sandvikdatamule.App;
import com.artclave.sandvikdatamule.AppSettings;
import com.artclave.sandvikdatamule.storage.FileStorage;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.lang.Object;

/**
 * Created by JuhaM on 5.1.2017.
 */
public class Logger {

    static private File logFile = null;
    static private Object logFileLock = new Object();
    static private String LOGS_FOLDER = "/debuglogs/";

    public static void v(String tag, String msg) {
        Log.v(tag, msg);
        writeLogFile("V", tag, msg);
    }

    public static void d(String tag, String msg) {
        Log.d(tag, msg);
        writeLogFile("D", tag, msg);
    }
    public static void i(String tag, String msg) {
        Log.i(tag, msg);
        writeLogFile("I", tag, msg);
    }
    public static void w(String tag, String msg) {
        Log.w(tag, msg);
        writeLogFile("W", tag, msg);
    }

    public static void e(String tag, String msg) {
        Log.e(tag, msg);
        writeLogFile("E", tag, msg);
    }
    public static void e(String tag, String msg, Throwable tr) {
        Log.e(tag, msg, tr);
        writeLogFile("E", tag, msg + "\r\n" + Log.getStackTraceString(tr));
    }

    public static void scanLogs()
    {
        String logFolder = AppSettings.getChrootDirAsString() + LOGS_FOLDER;
        MediaScannerConnection.scanFile(App.getAppContext(), new String[]{logFolder}, null, null);
    }

    private static void writeLogFile(String category, String tag, String msg)
    {
        boolean newFile = false;

        // initialize log file once.
        if (logFile == null || !logFile.exists()) {
            synchronized (logFileLock) {
                if (logFile == null || !logFile.exists()) {
                    // create Logs folder under our root path.
                    String logFolder = AppSettings.getChrootDirAsString() + LOGS_FOLDER;
                    if (logFolder == null || logFolder.isEmpty()) {
                        Log.e("Logger", "Unable to get logFolder.");
                        return;
                    }
                    FileStorage.CreateFolder(logFolder);

                    // compose filename, contains timestamp. and create empty file.
                    DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss");
                    String filename = "MuleLog_" + dateFormat.format(new Date()) + ".txt";
                    logFile = new File(logFolder + filename);
                    try {
                        logFile.createNewFile();
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                    // scan the folder so visible via USB
                    scanLogs();
                    newFile = true;
                }
            }
        }
        // clear old logs if new file was created, outside syncronization block.
        if (newFile)
        {
            clearOldLogs(5);

        }
        BufferedWriter logWriter = null;
        try
        {
            logWriter = new BufferedWriter(new FileWriter(logFile, true));
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.S");
            String text = String.format("%s[%s][%s]%s", dateFormat.format(new Date()), category, tag, msg);
            logWriter.append(text);
            logWriter.newLine();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        finally {
            try {
                if (logWriter != null) {
                    logWriter.close();
                }
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }

    }

    private static void clearOldLogs(int howManyWeKeep)
    {
        String logFolder = AppSettings.getChrootDirAsString() + LOGS_FOLDER;
        File files[] =  new File(logFolder).listFiles(new FileFilter()
        {
            @Override
            public boolean accept(File pathname) {
                return (pathname.isFile());
            }
        });
        // not enough files, no need to delete anything.
        if (files == null || files.length <= howManyWeKeep) {
            return;
        }

        // sort files by lastModified time.
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File lhs, File rhs) {
                return Long.compare(lhs.lastModified(), rhs.lastModified());
            }
        });
        // delete files from begin of array.
        for (int i = 0; i < files.length - howManyWeKeep; i++)
        {
            Logger.d("Logger", "Deleting old log file: " + files[i].getName());
            files[i].delete();
        }
        scanLogs();
    }
}

