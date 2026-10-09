package com.car2hass;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

/**
 * Non-blocking ongoing progress notification for long background tasks
 * (vehicle research, uploads). Replaces modal progress dialogs so the UI
 * stays usable while a task runs.
 */
public final class TaskNotifier {

    private static final String CHANNEL_ID = "car2hass_tasks";
    private static final int NOTIF_ID = 0x0CA2;

    private TaskNotifier() {}

    /** Posts/updates an ongoing progress notification (progress bar when total > 0). */
    public static void progress(Context ctx, String title, String text, int done, int total) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        ensureChannel(nm);
        Notification.Builder b = builder(ctx)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true);
        if (total > 0) b.setProgress(total, done, false);
        nm.notify(NOTIF_ID, b.build());
    }

    /** Finishes the notification (auto-cancels on tap). */
    public static void done(Context ctx, String title, String text) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        ensureChannel(nm);
        nm.notify(NOTIF_ID, builder(ctx)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build());
    }

    /** Removes the notification entirely (e.g. the task was cancelled). */
    public static void clear(Context ctx) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(NOTIF_ID);
    }

    private static Notification.Builder builder(Context ctx) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(ctx, CHANNEL_ID)
                : new Notification.Builder(ctx);
    }

    private static void ensureChannel(NotificationManager nm) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                    "Tasks", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
        }
    }
}
