package com.car2hass;

import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.car2hass.service.TelemetryService;
import com.car2hass.vehicle.ChannelWorkerStatus;

import java.util.List;

/** Read-only diagnostics page: per-channel worker state + global counters. */
public class WorkerStatusActivity extends BaseLocalizedActivity {

    private static final long REFRESH_MS = 2000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = this::refresh;
    private LinearLayout container;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        new CrashLogger(this).register();
        setContentView(R.layout.activity_worker_status);
        container = findViewById(R.id.workerStatusContainer);
        refresh();
        handler.postDelayed(refreshRunnable, REFRESH_MS);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(refreshRunnable);
    }

    private void refresh() {
        if (container == null || isFinishing()) return;
        container.removeAllViews();

        TelemetryService svc = MainActivity.getTelemetryService();
        if (svc == null) {
            container.addView(row(getString(R.string.worker_status_no_service), R.color.textSecondary));
            handler.postDelayed(refreshRunnable, REFRESH_MS);
            return;
        }

        addHeader(getString(R.string.worker_status_title));
        addRow(getString(R.string.worker_status_threads, svc.liveWorkerThreadCount()));
        addRow(getString(R.string.worker_status_values, svc.valueStoreSize()));
        addRow(getString(R.string.worker_status_queue, svc.uploadQueueSize()));

        List<ChannelWorkerStatus> statuses = svc.channelStatuses();
        for (ChannelWorkerStatus s : statuses) {
            addHeader(channelLabel(s.channelId()));
            addRow(statusText(s));
            addRow(getString(R.string.worker_status_cycles, s.cycleCount(), s.errorCount()));
            addRow(getString(R.string.worker_status_uptime, fmt(s.threadUptimeMs())));
            addRow(getString(R.string.worker_status_last_cycle, fmt(s.lastCycleAgeMs())));
        }

        handler.postDelayed(refreshRunnable, REFRESH_MS);
    }

    private String statusText(ChannelWorkerStatus s) {
        if (!s.running()) return getString(R.string.settings_channel_status_off);
        switch (s.lastResult()) {
            case ERROR:
                String reason = s.lastError();
                return getString(R.string.settings_channel_status_error,
                        reason == null || reason.isEmpty() ? "?" : reason);
            case EMPTY:
                return getString(R.string.settings_channel_status_retry, s.retryDelaySeconds());
            default:
                return getString(R.string.settings_channel_status_ok, s.cadenceSeconds());
        }
    }

    private void addHeader(String text) {
        TextView tv = row(text, R.color.textPrimary);
        tv.setTextSize(16);
        tv.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) tv.getLayoutParams();
        lp.topMargin = dp(12);
        tv.setLayoutParams(lp);
    }

    private void addRow(String text) {
        row(text, R.color.textSecondary);
    }

    private TextView row(String text, int colorRes) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTextColor(getResources().getColor(colorRes));
        tv.setPadding(0, dp(4), 0, dp(4));
        container.addView(tv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return tv;
    }

    private static String channelLabel(String id) {
        switch (id == null ? "" : id) {
            case "system": return "System";
            case "diplus": return "DiPlus";
            case "diplus_push": return "DiPlus Push";
            case "adb": return "ADB";
            case "obd": return "OBD";
            case "voyah": return "Voyah";
            case "dumpsys": return "Dumpsys";
            case "byd_cloud": return "BYD Cloud";
            default: return id;
        }
    }

    private static String fmt(long ms) {
        if (ms < 0) return "—";
        long s = ms / 1000;
        if (s < 60) return s + "s";
        return (s / 60) + "m " + (s % 60) + "s";
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
