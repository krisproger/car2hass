package com.diplustohass;

import android.content.Context;
import android.os.Bundle;
import android.app.Activity;

/**
 * Base activity that applies the user-selected (or default) locale before
 * inflating the layout. All application activities extend this class.
 */
public class BaseLocalizedActivity extends Activity {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.attach(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }
}
