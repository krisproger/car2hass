package com.diplustohass;

import android.app.Application;

public class DiPlusApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        LogBuffer.init(this);
        LogBuffer.setFileLogMode(AppConfig.getFileLogMode(this));
        AppConfig.migrateEnabledToDisabledIfNeeded(this);
        new CrashLogger(this).register();
    }
}
