package com.car2hass;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Wires the batching buffer, the SQLite store and the upload triggers together.
 * Log lines from {@link LogBuffer} are only buffered in memory here; the
 * flusher thread drains them to the DB. The DB log is uploaded on the user's
 * request, once after a crash, or by a once-per-day safety net — never on a
 * short timer.
 */
public final class LogManager {

    private static final String PREFS = "car2hass_logs";
    private static final String KEY_VERSION = "last_version";
    private static final String KEY_FIRST_RUN = "first_run_done";
    private static final String KEY_LAST_AUTO_UPLOAD = "last_auto_upload_ms";
    private static final String KEY_LAST_CRASH_UPLOAD = "last_crash_upload_ms";

    private static final long FLUSH_INTERVAL_MS = 5000L;
    private static final int BLOCK_SIZE = 20;
    private static final int BATCH_LIMIT = 500;

    private static volatile LogManager instance;

    private final Context ctx;
    private final LogDb db;
    private final LogBatchBuffer buffer;
    private final ScheduledExecutorService executor;
    private final LogUploadGate crashGate = new LogUploadGate();

    private LogManager(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.db = new LogDb(this.ctx);
        this.buffer = new LogBatchBuffer(this::writeBatch, BLOCK_SIZE);
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "log-manager");
            t.setDaemon(true);
            return t;
        });
    }

    public static synchronized LogManager init(Context ctx) {
        if (instance == null) instance = new LogManager(ctx);
        return instance;
    }

    public static LogManager get() {
        return instance;
    }

    /** Non-blocking, thread-safe: buffers a log record in memory only. */
    public void log(long ts, String level, String tag, String msg, String source) {
        buffer.add(ts, level, tag, msg, source);
    }

    /** Synchronous crash capture (survives process death via the DB write). */
    public void recordCrash(String stacktrace, String logSnapshot) {
        try {
            db.insertCrash(System.currentTimeMillis(), stacktrace, logSnapshot);
        } catch (Exception e) {
            LogBuffer.e("LogManager", "recordCrash: " + e.getMessage());
        }
        scheduleCrashUpload();
    }

    /** Starts the background flusher and runs one-time maintenance. */
    public void start() {
        executor.execute(this::runMaintenance);
        executor.scheduleWithFixedDelay(this::flushNow,
                FLUSH_INTERVAL_MS, FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);
        executor.execute(this::uploadPendingCrashOnce);
    }

    public void flushNow() {
        try {
            buffer.flush();
            maybeAutoUpload();
        } catch (Exception e) {
            LogBuffer.e("LogManager", "flush: " + e.getMessage());
        }
    }

    public long unsentLogCount() {
        try { return db.countLogs(); } catch (Exception e) { return 0; }
    }

    public long unsentCrashCount() {
        try { return db.countCrashes(); } catch (Exception e) { return 0; }
    }

    private void maybeAutoUpload() {
        long now = System.currentTimeMillis();
        long last = prefs().getLong(KEY_LAST_AUTO_UPLOAD, 0L);
        boolean hasUnsent = unsentLogCount() + unsentCrashCount() > 0;
        if (!LogUploadPolicy.shouldUpload(LogUploadPolicy.Trigger.AUTO, hasUnsent, now, last)) return;
        prefs().edit().putLong(KEY_LAST_AUTO_UPLOAD, now).apply();
        runUpload();
    }

    private void scheduleCrashUpload() {
        if (!crashGate.trySchedule()) return;
        try {
            executor.execute(() -> {
                try {
                    uploadCrashIfDue();
                } finally {
                    crashGate.release();
                }
            });
        } catch (Exception e) {
            crashGate.release();
            LogBuffer.e("LogManager", "scheduleCrashUpload: " + e.getMessage());
        }
    }

    private void uploadPendingCrashOnce() {
        if (unsentCrashCount() <= 0) return;
        if (!crashGate.trySchedule()) return;
        try {
            uploadCrashIfDue();
        } finally {
            crashGate.release();
        }
    }

    private void uploadCrashIfDue() {
        long now = System.currentTimeMillis();
        long last = prefs().getLong(KEY_LAST_CRASH_UPLOAD, 0L);
        boolean hasUnsent = unsentCrashCount() > 0;
        if (!LogUploadPolicy.shouldUpload(LogUploadPolicy.Trigger.CRASH, hasUnsent, now, last)) return;
        prefs().edit().putLong(KEY_LAST_CRASH_UPLOAD, now).apply();
        runUpload();
    }

    private int runUpload() {
        try {
            return new LogUploadCoordinator(db, p -> LogUploader.uploadPayload(ctx, p), BATCH_LIMIT)
                    .uploadOnce();
        } catch (Exception e) {
            LogBuffer.e("LogManager", "upload: " + e.getMessage());
            return 0;
        }
    }

    private SharedPreferences prefs() {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private void writeBatch(List<LogRecord> records) {
        try {
            for (LogRecord r : records) {
                db.insertLog(r.ts, r.level, r.tag, r.msg, r.source);
            }
        } catch (Exception e) {
            LogBuffer.e("LogManager", "writeBatch: " + e.getMessage());
        }
    }

    private void runMaintenance() {
        try {
            SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String previous = prefs.getString(KEY_VERSION, null);
            String current = AppInfo.getVersionName(ctx);
            String persisted = LogRetention.apply(db, System.currentTimeMillis(), previous, current);
            prefs.edit().putString(KEY_VERSION, persisted).apply();

            boolean firstRun = !prefs.getBoolean(KEY_FIRST_RUN, false);
            LogDownloadsCleaner.run(ctx, firstRun);
            if (firstRun) prefs.edit().putBoolean(KEY_FIRST_RUN, true).apply();
        } catch (Exception e) {
            LogBuffer.e("LogManager", "maintenance: " + e.getMessage());
        }
    }
}
