package com.diplustohass;

import android.app.Application;

public class DiPlusApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        LogBuffer.init(this);
        AppConfig.migrateEnabledToDisabledIfNeeded(this);
        new CrashLogger(this).register();
    }
}
