package com.car2hass;

import android.content.Context;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Telemetry send tracking: rolling reference + last successful send (via AppConfig). */
public final class SendHistory {

    private SendHistory() {}

    public static void record(Context ctx, int bytes, int count) {
        long now = System.currentTimeMillis();
        AppConfig.setSendHistory(ctx,
                SendHistoryCore.append(AppConfig.getSendHistory(ctx), now, bytes));
        AppConfig.setLastSendTs(ctx, now);
        AppConfig.setLastSendBytes(ctx, bytes);
        AppConfig.setLastSendCount(ctx, count);
    }

    /** Bar reference: recent 2h throughput, never below the floor. */
    public static long getReferenceBytes(Context ctx) {
        long raw = SendHistoryCore.computeReference(
                AppConfig.getSendHistory(ctx), System.currentTimeMillis());
        return Math.max(raw, SendHistoryCore.FLOOR_BYTES);
    }

    public static long getPendingBytes(Context ctx) {
        return SnapshotQueue.getApproximateSize(ctx);
    }

    public static String formatBytes(long bytes) {
        if (bytes >= 1024 * 1024) {
            return String.format(Locale.US, "%.1f МБ", bytes / (1024.0 * 1024.0));
        }
        if (bytes >= 1024) return (bytes / 1024) + " КБ";
        return bytes + " Б";
    }

    public static String getLastSendLabel(Context ctx) {
        long ts = AppConfig.getLastSendTs(ctx);
        if (ts == 0) return "—";
        return new SimpleDateFormat("HH:mm", Locale.US).format(new Date(ts))
                + " · " + formatBytes(AppConfig.getLastSendBytes(ctx))
                + " · " + AppConfig.getLastSendCount(ctx) + " шт";
    }

    public static void recordAttempt(Context ctx, String result) {
        AppConfig.setLastAttemptTs(ctx, System.currentTimeMillis());
        AppConfig.setLastAttemptResult(ctx, result != null ? result : "");
    }

    public static String getLastAttemptLabel(Context ctx) {
        long ts = AppConfig.getLastAttemptTs(ctx);
        if (ts == 0) return "—";
        return new SimpleDateFormat("HH:mm", Locale.US).format(new Date(ts))
                + " · " + AppConfig.getLastAttemptResult(ctx);
    }
}