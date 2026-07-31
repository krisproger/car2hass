package com.diplustohass.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.diplustohass.LogBuffer;

/**
 * Stops telemetry and cancels keep-alive jobs when the device is shutting down.
 *
 * Without this receiver the foreground service and scheduled restart jobs can
 * keep the process alive across a reboot, so the next BOOT_COMPLETED finds the
 * service already running and the user hears no fresh-start notification sound.
 */
public class ShutdownReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_SHUTDOWN.equals(action)
                && !"android.intent.action.QUICKBOOT_POWEROFF".equals(action)) {
            return;
        }

        LogBuffer.init(context);
        LogBuffer.i("ShutdownReceiver", action + " received — stopping telemetry and canceling keep-alive jobs");

        // Tell TelemetryService to stop without rescheduling a restart.
        try {
            TelemetryService.stop(context);
        } catch (Exception e) {
            LogBuffer.e("ShutdownReceiver", "Failed to stop TelemetryService: " + e.getMessage());
        }

        // Cancel JobScheduler keep-alive jobs.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                JobScheduler js = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
                if (js != null) {
                    js.cancel(BootReceiver.JOB_ID_IMMEDIATE_RESTART);
                    js.cancel(BootReceiver.JOB_ID_PERIODIC_KEEPALIVE);
                    LogBuffer.i("ShutdownReceiver", "Canceled JobScheduler keep-alive jobs");
                }
            } catch (Exception e) {
                LogBuffer.e("ShutdownReceiver", "Failed to cancel JobScheduler jobs: " + e.getMessage());
            }
        }

        // Cancel any AlarmManager restart intent (fallback path on older devices).
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am != null) {
                Intent serviceIntent = new Intent(context, TelemetryService.class);
                PendingIntent pi = PendingIntent.getService(
                        context, 0, serviceIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                am.cancel(pi);
                LogBuffer.i("ShutdownReceiver", "Canceled AlarmManager restart intent");
            }
        } catch (Exception e) {
            LogBuffer.e("ShutdownReceiver", "Failed to cancel AlarmManager intent: " + e.getMessage());
        }
    }
}
