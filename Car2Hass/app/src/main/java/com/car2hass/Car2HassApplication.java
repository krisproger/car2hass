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
        LogManager manager = LogManager.init(this);
        LogBuffer.setSink(manager::log);
        manager.start();
        new CrashLogger(this).register();
        if (AppConfig.isCloudSettingsEnabled(this)) {
            CloudSettingsSync.syncAsync(this, "app_start");
        }
    }
}
