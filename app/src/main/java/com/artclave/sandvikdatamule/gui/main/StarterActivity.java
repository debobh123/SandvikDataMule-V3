package com.artclave.sandvikdatamule.gui.main;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import com.artclave.sandvikdatamule.R;

public final class StarterActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent i = getIntentForStartingActivity();
        startActivity(i);
        finish();
    }

    private Intent getIntentForStartingActivity() {
        Intent i = new Intent();

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.N_MR1) {
            i.setClass(this, UnsupportedActivity.class);

            final String msg = String.format(getString(R.string.android_version_not_supported), Build.VERSION.RELEASE);
            i.putExtra(UnsupportedActivity.INTENT_EXTRA_ID_MESSAGE, msg);
            return i;
        }


        i.setClass(this, MainActivity.class);
        return i;
    }
}

