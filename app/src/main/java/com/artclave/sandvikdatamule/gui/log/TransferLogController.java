package com.artclave.sandvikdatamule.gui.log;

import android.app.Activity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

/**
 * Created by JuhaM on 20.1.2017.
 */
public class TransferLogController  {

    private static long DeleteOlderThanMs = TimeUnit.DAYS.toMillis(30);

    private TransferLogListView listView;
    private Activity activity;

    private HashMap<String, TransferLogItem> items = new HashMap<String, TransferLogItem>();
    private ArrayList<TransferLogItem> sortedItems = new ArrayList<TransferLogItem>();
    private boolean sortedItemsInvalid = false;


    public void setParentView(Activity activity, TransferLogListView view) {
        this.activity = activity;
        listView = view;
        synchronized (items) {
            sortFilesPrivate();
            listView.setData(new TransferLogControllerData(sortedItems));
        }
    }

    public void cleanOldFiles()
    {
        boolean changed = false;
        synchronized (items) {
            // delete items older than 30days. do it only when sortedItems is valid.
            if (sortedItems.size() > 0 && !sortedItemsInvalid) {
                TransferLogItem last = sortedItems.get(sortedItems.size() - 1);
                while (new Date().getTime() - last.getTimestamp().getTime() > DeleteOlderThanMs) {
                    sortedItems.remove(sortedItems.size() - 1);
                    items.remove(last.getFilename());
                    changed = true;
                    // no more items, we deleted them all, break out.
                    if (sortedItems.size() <= 0) break;
                    // otherwise check next item.
                    last = sortedItems.get(sortedItems.size() - 1);
                }
            }
        }
        if (changed) {
            refreshUI();
        }
    }

    public TransferLogItem getOrAddFile(String filename)
    {
        cleanOldFiles();

        synchronized (items) {
            TransferLogItem item = null;
            if (items.containsKey(filename)) {
                item = items.get(filename);
            } else {
                item = TransferLogItem.create(filename);
                if (item != null) {
                    items.put(filename, item);
                    sortedItems.add(item);
                    sortedItemsInvalid = true;
                }
            }
            return item;
        }
    }



    public void sortFiles()
    {
        synchronized (items) {
            sortFilesPrivate();
        }
    }

    private void sortFilesPrivate() {
        if (sortedItemsInvalid) {
            Collections.sort(sortedItems, Collections.reverseOrder());
            sortedItemsInvalid = false;
        }
    }

    public boolean refreshUI()
    {
        boolean ret = false;
        if (activity != null && listView != null) {
            try {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (items) {
                            sortFilesPrivate();
                            listView.setData(new TransferLogControllerData(sortedItems));
                        }
                        listView.notifyDataSetChanged();
                    }
                });
                ret = true;
            }
            catch (Throwable t) { }
        }
        return ret;
    }

}
