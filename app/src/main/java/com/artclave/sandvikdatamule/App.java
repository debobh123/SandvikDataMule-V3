package com.artclave.sandvikdatamule;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;

import com.artclave.sandvikdatamule.util.Logger;

public class App extends Application {

    private static final String TAG = App.class.getSimpleName();

    private static Context sContext;

    @Override
    public void onCreate() {
        super.onCreate();
        sContext = getApplicationContext();
    }

    /**
     * @return the Context of this application
     */
    public static Context getAppContext() {
        if (sContext == null) {
            Logger.w(TAG, "Global context not set");
            sContext = App.getAppContext();
        }
        return sContext;
    }


    /**
     * Get the version from the manifest.
     *
     * @return The version as a String.
     */
    public static String getVersion() {
        Context context = App.getAppContext();
        String packageName = context.getPackageName();
        try {
            PackageManager pm = context.getPackageManager();
            return pm.getPackageInfo(packageName, 0).versionName;
        } catch (NameNotFoundException e) {
            Logger.e(TAG, "Unable to find the name " + packageName + " in the package");
            return null;
        }
    }

}

