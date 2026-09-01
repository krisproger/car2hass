package com.car2hass.service;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

import com.car2hass.AppConfig;
import com.car2hass.LogBuffer;

/**
 * Transparent 1x1 activity used on API >= 29 to start TelemetryService from BOOT_COMPLETED.
 *
 * Starting with Android 10 (API 29), BYD DiLink blocks direct startServiceLocked after
 * BOOT_COMPLETED. A BOOT_COMPLETED receiver cannot reliably start a foreground service
 * directly, so diplus uses a tiny transparent activity as a trampoline. We mirror that
 * pattern here.
 */
public class BootActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LogBuffer.init(this);
        try {
            requestWindowFeature(Window.FEATURE_NO_TITLE);
            Window window = getWindow();
            if (window != null) {
                window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    window.setDimAmount(0f);
                }
            }
        } catch (Exception e) {
            LogBuffer.w("BootActivity", "Window setup failed: " + e.getMessage());
        }
        LogBuffer.i("BootActivity", "Trampoline started from boot, sdk=" + android.os.Build.VERSION.SDK_INT);

        // Always start the telemetry service on boot so vehicle data is collected
        // and displayed even before HA is configured.
        try {
            TelemetryService.start(this);
            LogBuffer.i("BootActivity", "TelemetryService.start() invoked");
        } catch (Exception e) {
            LogBuffer.e("BootActivity", "Failed to start TelemetryService: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        // Bring the main activity to the foreground briefly and then minimize it.
        // This improves survival on BYD DiLink and other OEM skins that restrict
        // background services unless the app has been shown at least once.
        Intent main = new Intent(this, com.car2hass.MainActivity.class);
        main.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        main.putExtra("from_boot", true);
        try {
            startActivity(main);
            LogBuffer.i("BootActivity", "MainActivity started");
        } catch (Exception e) {
            LogBuffer.e("BootActivity", "Failed to start MainActivity: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        // Keep this transparent activity alive a little longer so the foreground
        // service has time to call startForeground before the process loses its
        // foreground state.
        try {
            getWindow().getDecorView().postDelayed(this::finish, 1500);
        } catch (Exception e) {
            LogBuffer.e("BootActivity", "Delayed finish failed: " + e.getMessage());
            finish();
        }
    }
}
