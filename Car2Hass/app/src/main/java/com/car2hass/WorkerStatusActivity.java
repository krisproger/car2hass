package com.car2hass;

import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.car2hass.service.TelemetryService;
import com.car2hass.vehicle.ChannelWorkerStatus;

import java.util.ArrayList;
import java.util.List;

/** Read-only diagnostics page: per-channel worker state + global counters. */
public class WorkerStatusActivity extends BaseLocalizedActivity {

    private static final long REFRESH_MS = 2000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = this::refresh;
    private LinearLayout container;

    /** Last rendered content signature; skips the rebuild when nothing changed. */
    private String lastSignature;
    /** Last good snapshot, kept so a momentary service unbind does not blank the page. */
    private List<ChannelWorkerStatus> cachedStatuses;
    private int cachedThreads;
    private int cachedValues;
    private int cachedQueue;
    private boolean haveSnapshot;

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

        TelemetryService svc = MainActivity.getTelemetryService();
        boolean live = svc != null;
        if (live) {
            cachedThreads = svc.liveWorkerThreadCount();
            cachedValues = svc.valueStoreSize();
            cachedQueue = svc.uploadQueueSize();
            cachedStatuses = svc.channelStatuses();
            haveSnapshot = true;
        }

        List<Row> rows = new ArrayList<>();
        if (!live && !haveSnapshot) {
            rows.add(new Row(getString(R.string.worker_status_no_service), R.color.textSecondary, 13, false));
        } else {
            rows.add(new Row(getString(R.string.worker_status_title), R.color.textPrimary, 16, true));
            rows.add(new Row(getString(R.string.worker_status_threads, cachedThreads), R.color.textSecondary, 13, false));
            rows.add(new Row(getString(R.string.worker_status_values, cachedValues), R.color.textSecondary, 13, false));
            rows.add(new Row(getString(R.string.worker_status_queue, cachedQueue), R.color.textSecondary, 13, false));
            if (!live) {
                // Keep the last snapshot on screen instead of blanking it.
                rows.add(new Row(getString(R.string.worker_status_reconnecting), R.color.accentYellow, 13, false));
            }
            if (cachedStatuses != null) {
                for (ChannelWorkerStatus s : cachedStatuses) {
                    rows.add(new Row(channelLabel(s.channelId()), R.color.textPrimary, 16, true));
                    rows.add(new Row(statusText(s), R.color.textSecondary, 13, false));
                    if (!s.phase().isEmpty()) {
                        rows.add(new Row(getString(R.string.worker_status_phase, s.phase()), R.color.textSecondary, 13, false));
                    }
                    rows.add(new Row(getString(R.string.worker_status_cycles, s.cycleCount(), s.errorCount()), R.color.textSecondary, 13, false));
                    rows.add(new Row(getString(R.string.worker_status_uptime, fmt(s.threadUptimeMs())), R.color.textSecondary, 13, false));
                    rows.add(new Row(getString(R.string.worker_status_last_cycle, fmt(s.lastCycleAgeMs())), R.color.textSecondary, 13, false));
                }
            }
        }
        render(rows);
        handler.postDelayed(refreshRunnable, REFRESH_MS);
    }

    private void render(List<Row> rows) {
        StringBuilder sig = new StringBuilder();
        for (Row r : rows) sig.append(r.text).append('\u0001').append(r.colorRes).append('\u0001')
                .append(r.size).append('\u0001').append(r.bold).append('\u0002');
        String signature = sig.toString();
        if (signature.equals(lastSignature)) return;
        lastSignature = signature;

        container.removeAllViews();
        for (Row r : rows) {
            TextView tv = new TextView(this);
            tv.setText(r.text);
            tv.setTextSize(r.size);
            tv.setTextColor(getResources().getColor(r.colorRes));
            if (r.bold) {
                tv.setTypeface(null, Typeface.BOLD);
                tv.setPadding(0, dp(12), 0, dp(4));
            } else {
                tv.setPadding(0, dp(4), 0, dp(4));
            }
            container.addView(tv, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
    }

    /** One rendered line (text + style), used to detect changes without rebuilding. */
    private static final class Row {
        final String text;
        final int colorRes;
        final float size;
        final boolean bold;
        Row(String text, int colorRes, float size, boolean bold) {
            this.text = text;
            this.colorRes = colorRes;
            this.size = size;
            this.bold = bold;
        }
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
            case "phone": return "Phone";
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
