package com.car2hass;

import android.app.Application;

public class Car2HassApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AdbShellExecutor.init(this);
        LogBuffer.init(this);
        LogBuffer.setFileLogMode(AppConfig.getFileLogMode(this));
        AppConfig.migrateEnabledToDisabledIfNeeded(this);
        new CrashLogger(this).register();
    }
}
