package com.artclave.sandvikdatamule.storage;

import com.artclave.sandvikdatamule.util.Logger;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class FileStorageOutboxCache {

    private class MachineCache {
        public boolean isSorted = false;
        public ArrayList<OutboxFile> files = new ArrayList<>();
    }

    private Object cacheDataLock = new Object();
    private HashMap<String, MachineCache> cacheData = null;

    public FileStorageOutboxCache() { }

    public List<OutboxFile> getLatestFiles() {

        List<String> modifiedMachines = null;

        List<OutboxFile> files = new ArrayList<>();
        synchronized (cacheDataLock) {
            // if no cache, create from outbox folders.
            if (cacheData == null) {
                refreshCache();
                modifiedMachines = new ArrayList<>(cacheData.keySet());
            }

            // get latest file from all machines.
            for (MachineCache mc : cacheData.values()) {
                if (!mc.isSorted) {
                    Collections.sort(mc.files);
                    Collections.reverse(mc.files);
                    mc.isSorted = true;
                }
                if (mc.files.size() > 0) {
                    files.add(mc.files.get(0));
                }
            }
        }

        if (modifiedMachines != null) {
            for (String machine : modifiedMachines) {
                FileStorage.instance().callOnMachineMetaDataChanged(machine);
            }
        }

        return files;
    }

    public boolean addFile(File f) {
        OutboxFile o = new OutboxFile(f);
        synchronized (cacheDataLock) {
            if (o.isValid()) {
                MachineCache machineCache = cacheData.get(o.getDeviceName());
                if (machineCache == null) {
                    machineCache = new MachineCache();
                    cacheData.put(o.getDeviceName(), machineCache);
                }

                machineCache.files.add(o);
                machineCache.isSorted = false;
            }
            else {
                return false;
            }
        }

        FileStorage.instance().callOnMachineMetaDataChanged(o.getDeviceName());
        return true;
    }

    public void deleteFile(OutboxFile of) {
        synchronized (cacheDataLock) {
            MachineCache machineCache = cacheData.get(of.getDeviceName());
            if (machineCache != null) {
                machineCache.files.remove(of);
            }
        }

        FileStorage.instance().callOnMachineMetaDataChanged(of.getDeviceName());
    }

    public int getMachinePendingFilesCount(String machine) {
        int count = 0;
        synchronized (cacheDataLock) {
            if (cacheData != null)
            {
                MachineCache mc = cacheData.get(machine);
                if (mc != null)
                {
                    count = mc.files.size();
                }
            }
        }
        return count;
    }

    public int getPendingFilesCount() {
        int count = 0;
        synchronized (cacheDataLock) {
            if (cacheData != null)
            {
                for (MachineCache mc : cacheData.values()) {
                    count += mc.files.size();
                }
            }
        }
        return count;
    }

    private void refreshCache() {
        Logger.i("FileStorageOutboxCache", "Refreshing cache...");

        // clear existing cache and add all files from outbox and m2moutbox
        cacheData = new HashMap<>();


        ArrayList<File> files = new ArrayList<File>();
        // add files from outbox
        File[] fileArray = new File(FileStorage.instance().getOutboxPath()).listFiles(new FileFilter() {
                                                                                          @Override
                                                                                          public boolean accept(File pathname) {
                                                                                              return acceptFile(pathname);
                                                                                          }
                                                                                      }
        );
        if (fileArray != null && fileArray.length > 0) {
            refreshCacheAddFiles(fileArray);
        }

        // add files from m2moutbox
        fileArray = new File(FileStorage.instance().getM2MOutboxPath()).listFiles(new FileFilter() {
                                                                                      @Override
                                                                                      public boolean accept(File pathname) {
                                                                                          return acceptFile(pathname);
                                                                                      }
                                                                                  }
        );
        if (fileArray != null && fileArray.length > 0) {
            refreshCacheAddFiles(fileArray);
        }
    }

    private void refreshCacheAddFiles(File[] fileArray) {

        for (File f : fileArray) {
            OutboxFile o = new OutboxFile(f);
            if (o.isValid()) {
                MachineCache machineCache = cacheData.get(o.getDeviceName());
                if (machineCache == null)
                {
                    machineCache = new MachineCache();
                    cacheData.put(o.getDeviceName(), machineCache);
                }

                machineCache.files.add(o);
            }
            else
            {
                Logger.w("FileStorage:", "Deleting invalid filename from outbox:" + f.getName());
                FileStorage.instance().deleteErrornousOutgoingFile(f);
            }
        }
    }

    private boolean acceptFile(File file) {
        if (file.isFile()) {
            if (file.canRead() && file.canWrite()) {
                return true;
            }
            else
            {
                Logger.w("FileStorage:", "File with insufficient permissions in outbox:" + file.getPath());
            }
        }
        return false;
    }


}

