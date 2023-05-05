package com.artclave.sandvikdatamule.storage;

import android.media.MediaScannerConnection;
import android.net.Uri;

import com.artclave.sandvikdatamule.App;
import com.artclave.sandvikdatamule.util.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;


/**
 * Created by JuhaM on 12.4.2016.
 */
public class FileStorage {
    private final static String TAG = FileStorage.class.getSimpleName();


    /// Outbox contains files that are fully transferred from DCU, this is NOT visible via FTP.
    private static String OUTBOX_PATH = "/Sandvik/Outbox/";
    private static String M2MOUTBOX_PATH = "/Sandvik/OutboxM2M/";
    private static String INBOX_PATH = "/Sandvik/Inbox/";
    private static String SENT_PATH = "/Sandvik/SentFiles/";
    private static String ERROR_PATH = "/Sandvik/ErrorFiles/";
    private static String TOBEREMOVED_PATH = "/Sandvik/ToBeRemoved.txt";

    private static FileStorage instance = null;

    private File rootPath;

    private Object latestFtpActivityLock = new Object();
    private long latestFtpActivity = 0;

    private FileStorageOutboxCache outboxCache = new FileStorageOutboxCache();

    private HashSet<IFileStorageListener> listeners = new HashSet<IFileStorageListener>();

    public static void createInstance(File storageRootPath) {
        if (instance == null) {
            instance = new FileStorage(storageRootPath);
        }
    }

    public static FileStorage instance() {
        return instance;
    }

    private FileStorage(File storageRootPath) {
        rootPath = storageRootPath;
        CreateFolder(getOutboxPath());
        CreateFolder(getM2MOutboxPath());
        CreateFolder(getSentFilesPath());
        CreateFolder(getErrorFilesPath());
        MediaScannerConnection.scanFile(App.getAppContext(), new String[]{getOutboxPath(), getM2MOutboxPath(), getSentFilesPath(), getErrorFilesPath()}, null, null);
    }

    public File getStorageRootPath() {
        return rootPath;
    }

    public void addListener(IFileStorageListener l) {
        synchronized (listeners) {
            listeners.add(l);
        }
    }

    public void removeListener(IFileStorageListener l) {
        synchronized (listeners) {
            listeners.remove(l);
        }
    }

    public static void CreateFolder(String path) {
        File dir = new File(path);
        if (dir.exists() && !dir.isDirectory()) {
            dir.delete();
        }

        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    public String getOutboxPath() {
        return getStorageRootPath().getAbsolutePath() + OUTBOX_PATH;
    }

    public String getM2MOutboxPath() {
        return getStorageRootPath().getAbsolutePath() + M2MOUTBOX_PATH;
    }

    public String getSentFilesPath() {
        return getStorageRootPath().getAbsolutePath() + SENT_PATH;
    }

    public String getErrorFilesPath() {
        return getStorageRootPath().getAbsolutePath() + ERROR_PATH;
    }

    public String getInboxPath(String containerName) {
        String path = getStorageRootPath().getAbsolutePath() + INBOX_PATH + containerName + "/";
        CreateFolder(path);
        return path;
    }

    private String getToBeRemovedFilePath() {
        return getStorageRootPath().getAbsolutePath() + TOBEREMOVED_PATH;
    }

    /**
     * Move received file to regular outbox, sent to IoTHUB or Optimine iothub api.
     *
     * @param srcPath
     */
    public void moveToOutbox(String srcPath) {
        moveToDefinedOutbox(srcPath, getOutboxPath());
    }

    /**
     * Move received file to m2m outbox, sent to Optimine M2M API.
     *
     * @param srcPath
     */
    public void moveToM2MOutbox(String srcPath) {
        moveToDefinedOutbox(srcPath, getM2MOutboxPath());
    }

    private void moveToDefinedOutbox(String srcPath, String outboxPath) {
        updateLatestFtpActivityTime();
        OutboxFile.OutboxFileMetaData fileData = OutboxFile.parseFileName(srcPath);
        if (fileData.IsValid) {

            File dstFile = moveFile(new File(srcPath), new File(outboxPath));
            if (!outboxCache.addFile(dstFile)) {
                deleteErrornousOutgoingFile(dstFile);
            }
        } else {
            deleteErrornousOutgoingFile(new File(srcPath));
        }
    }

    public void callOnMachineMetaDataChanged(String machineSerial) {
        synchronized (listeners) {
            for (IFileStorageListener listener : listeners) {
                listener.onMachineMetaDataChanged(machineSerial);
            }
        }
    }

    private final Object blobToBeRemovedLock = new Object();

    public void addBlobFileToBeRemoved(String filePath) {
        synchronized (blobToBeRemovedLock) {
            try {
                FileWriter file = new FileWriter(getToBeRemovedFilePath(), true);
                BufferedWriter writer = new BufferedWriter(file);
                writer.write(filePath);
                writer.newLine();
                writer.close();
            } catch (IOException ie) {
                Logger.d(TAG, "ERROR: Unable to write `toBeRemoved` file. IOException:" + ie.toString());
                ie.printStackTrace();
            }
        }
    }

    public String[] getBlobFilesToBeRemoved() {
        synchronized (blobToBeRemovedLock) {
            try {
                List<String> list = new ArrayList<String>();
                FileReader file = new FileReader(getToBeRemovedFilePath());
                BufferedReader reader = new BufferedReader(file);
                String line = null;
                while ((line = reader.readLine()) != null) {
                    list.add(line);
                }
                reader.close();
                return list.toArray(new String[0]);
            } catch (FileNotFoundException fnf) {
                // normal case, simply return empty array.
            } catch (IOException ie) {
                ie.printStackTrace();
                Logger.d(TAG, "ERROR: Unable to read toBeRemoved file. IOException:" + ie.toString());
            }
            return new String[0];
        }
    }

    public void removeBlobFromToBeRemovedList(String filePath) {
        synchronized (blobToBeRemovedLock) {
            try {
                // first read the existing file, everything but the file we want to remove.
                List<String> filesLeft = new ArrayList<String>();
                FileReader file = new FileReader(getToBeRemovedFilePath());
                BufferedReader reader = new BufferedReader(file);
                String line = null;
                while ((line = reader.readLine()) != null) {
                    if (line.compareTo(filePath) != 0) {
                        filesLeft.add(line);
                    }
                }
                reader.close();

                // then write back the files that we don't want to remove.
                FileWriter fileWrite = new FileWriter(getToBeRemovedFilePath(), false);
                BufferedWriter writer = new BufferedWriter(fileWrite);
                for (String fileLeft :
                        filesLeft) {
                    writer.write(fileLeft);
                    writer.newLine();
                }
                writer.close();
            } catch (FileNotFoundException fnf) {
                // normal case.
            } catch (IOException ie) {
                ie.printStackTrace();
                Logger.d(TAG, "ERROR: Unable to access toBeRemoved file. IOException:" + ie.toString());
            }
        }
    }


    /**
     * Returns number of pending reports in outbox, this number is only updated on getNextOutboxFiles() call.
     *
     * @return
     */
    public int getPendingReportCount() {
        return outboxCache.getPendingFilesCount();
    }

    public int getPendingReportCount(String machineSerial) {
        return outboxCache.getMachinePendingFilesCount(machineSerial);
    }

    public long getLatestFtpActivityTime() {
        synchronized (latestFtpActivityLock) {
            return latestFtpActivity;
        }
    }

    public void updateLatestFtpActivityTime() {
        synchronized (latestFtpActivityLock) {
            latestFtpActivity = new Date().getTime();
        }
    }

    public OutboxFile[] getNextOutboxFiles() {
        long start = System.currentTimeMillis();
        HashMap<String, Integer> pendingReportCountPerMachine = new HashMap<String, Integer>();

        List<OutboxFile> latestFiles = outboxCache.getLatestFiles();

        long end = System.currentTimeMillis();
        Logger.i("Performance", "Executing getNextOutboxFiles() took " + (end - start) + "ms");
        return latestFiles.toArray(new OutboxFile[0]);
    }

    public void fileUploadCompleted(OutboxFile file) {
        outboxCache.deleteFile(file);

        // at development stage move to SentFiles folder, delete files older than 30days.
        clearOldFiles(getSentFilesPath(), TimeUnit.DAYS.toMillis(30));
        moveFile(file.getParentFile(), new File(getSentFilesPath()));
    }

    public void fileUploadFailed(OutboxFile file) {
        outboxCache.deleteFile(file);
        deleteErrornousOutgoingFile(file.getParentFile());
    }

    public void deleteErrornousOutgoingFile(File file) {
        Logger.w("FileStorage", "deleteErrornousOutgoingFile " + file.getPath());
        // at development stage move to ErrorFiles folder, delete files older than 30d ays.
        clearOldFiles(getErrorFilesPath(), TimeUnit.DAYS.toMillis(30));
        moveFile(file, new File(getErrorFilesPath()));
    }


    private static HashMap<String, Long> latestClearOldFilesTime = new HashMap<String, Long>();

    private static void clearOldFiles(String directory, long deleteOlderThanMsecs) {

        // this is heavy function and really slows down the transmission if done after each file.
        // execute twice a day.
        synchronized (latestClearOldFilesTime) {
            Long latestRun = latestClearOldFilesTime.get(directory);
            if (latestRun != null) {
                long timeSinceLatest = new Date().getTime() - latestRun;
                if (timeSinceLatest < TimeUnit.HOURS.toMillis(12)) {
                    return;
                }
            }
            latestClearOldFilesTime.put(directory, new Date().getTime());
        }

        long olderThan = new Date().getTime() - deleteOlderThanMsecs;

        // get files that's lastModified is < olderThan.
        File files[] = new File(directory).listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return (pathname.isFile() && pathname.lastModified() < olderThan);
            }
        });
        String[] deleted = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            files[i].delete();
            deleted[i] = files[i].getPath();
        }
        MediaScannerConnection.scanFile(App.getAppContext(), deleted, null, null);
    }


    public static File moveFile(File srcFile, File targetDir) {
        String filename = srcFile.getName();
        File targetFile = new File(targetDir.getPath() + "/" + filename);
        // if targetFile exists, add incrementing "-index" to end of filename.
        int fileIndex = 0;
        while (targetFile.exists()) {
            targetFile = new File(targetDir.getPath() + "/" + filename + "-" + String.valueOf(fileIndex++));
        }

        if (!srcFile.renameTo(targetFile)) {
            moveFilePart(srcFile, targetFile);
        }


        MediaScannerConnection.scanFile(App.getAppContext(), new String[]{srcFile.getPath(), targetFile.getPath()}, null, new MediaScannerConnection.OnScanCompletedListener() {
            @Override
            public void onScanCompleted(String path, Uri uri) {

            }
        });

        return targetFile;
    }

    /**
     * Moves files between partitions by copying all the bytes and then deleting original.
     * This is used only if File.renameTo() fails.
     *
     * @param inputPath
     * @param outputPath
     */
    private static void moveFilePart(File inputPath, File outputPath) {

        InputStream in = null;
        OutputStream out = null;
        try {

            //create output directory if it doesn't exist
            File dir = new File(outputPath.getParent());
            if (!dir.exists()) {
                dir.mkdirs();
            }


            in = new FileInputStream(inputPath);
            out = new FileOutputStream(outputPath);

            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();
            in = null;

            // write the output file
            out.flush();
            out.close();
            out = null;

            // delete the original file
            inputPath.delete();
        } catch (FileNotFoundException fnfe1) {
            Logger.e("FileStorage:", fnfe1.getMessage());
        } catch (Exception e) {
            Logger.e("FileStorage:", e.getMessage());
        }
    }

}

