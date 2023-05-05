package com.artclave.sandvikdatamule;

import android.content.Context;
import android.util.Patterns;

import com.artclave.sandvikdatamule.service.ftp.FsService;
import com.artclave.sandvikdatamule.storage.FileStorage;
import com.artclave.sandvikdatamule.storage.OutboxFile;
import com.artclave.sandvikdatamule.util.Logger;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.SyncHttpClient;
import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.blob.CloudBlob;
import com.microsoft.azure.storage.blob.CloudBlobClient;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.ListBlobItem;

import org.apache.commons.net.util.Base64;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ThreadLocalRandom;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.entity.ByteArrayEntity;
import cz.msebera.android.httpclient.entity.StringEntity;


// Include the following imports to use blob APIs.

/**
 * Created by JuhaM on 12.4.2016.
 */

public class IoTHUBClient {
    private static final String TAG = IoTHUBClient.class.getSimpleName();


    private static long MAX_FILESIZE = 256 * 1024;
    private static String URL_PATH_SENDMESSAGE = "%s/devices/%s/messages/events?api-version=2016-02-03";
    private static String URL_PATH_LISTBLOBS = "%s/%s/restype=container&comp=list";
    private static String SIGURL_DEVICE_PATH = "%s/devices/%s";
    private static String URL_PATH_M2MUPLOAD = "%s/m2m/v1/filetransfer/upload/%s?session=%s&filetype=%d&filename=%s";
    private static String URL_PATH_M2M_LOGIN = "%s/m2m/v1/authentication/login?username=operator&password=M2Mdefpw";
    private static String URL_PATH_M2M_LOGOUT = "%s/m2m/v1/authentication/logout?session=%s";

    private static String DEVICELIST_FILE_PATH = "/Sandvik/DeviceList.csv";

    private static int fdmPort = 8283;
    private static int m2mPort = 8282;

    private String hubUrlBase;
    private String m2mUrlBase;
    private String sigUrlBase;
    private String blobStorageUrlBase;
    private int nextMessageId = 0;

    private int ongoingTransfers = 0;
    private SyncHttpClient httpClient = null;

    Object m2mSessionIdLock = new Object();
    String m2mSessionId = null;
    Date sessionAcquired;


    public static boolean isURL(String url) {
        try {
            URL u = new URL(url);
            if (u != null) {
                return Patterns.WEB_URL.matcher(url).matches();
            }
        } catch (Exception e) {
        }

        return false;
    }

    public static boolean isValidUrlBase(String hubUrlBase) {
        // ensure that valid url and does not contain any port or paths by adding port and path.
        if (!isURL(hubUrlBase)) {
            return false;
        }
        if (AppSettings.getServerType() == AppSettings.ServerType.Optimine) {
            if (!isURL(hubUrlBase + ":12345/path/dummy")) {
                return false;
            }
        }
        return true;
    }


/**
     * @param hubUrlBase Base part of url containing protocol and domain, for example:
     *                   https://devfdciothub.azure-devices.net
     */

    public IoTHUBClient(String hubUrlBase) throws URISyntaxException {
        if (!isURL(hubUrlBase)) {
            throw new URISyntaxException(hubUrlBase, "Invalid URL.");
        }

        this.hubUrlBase = hubUrlBase;
        if (AppSettings.getServerType() == AppSettings.ServerType.Optimine) {
            this.hubUrlBase += ":" + fdmPort + "/fdm";
            m2mUrlBase = hubUrlBase + ":" + m2mPort;

            if (!isURL(this.hubUrlBase)) {
                throw new URISyntaxException(this.hubUrlBase, "Invalid URL, remove all ports and paths from base url.");
            }
            if (!isURL(m2mUrlBase)) {
                throw new URISyntaxException(m2mUrlBase, "Invalid URL, remove all ports and paths from base url.");
            }
        }


        // build sigUrlBase, it contains only the domain without protocol.
        int protocolEnds = hubUrlBase.indexOf("://");
        if (protocolEnds < 0)
            throw new URISyntaxException(hubUrlBase, "Protocol delimiter :// not found");

        sigUrlBase = hubUrlBase.substring(protocolEnds + 3);

        httpClient = new SyncHttpClient();
    }

    public int getOngoingTransfers() {
        return ongoingTransfers;
    }

    public void SendFile(Context c, OutboxFile file, AsyncHttpResponseHandler responseHandler) throws IOException {
        long start = System.currentTimeMillis();
        // read file content to memory, file size is limited to 256K so it's ok to read it all to ram.
        FileInputStream fis = new FileInputStream(file.getParentFile());
        if (file.getParentFile().length() > MAX_FILESIZE) {
            Logger.e("IotHUBClient:", "File " + file.getParentFile().getPath() + " is too large, " + file.getParentFile().length() + " bytes.");
            throw new IllegalArgumentException("File is too large.");
        }
        byte[] data = new byte[(int) file.getParentFile().length()];
        fis.read(data);
        fis.close();

        if (file.getAPI() == OutboxFile.APIType.M2M) {
            SendFileM2M(c, data, file, responseHandler, 0);
        } else {
            SendFileFDM(c, data, file, responseHandler);
        }
        long end = System.currentTimeMillis();
        Logger.i("Performance", "Executing SendFile() took " + (end - start) + "ms. Filesize:" + file.getParentFile().length());
    }


    private String getM2MSessionID(boolean getFreshSession, Date failedAttemptTime) throws IOException {
        synchronized (m2mSessionIdLock) {
            // login to server if this is first time, or fresh session requested after we have updated it
            // date check required as concurrent http requests are done, several of them may fail.
            if (m2mSessionId == null || m2mSessionId.isEmpty() ||
                    (getFreshSession && failedAttemptTime.after(sessionAcquired))) {
                m2mSessionId = null;
                String url = String.format(URL_PATH_M2M_LOGIN, m2mUrlBase);
                httpClient.removeAllHeaders();
                httpClient.get(url, new AsyncHttpResponseHandler() {
                    @Override
                    public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                        if (statusCode != 200) {
                            Logger.e(TAG, "M2M Authentication failed, statusCode=" + statusCode);
                        }
                        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
                        DocumentBuilder dBuilder = null;
                        try {
                            dBuilder = dbFactory.newDocumentBuilder();
                            ByteArrayInputStream bis = new ByteArrayInputStream(responseBody);
                            Document doc = dBuilder.parse(bis);
                            doc.getDocumentElement().normalize();
                            NodeList sessionIdNodes = doc.getElementsByTagName("SessionId");
                            if (sessionIdNodes.getLength() == 1) {
                                m2mSessionId = sessionIdNodes.item(0).getTextContent().toString();
                                sessionAcquired = new Date();
                            }
                        } catch (Exception e) {
                            Logger.e(TAG, "M2M Authentication failed to exception.", e);
                            m2mSessionId = null;
                        }
                    }

                    @Override
                    public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                        Logger.e(TAG, "M2M Authentication request failed. statusCode=" + statusCode, error);
                    }
                });


                if (m2mSessionId == null) {
                    Logger.e(TAG, "Unable to get m2mSessionId, something wrong with the Optimine server? Try again after few minutes.");
                    throw new IOException();
                }

            }
            return m2mSessionId;
        }
    }

/*
*
     * Closes the client, must be called after no longer used. And must not be used after called.

*/

    private void Close() {
        // logs out the m2m session
        synchronized (m2mSessionIdLock) {
            if (m2mSessionId != null) {
                // logout, we don't really care if it fails but log anyway.
                Logger.d(TAG, "Logging out.");
                SyncHttpClient client = new SyncHttpClient();
                String url = String.format(URL_PATH_M2M_LOGOUT, m2mUrlBase, m2mSessionId);
                client.get(url, new AsyncHttpResponseHandler() {
                    @Override
                    public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                        Logger.d(TAG, "Logout succeeded.");
                    }

                    @Override
                    public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                        Logger.e(TAG, "Logout FAILED. statusCode=" + statusCode, error);
                    }
                });
            }
        }
    }

    private void SendFileM2M(Context c, byte[] data, OutboxFile file, AsyncHttpResponseHandler responseHandler, int recursiveRetry) throws IOException {
        Logger.d(TAG, "Sending file to M2M api. File:" + file.getParentFile().getName());

        // first get session
        String sessionId = getM2MSessionID(false, null);

        // send alarm logs with fileType 55
        int fileType = 156;
        if (file.getParentFile().getName().startsWith("Alarm")) {
            fileType = 55;
        }

        Logger.d(TAG, "Posting the file.");
        String url = String.format(URL_PATH_M2MUPLOAD, m2mUrlBase, file.getDeviceName(), sessionId, fileType, file.getParentFile().getName());
        httpClient.removeAllHeaders();
        httpClient.addHeader("X-Filename", file.getParentFile().getName());
        // do http post.
        ByteArrayEntity entity = new ByteArrayEntity(data);
        Logger.d("SendFile", "M2MApi URL:" + url);
        ongoingTransfers++;
        Date requestStarted = new Date();
        httpClient.post(c, url, entity, "application/octet-stream", new AsyncHttpResponseHandler() {

            // forward success to caller.
            @Override
            public void onSuccess(int i, Header[] headers, byte[] bytes) {
                ongoingTransfers--;
                Logger.d(TAG, "SendFileFDM() onSuccess() ongoingTransfers=" + ongoingTransfers + " file:" + file.getParentFile().getName());
                responseHandler.onSuccess(i, headers, bytes);
            }

            // implements authentication on demand (402), and forward to caller.
            @Override
            public void onFailure(int i, Header[] headers, byte[] bytes, Throwable throwable) {
                ongoingTransfers--;
                Logger.d(TAG, "SendFileFDM() onFailure() i=" + i + " ongoingTransfers=" + ongoingTransfers + " file:" + file.getParentFile().getName());
                // if fails on 402 (Unauthorized) try to login again and retry once, then fail.
                if (i == 402 && recursiveRetry == 0) {
                    try {
                        getM2MSessionID(true, requestStarted);
                        SendFileM2M(c, data, file, responseHandler, recursiveRetry + 1);
                    } catch (IOException e) {
                        Logger.e(TAG, "SendFileM2M() Retry failed due IOException.", e);
                        responseHandler.onFailure(i, headers, bytes, e);
                    }
                } else {
                    responseHandler.onFailure(i, headers, bytes, throwable);
                }
            }
        });
    }

    private void SendFileFDM(Context c, byte[] data, OutboxFile file, AsyncHttpResponseHandler responseHandler) throws IOException {
        Logger.d(TAG, "Sending to FDM api. Filename:" + file.getParentFile().getName());


        // build http request.
        httpClient.removeAllHeaders();

        String url = String.format(URL_PATH_SENDMESSAGE, hubUrlBase, file.getDeviceName());
        String sigUrl = String.format(SIGURL_DEVICE_PATH, sigUrlBase, file.getDeviceName());
        String key = GetDeviceAccessKey(file.getDeviceName());
        String signature = GetSharedAccessSignature(key, sigUrl, 60);

        httpClient.addHeader("Authorization", signature);
        httpClient.addHeader("IoTHub-app-message-id", String.valueOf(ThreadLocalRandom.current().nextInt(20, 20000 + 1)));
        httpClient.addHeader("IoTHub-app-compressed", "1");

        // do http post.
        ByteArrayEntity entity1 = new ByteArrayEntity(data);
        StringEntity entity = new StringEntity(Base64.encodeBase64String(data));
        // Logger.e("IotHUBClient:", entity.toString());
        Logger.d(TAG, "FDMApi URL:" + url);
        ongoingTransfers++;
        httpClient.post(c, url, entity, "application/atom+xml;type=entry;charset=utf-8", new AsyncHttpResponseHandler() {
            @Override
            public void onSuccess(int i, Header[] headers, byte[] bytes) {
                ongoingTransfers--;
                Logger.d(TAG, "SendFileFDM() onSuccess() ongoingTransfers=" + ongoingTransfers + " file:" + file.getParentFile().getName());
                responseHandler.onSuccess(i, headers, bytes);
            }

            @Override
            public void onFailure(int i, Header[] headers, byte[] bytes, Throwable throwable) {
                ongoingTransfers--;
                Logger.d(TAG, "SendFileFDM() onFailure() i=" + i + " ongoingTransfers=" + ongoingTransfers + " file:" + file.getParentFile().getName());
                responseHandler.onFailure(i, headers, bytes, throwable);
            }
        });
    }


/**
     * Used to syncronize blob storage functions below. To avoid race-condition where delete file
     * is immediately downloaded back if called at the same time.*/


    private static final Object blobLock = new Object();

    public static void DownloadNewFilesFromBlob(List<String> devices) {
        if (devices == null || devices.isEmpty()) return;

        // blob storage not supported by Optimine.
        if (AppSettings.getServerType() == AppSettings.ServerType.Optimine) {
            Logger.d(TAG, "Skip DownloadNewFilesFromBlob for Optimine.");
            return;
        }

        final String storageConnectionString = String.format(
                "DefaultEndpointsProtocol=https;" +
                        "AccountName=%s;" +
                        "AccountKey=%s",
                AppSettings.getBlobStorageAccount(), AppSettings.getBlobStorageKey());

        synchronized (blobLock) {
            if (FsService.isNetworkAvailable()) {
                try {
                    CloudStorageAccount account = CloudStorageAccount.parse(storageConnectionString);
                    CloudBlobClient client = account.createCloudBlobClient();

                    String[] containerNames = new String[]{"configurations", "swpackages"};
                    for (String containerName : containerNames) {
                        CloudBlobContainer container = client.getContainerReference(containerName);

                        // first get all existing files from matching local folder, we sync folder with blob storage.
                        File inbox = new File(AppSettings.getChrootDirAsString() + "/" + containerName);
                        List<File> existingFiles = new LinkedList<File>(Arrays.asList(inbox.listFiles()));

                        try {
                            // iterate all blob items, download all files which doesn't already exist.
                            for (ListBlobItem blobItem : container.listBlobs()) {
                                Logger.d("", blobItem.getUri().toString());
                                if (blobItem instanceof CloudBlob) {
                                    CloudBlob blob = (CloudBlob) blobItem;
                                    // first filter files for given devices.
                                    boolean matchingFile = false;
                                    for (String deviceId : devices) {
                                        if (blob.getName().compareTo(deviceId + "_cfg.tar.gz") == 0 ||
                                                blob.getName().compareTo(deviceId + "_cfg.zip") == 0 ||
                                                blob.getName().compareTo(deviceId + "_swpackage.zip") == 0) {
                                            matchingFile = true;
                                            break;
                                        }
                                    }
                                    if (!matchingFile) continue;
                                    // then filter out files that are already downloaded.
                                    boolean fileExists = false;
                                    for (File file : existingFiles) {
                                        if (file.getName().compareTo(blob.getName()) == 0) {
                                            fileExists = true;
                                            existingFiles.remove(file);
                                            break;
                                        }
                                    }
                                    if (!fileExists) {
                                        // first download to temporary location, then move to ftp folder when done. to avoid partial ftp downloads during blob download.
                                        File downloadedFile = new File(FileStorage.instance().getInboxPath(containerName) + "/" + blob.getName());
                                        Logger.d(TAG, "Downloading Blob file " + downloadedFile.getName());

                                        blob.download(new java.io.FileOutputStream(downloadedFile, false));
                                        FileStorage.moveFile(downloadedFile, new File(AppSettings.getChrootDirAsString() + "/" + containerName));
                                        Logger.d(TAG, "File " + downloadedFile.getName() + " download completed.");
                                    }
                                }
                            }
                        }
                        // blob container not found, nothing we need to do, same as no files exists.
                        catch (NoSuchElementException nse) {
                            Logger.d(TAG, "Blob storage not found");
                        }
                        // what's left in existingFiles are the files that no longer exists in blob, remove them.
                        for (File removedFile : existingFiles) {
                            Logger.d(TAG, "Local file " + removedFile.getAbsolutePath() + " no longer exists in blob, removing.");
                            removedFile.delete();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static boolean DeleteFileFromBlob(String filePath) {
        // blob storage not supported by Optimine.
        if (AppSettings.getServerType() == AppSettings.ServerType.Optimine) {
            Logger.d(TAG, "Skip DeleteFileFromBlob for Optimine.");
            return false;
        }

        final String storageConnectionString = String.format(
                "DefaultEndpointsProtocol=https;" +
                        "AccountName=%s;" +
                        "AccountKey=%s",
                AppSettings.getBlobStorageAccount(), AppSettings.getBlobStorageKey());

        synchronized (blobLock) {
            File file = new File(filePath);
            // parent folder is container name
            String containerName = file.getParentFile().getName().toLowerCase();
            String fileName = file.getName();

            Logger.d(TAG, "Deleting file " + containerName + "/" + fileName + " from Blob");

            try {
                CloudStorageAccount account = CloudStorageAccount.parse(storageConnectionString);
                CloudBlobClient client = account.createCloudBlobClient();
                CloudBlobContainer container = client.getContainerReference(containerName);

                for (ListBlobItem blobItem : container.listBlobs(fileName)) {
                    if (blobItem instanceof CloudBlob) {
                        CloudBlob blob = (CloudBlob) blobItem;
                        if (fileName.compareTo(blob.getName()) == 0) {
                            blob.delete();
                        }
                    }
                }
            }
            // blob container not found, nothing we need to do, same as no file exists. ok.
            catch (NoSuchElementException nse) {
                Logger.d("DeleteFileFromBlob", "Blob storage not found: " + containerName);
                return true; // return true, operation succeeded, file didn't exist.
            } catch (Exception e) {
                Logger.e("DeleteFileFromBlob", "Delete failed to exception.");
                e.printStackTrace();
                return false;
            }
        }
        return true;
    }

    public static String bytesToHex(byte[] in) {
        final StringBuilder builder = new StringBuilder();
        for (byte b : in) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    private String GetDeviceAccessKey(String deviceId) throws IllegalArgumentException {
        try {
            // build the secret, deviceId + reverse(deviceId)
            String secret = deviceId + new StringBuilder(deviceId).reverse().toString();
            // calculate MD5 hex
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(secret.getBytes("UTF-8"));
            String digestHex = bytesToHex(md.digest());
            Logger.d("IoTHUBClient:", "GetDeviceAccessKey: deviceId=" + deviceId + " MD5=" + digestHex);
            // base64 encode the MD5 hex.
            String keyBase64 = Base64.encodeBase64StringUnChunked(digestHex.getBytes("UTF-8"));
            Logger.d("IoTHUBClient:", "GetDeviceAccessKey: return " + keyBase64 + " for device " + deviceId);
            return keyBase64;
        } catch (NoSuchAlgorithmException nsae) {
            nsae.printStackTrace();
            return null;
        } catch (UnsupportedEncodingException uee) {
            uee.printStackTrace();
            return null;
        }
    }

    private static String GetSharedAccessSignature(String sharedAccessKey, String resource, int tokenTimeToLiveSecs) {
        // http://msdn.microsoft.com/en-us/library/azure/dn170477.aspx
        // the canonical Uri scheme is http because the token is not amqp specific
        // signature is computed from joined encoded request Uri string and expiry string
        try {

            long exp = System.currentTimeMillis() / 1000;
            exp += tokenTimeToLiveSecs;

            String expiry = String.valueOf(exp);
            String encodedUri = URLEncoder.encode(resource, "UTF-8");
            String data = encodedUri + "\n" + expiry;

            byte[] accessKey = Base64.decodeBase64(sharedAccessKey);

            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(accessKey, "HmacSHA256");
            sha256_HMAC.init(secret_key);

            String sig = Base64.encodeBase64StringUnChunked(sha256_HMAC.doFinal(data.getBytes()));

            String str = "SharedAccessSignature sr=" + encodedUri + "&sig=" + URLEncoder.encode(sig, "UTF-8") + "&se=" + URLEncoder.encode(expiry, "UTF-8");
            return str;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

}

