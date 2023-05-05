package com.artclave.sandvikdatamule.gui.log;

import android.util.Log;

import com.artclave.sandvikdatamule.util.Logger;
import com.artclave.sandvikdatamule.storage.OutboxFile;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by JuhaM on 24.1.2017.
 */
public class TransferLogItem implements Comparable<TransferLogItem> {
    public enum State {
        Invalid,
        FtpDownload,
        Received,
        CloudUpload,
        Uploaded,
        Failed
    }

    static public TransferLogItem create(String filename)
    {
        TransferLogItem item = null;
        OutboxFile.OutboxFileMetaData fileInfo = OutboxFile.parseFileName(filename);
        if (fileInfo.IsValid) {
            String timeStr = String.valueOf(fileInfo.TimeStamp);
            SimpleDateFormat dateFormat;
            if (timeStr.length() == 8) {
                dateFormat = new SimpleDateFormat("yyyyMMdd");
            }
            else if (timeStr.length() == 12)
            {
                dateFormat = new SimpleDateFormat("yyyyMMddHHmm");
            }
            else if (timeStr.length() >= 14) {
                dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
            }
            else
            {
                Logger.w("TransferLog", "Unable to parse timestamp from filename: " + filename + ", invalid timeStr:" + timeStr);
                return null;
            }
            try {
                Date d = dateFormat.parse(timeStr);
                item = new TransferLogItem();
                item.filename = filename;
                item.machineSerial = fileInfo.DeviceName;
                item.timestamp = d;
            } catch (ParseException pe) {
                Logger.w("TransferLog", "Unable to parse timestamp from filename: " + filename + ", ParseException:" + Log.getStackTraceString(pe));
            }
        }
        else
        {
            Logger.w("TransferLog", "Unable to parse metadata from filename: " + filename);
        }
        return item;
    }

    private TransferLogItem() { }

    private State itemState = State.Invalid;
    private Object itemStateLock = new Object();
    private String filename;
    private String machineSerial;
    private Date timestamp;

    @Override
    public int compareTo(TransferLogItem i) {
        return timestamp.compareTo(i.timestamp);
    }


    public State getState() {
        synchronized (itemStateLock) {
            return itemState;
        }
    }
    public void setState(State state) {
        synchronized (itemStateLock) {
            itemState = state;
        }
    }
    public String getFilename() { return filename; }
    public String getMachineSerial() { return machineSerial; }
    public Date getTimestamp() { return timestamp; }
}
