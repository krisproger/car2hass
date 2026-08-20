package com.diplustohass.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.widget.Toast;

import com.diplustohass.AppConfig;
import com.diplustohass.LogBuffer;

public class BootReceiver extends BroadcastReceiver {

    private static final String QUICKBOOT_ACTION = "android.intent.action.QUICKBOOT_POWERON";

    static final int JOB_ID_IMMEDIATE_RESTART = 1001;
    static final int JOB_ID_PERIODIC_KEEPALIVE = 1002;

    @Override
    public void onReceive(Context context, Intent intent) {
        LogBuffer.init(context);
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                || (QUICKBOOT_ACTION.equals(action) && isSystemSender(intent))) {

            LogBuffer.i("BootReceiver", action + " received, sdk=" + Build.VERSION.SDK_INT
                    + " autoStart=" + AppConfig.isBootAutoStartEnabled(context));

            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            PowerManager.WakeLock wl = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK, "diplus2hass:boot");
            wl.acquire(10000);

            try {
                if (!AppConfig.isBootAutoStartEnabled(context)) {
                    LogBuffer.i("BootReceiver", "Boot auto-start disabled — not starting service");
                    return;
                }
                // Service always starts to collect vehicle data locally; HA transmission
                // is enabled separately in settings or via bundled config.

                notifyUser(context);

                // Always try the direct foreground-service path first. On API 29+ this is
                // allowed for a short window after receiving BOOT_COMPLETED, and it is far
                // more reliable than a transparent trampoline activity because Android 10
                // restricts background activity starts. A JobScheduler restart is also
                // scheduled as a safety net in case the direct start is blocked by the OEM.
                Intent serviceIntent = new Intent(context, TelemetryService.class);
                boolean directStarted = false;
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent);
                        LogBuffer.i("BootReceiver", "Started TelemetryService via startForegroundService");
                    } else {
                        context.startService(serviceIntent);
                        LogBuffer.i("BootReceiver", "Started TelemetryService via startService");
                    }
                    directStarted = true;
                } catch (Exception directEx) {
                    LogBuffer.w("BootReceiver", "Direct service start failed: " + directEx.getMessage());
                }

                // Safety net: schedule a near-immediate JobScheduler restart. This works even
                // when direct foreground service starts are blocked and does not require
                // starting an activity from the background.
                try {
                    scheduleRestart(context, directStarted ? 10000 : 1000);
                    LogBuffer.i("BootReceiver", "Scheduled keep-alive restart job after boot");
                } catch (Exception e) {
                    LogBuffer.e("BootReceiver", "Failed to schedule boot restart job: " + e.getMessage());
                }

                // Last-resort fallback for OEM skins that block both direct starts and
                // JobScheduler. Starting an activity from a BOOT_COMPLETED receiver is
                // restricted on Android 10+, so this may not work on all devices.
                if (!directStarted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        Intent trampoline = new Intent(context, BootActivity.class);
                        trampoline.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_CLEAR_TASK
                                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                        context.startActivity(trampoline);
                        LogBuffer.i("BootReceiver", "Started BootActivity trampoline as fallback");
                    } catch (Exception trampEx) {
                        LogBuffer.e("BootReceiver", "BootActivity fallback failed: " + trampEx.getMessage());
                    }
                }
            } catch (Exception e) {
                LogBuffer.e("BootReceiver", "Failed to start from boot: " + e.getMessage());
            } finally {
                if (wl.isHeld()) {
                    wl.release();
                }
            }
        }
    }

    /**
     * QUICKBOOT_* are not protected broadcasts, so any app can spoof them.
     * Accept them only when the sender is the system/shell (UID 1000/2000/0).
     * getCreatorUid() is a hidden API — call it via reflection. On newer Android
     * the call may be blocked; then we accept the broadcast so QUICKBOOT-based
     * boot detection keeps working on OEM devices (the action is already
     * validated above).
     */
    private static boolean isSystemSender(Intent intent) {
        if (intent == null) return false;
        try {
            java.lang.reflect.Method m = Intent.class.getMethod("getCreatorUid");
            int uid = (Integer) m.invoke(intent);
            return uid == android.os.Process.SYSTEM_UID
                    || uid == android.os.Process.SHELL_UID
                    || uid == android.os.Process.ROOT_UID;
        } catch (Exception e) {
            LogBuffer.d("BootReceiver", "getCreatorUid unavailable, accepting QUICKBOOT");
            return true;
        }
    }

    private void notifyUser(Context context) {
        String msg = context.getString(com.diplustohass.R.string.boot_auto_start_toast);
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                Toast.makeText(context.getApplicationContext(), msg, Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                LogBuffer.w("BootReceiver", "Toast failed: " + e.getMessage());
            }
        });
    }

    /**
     * Schedule a service restart. On Lollipop+ we use JobScheduler for both an
     * immediate one-shot restart and a long-term periodic keep-alive job. Older
     * devices fall back to AlarmManager.
     */
    public static void scheduleRestart(Context context, long delayMs) {
        LogBuffer.i("BootReceiver", "scheduleRestart delay=" + delayMs + " ms");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            JobScheduler js = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (js != null) {
                ComponentName component = new ComponentName(context, KeepAliveJobService.class);

                // 1. Immediate one-shot restart with the requested delay.
                try {
                    long oneShotDelay = Math.max(delayMs, 1000L);
                    JobInfo.Builder oneShot = new JobInfo.Builder(JOB_ID_IMMEDIATE_RESTART, component)
                            .setPersisted(true)
                            .setMinimumLatency(oneShotDelay)
                            .setOverrideDeadline(oneShotDelay + 5000L)
                            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE);
                    int result = js.schedule(oneShot.build());
                    if (result == JobScheduler.RESULT_SUCCESS) {
                        LogBuffer.i("BootReceiver", "Scheduled one-shot keep-alive job in " + oneShotDelay + " ms");
                    } else {
                        LogBuffer.w("BootReceiver", "One-shot JobScheduler schedule returned " + result);
                    }
                } catch (Exception e) {
                    LogBuffer.e("BootReceiver", "One-shot JobScheduler failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                }

                // 2. Periodic long-term keep-alive job (OEMs often tolerate periodic
                //    jobs even when exact alarms are blocked).
                try {
                    JobInfo.Builder periodic = new JobInfo.Builder(JOB_ID_PERIODIC_KEEPALIVE, component)
                            .setPersisted(true)
                            .setPeriodic(15 * 60 * 1000L)
                            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE);
                    int result = js.schedule(periodic.build());
                    if (result == JobScheduler.RESULT_SUCCESS) {
                        LogBuffer.i("BootReceiver", "Scheduled periodic keep-alive job (15 min)");
                    } else {
                        LogBuffer.w("BootReceiver", "Periodic JobScheduler schedule returned " + result);
                    }
                } catch (Exception e) {
                    LogBuffer.e("BootReceiver", "Periodic JobScheduler failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                }

                // JobScheduler path succeeded enough; no need for AlarmManager fallback here.
                return;
            } else {
                LogBuffer.w("BootReceiver", "JobScheduler is null");
            }
        }

        // Fallback to AlarmManager for older Android or when JobScheduler is unavailable.
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;

            Intent intent = new Intent(context, TelemetryService.class);
            PendingIntent pi = PendingIntent.getService(
                    context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            long triggerAt = SystemClock.elapsedRealtime() + delayMs;
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi);
                } else {
                    am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi);
                }
                LogBuffer.i("BootReceiver", "Scheduled exact TelemetryService restart in " + delayMs + " ms");
            } catch (SecurityException e) {
                // API 31+ may require SCHEDULE_EXACT_ALARM permission. Fall back to
                // an inexact alarm to avoid crashing the keep-alive path.
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi);
                LogBuffer.w("BootReceiver", "Exact alarm not allowed, scheduled inexact restart");
            }
        } catch (Exception e) {
            LogBuffer.e("BootReceiver", "AlarmManager scheduleRestart failed: " + e.getMessage());
        }
    }
}
