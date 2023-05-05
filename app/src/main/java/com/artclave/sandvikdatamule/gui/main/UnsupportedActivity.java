package com.artclave.sandvikdatamule.gui.main;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import com.artclave.sandvikdatamule.R;

public final class UnsupportedActivity extends AppCompatActivity {
    public final static String INTENT_EXTRA_ID_MESSAGE = "msg";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.unsupported_page);

        final String msg = getIntentMsg();
        final String errorMessage = msg != null ? msg : getString(R.string.app_not_supported);

        TextView msgTextView = findViewById(R.id.unsupportedTextView);
        msgTextView.setText(errorMessage);
    }

    private String getIntentMsg() {
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            return extras.getString(INTENT_EXTRA_ID_MESSAGE);
        }
        return null;
    }
}

