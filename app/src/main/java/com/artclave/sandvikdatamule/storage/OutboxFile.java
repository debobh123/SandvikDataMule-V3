package com.artclave.sandvikdatamule.storage;

import com.artclave.sandvikdatamule.util.Logger;

import java.io.File;


public class OutboxFile implements Comparable<OutboxFile>
{
    public enum APIType {
        FDM,
        M2M
    }

    public static class OutboxFileMetaData {
        public boolean IsValid;
        public String DeviceName;
        public long TimeStamp;
    }

    private File parentFile;
    private OutboxFileMetaData metaData;
    private APIType api;

    public OutboxFile(File file)
    {
        parentFile = file;
        metaData = parseFileName(file.getName());
        // determine api type from file path.
        if (file.getAbsolutePath().startsWith(FileStorage.instance().getM2MOutboxPath()))
        {
            api = APIType.M2M;
        }
        else
        {
            api = APIType.FDM;
        }
    }

    public File getParentFile()
    {
        return parentFile;
    }
    public APIType getAPI() { return api; }

    public String getDeviceName()
    {
        return metaData.DeviceName;
    }
    public long getTimeStamp() { return metaData.TimeStamp; }

    public boolean isValid() { return metaData.IsValid; }

    @Override
    public int compareTo(OutboxFile i) {
        return Long.compare(getTimeStamp(), i.getTimeStamp());
    }


    static public OutboxFileMetaData parseFileName(String fileName) {

        OutboxFileMetaData data = new OutboxFileMetaData();
        data.IsValid = false;

        try {
            // get first '-'
            int left = fileName.indexOf("-") + 1;
            // get last '-' before extension
            int lastExtension = fileName.lastIndexOf(".");
            String withoutExt = fileName.substring(0, lastExtension);
            int right = withoutExt.lastIndexOf("-");

            data.DeviceName = fileName.substring(left, right);
            int timestampRight = fileName.indexOf(".", right);

            String timeString = fileName.substring(right + 1, timestampRight);
            data.TimeStamp = Long.parseLong(timeString);
            data.IsValid = true;
        }
        catch (NumberFormatException nfe) {
            Logger.e("OutboxFile", "Invalid filename " + fileName + ", timestamp is not valid integer.");
            data.TimeStamp = -1;
        }
        catch (IndexOutOfBoundsException iooe)
        {
            Logger.e("OutboxFile", "Invalid filename " + fileName + ", IndexOutOfBoundsException");
            data.TimeStamp = -1;
        }

        return data;
    }
}

