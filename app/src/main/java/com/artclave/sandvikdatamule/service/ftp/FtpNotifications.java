package com.artclave.sandvikdatamule.service.ftp;

import android.util.Log;

import com.artclave.sandvikdatamule.storage.FileStorage;
import com.artclave.sandvikdatamule.AppSettings;
import com.artclave.sandvikdatamule.gui.main.MainActivity;

import org.apache.ftpserver.ftplet.DefaultFtplet;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpReply;
import org.apache.ftpserver.ftplet.FtpRequest;
import org.apache.ftpserver.ftplet.FtpSession;
import org.apache.ftpserver.ftplet.FtpletResult;

import java.io.File;
import java.io.IOException;

/**
 * Created by juham on 09/11/2017.*/



public class FtpNotifications extends DefaultFtplet {

    public FtpletResult onConnect(FtpSession session) throws FtpException, IOException {
        String addr = session.getClientAddress().getHostString();
        MainActivity.ftpDeviceConnected(addr);

        return null;
    }

    public FtpletResult onDisconnect(FtpSession session) throws FtpException, IOException {
        String addr = session.getClientAddress().getHostString();
        MainActivity.ftpDeviceDisconnected(addr);

        return null;
    }


    public FtpletResult beforeCommand(FtpSession session, FtpRequest request) throws FtpException, IOException {
        Log.i("FtpNotifications", "Got command:" + request.getCommand() + " " + request.getArgument());
        FileStorage.instance().updateLatestFtpActivityTime();
        return super.beforeCommand(session, request);
    }

    public FtpletResult afterCommand(FtpSession session, FtpRequest request, FtpReply reply) throws FtpException, IOException {
        Log.i("FtpNotifications", "After command:" + request.getCommand() + " " + request.getArgument());
        FileStorage.instance().updateLatestFtpActivityTime();
        return super.afterCommand(session, request, reply);
    }

    public FtpletResult onUploadStart(FtpSession session, FtpRequest request) throws FtpException, IOException {
        String addr = session.getClientAddress().getHostString();
        MainActivity.ftpFileReceiveStarted(addr, request.getArgument());
        return null;
    }

    public FtpletResult onUploadEnd(FtpSession session, FtpRequest request) throws FtpException, IOException {
        String path = getRealPath(session, request.getArgument());
        Log.i("FtpNotifications", "New file uploaded:" + path);
        MediaUpdater.notifyFileCreated(path);

        String addr = session.getClientAddress().getHostString();
        MainActivity.ftpFileReceiveCompleted(addr, request.getArgument(), true);

        return null;
    }

    public FtpletResult onDeleteEnd(FtpSession session, FtpRequest request) throws FtpException, IOException {
        String path = getRealPath(session, request.getArgument());
        Log.i("FtpNotifications", "File deleted:" + path);
        MediaUpdater.notifyFileDeleted(path);

        return null;
    }

    private String getRealPath(FtpSession session, String filename) throws FtpException, IOException
    {
        String dir = session.getFileSystemView().getWorkingDirectory().getAbsolutePath();
        String path = new File(dir, filename).getCanonicalPath();
        path = new File(AppSettings.getChrootDir(), path).getCanonicalPath();
        return path;
    }

}

