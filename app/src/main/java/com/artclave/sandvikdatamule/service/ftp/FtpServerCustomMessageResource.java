package com.artclave.sandvikdatamule.service.ftp;

import org.apache.ftpserver.message.MessageResource;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * Created by juham on 15/05/2018.
 */

public class FtpServerCustomMessageResource implements MessageResource {

    private MessageResource origResource = null;

    public FtpServerCustomMessageResource(MessageResource orig) {
        origResource = orig;
    }

    public List<String> getAvailableLanguages() {
        return origResource.getAvailableLanguages();
    }

    public String getMessage(int var1, String var2, String var3) {
        if (var1 == 220)
        {
            // TIME=yyyy-mm-dd hh:mm:ss
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
            String date = formatter.format(new Date());
            return "TIME=" + date;

        }
        else {
            return origResource.getMessage(var1, var2, var3);
        }
    }

    public Map<String, String> getMessages(String var1)
    {
        return origResource.getMessages(var1);
    }

}

