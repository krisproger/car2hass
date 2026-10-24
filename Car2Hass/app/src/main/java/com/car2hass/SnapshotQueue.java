package com.car2hass;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class SnapshotQueue {
    // Half of the HA integration's MAX_BATCH_SNAPSHOTS (1000, custom_components/
    // cartelemetry/__init__.py). Keep the app chunk below that hard limit so a flush
    // never gets rejected as "too many snapshots" — keep both values in sync.
    public static final int DEQUEUE_CHUNK_SIZE = 500;

    private static final String DB_NAME = "snapshot_queue.db";
    private static final int DB_VERSION = 4;
    private static final String TABLE = "queue";
    private static final String COL_ID = "id";
    private static final String COL_TS = "ts";
    private static final String COL_LAT = "lat";
    private static final String COL_LON = "lon";
    private static final String COL_ACCURACY = "accuracy";
    private static final String COL_FIX_TS = "fix_ts";
    private static final String COL_SIGNALS = "signals";
    private static final String COL_CREATED = "created";
    private static final String COL_DISABLED = "disabled";

    private static QueueDbHelper dbHelper = null;

    private static synchronized QueueDbHelper getHelper(Context ctx) {
        if (dbHelper == null) {
            dbHelper = new QueueDbHelper(ctx.getApplicationContext());
        }
        return dbHelper;
    }

    public static void enqueue(Context ctx, HassClient.Snapshot snap) {
        SQLiteDatabase db = getHelper(ctx).getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_TS, snap.timestamp);
        cv.put(COL_LAT, snap.lat);
        cv.put(COL_LON, snap.lon);
        cv.put(COL_ACCURACY, snap.accuracy);
        cv.put(COL_FIX_TS, snap.fixTimeSec);
        cv.put(COL_SIGNALS, snap.signalJson);
        cv.put(COL_DISABLED, snap.disabledJson == null ? "" : snap.disabledJson);
        cv.put(COL_CREATED, System.currentTimeMillis());
        db.insert(TABLE, null, cv);
    }

    public static void enqueueAll(Context ctx, List<HassClient.Snapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) return;
        if (!AppConfig.isQueueEnabled(ctx)) {
            LogBuffer.i("SnapshotQueue", "Queue disabled, discarding " + snapshots.size() + " snapshots");
            return;
        }
        compact(ctx, 2 * 3600, 60);
        evictByAge(ctx, AppConfig.getQueueMaxDays(ctx));
        evictBySize(ctx, AppConfig.getQueueMaxMb(ctx));

        SQLiteDatabase db = getHelper(ctx).getWritableDatabase();
        db.beginTransaction();
        try {
            for (HassClient.Snapshot snap : snapshots) {
                ContentValues cv = new ContentValues();
                cv.put(COL_TS, snap.timestamp);
                cv.put(COL_LAT, snap.lat);
                cv.put(COL_LON, snap.lon);
                cv.put(COL_ACCURACY, snap.accuracy);
                cv.put(COL_FIX_TS, snap.fixTimeSec);
                cv.put(COL_SIGNALS, snap.signalJson);
        cv.put(COL_DISABLED, snap.disabledJson == null ? "" : snap.disabledJson);
                cv.put(COL_CREATED, System.currentTimeMillis());
                db.insert(TABLE, null, cv);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        LogBuffer.i("SnapshotQueue", "Enqueued " + snapshots.size() + " snapshots (total: " + getCount(ctx) + ")");
    }

    /**
     * Read the oldest {@code limit} queued snapshots WITHOUT deleting them.
     * Deletion happens only after a confirmed successful send (see
     * {@link #deleteUpTo}). This fixes two data-loss bugs:
     * - snapshots inserted between the SELECT and DELETE were destroyed unread;
     * - a process kill between dequeue and re-enqueue-on-error lost the whole batch.
     * Returns each snapshot with its database id set (via {@link HassClient.Snapshot#queueId}).
     */
    public static List<HassClient.Snapshot> dequeueChunk(Context ctx, int limit) {
        if (limit <= 0) return new ArrayList<>();
        List<HassClient.Snapshot> result = new ArrayList<>();
        SQLiteDatabase db = getHelper(ctx).getReadableDatabase();
        Cursor cursor = db.rawQuery(
            "SELECT id, ts, lat, lon, accuracy, fix_ts, signals, disabled FROM " + TABLE
                    + " ORDER BY id ASC LIMIT ?",
            new String[]{String.valueOf(limit)});
        try {
            // Resolve columns by name so a SELECT/read index drift can never happen
            // again (a previous build read index 7 while the SELECT listed 7 columns,
            // throwing on every non-empty queue and permanently blocking offline sync).
            int iId = cursor.getColumnIndexOrThrow(COL_ID);
            int iTs = cursor.getColumnIndexOrThrow(COL_TS);
            int iLat = cursor.getColumnIndexOrThrow(COL_LAT);
            int iLon = cursor.getColumnIndexOrThrow(COL_LON);
            int iAcc = cursor.getColumnIndexOrThrow(COL_ACCURACY);
            int iFix = cursor.getColumnIndexOrThrow(COL_FIX_TS);
            int iSig = cursor.getColumnIndexOrThrow(COL_SIGNALS);
            int iDis = cursor.getColumnIndexOrThrow(COL_DISABLED);
            while (cursor.moveToNext()) {
                double lat = cursor.isNull(iLat) ? Double.NaN : cursor.getDouble(iLat);
                double lon = cursor.isNull(iLon) ? Double.NaN : cursor.getDouble(iLon);
                float accuracy = cursor.isNull(iAcc) ? 0 : cursor.getFloat(iAcc);
                long fixTimeSec = cursor.isNull(iFix) ? 0 : cursor.getLong(iFix);
                HassClient.Snapshot snap = new HassClient.Snapshot(
                    cursor.getLong(iTs),
                    lat,
                    lon,
                    accuracy,
                    fixTimeSec,
                    cursor.getString(iSig),
                    cursor.isNull(iDis) ? "" : cursor.getString(iDis)
                );
                snap.queueId = cursor.getLong(iId);
                result.add(snap);
            }
        } finally {
            cursor.close();
        }
        if (!result.isEmpty()) {
            LogBuffer.i("SnapshotQueue", "Dequeued " + result.size() + " snapshots for flush (kept in queue until confirmed)");
        }
        return result;
    }

    /**
     * Delete all queued rows with id &lt;= maxId — called after a confirmed send.
     * Single DELETE is atomic; ids below the watermark can never be sent again.
     */
    public static void deleteUpTo(Context ctx, long maxId) {
        SQLiteDatabase db = getHelper(ctx).getWritableDatabase();
        int deleted = db.delete(TABLE, COL_ID + " <= ?", new String[]{String.valueOf(maxId)});
        if (deleted > 0) {
            LogBuffer.i("SnapshotQueue", "Confirmed " + deleted + " snapshots sent, removed from queue");
        }
    }

    public static int getCount(Context ctx) {
        SQLiteDatabase db = getHelper(ctx).getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE, null);
        try {
            if (cursor.moveToFirst()) return cursor.getInt(0);
        } finally {
            cursor.close();
        }
        return 0;
    }

    public static long getApproximateSize(Context ctx) {
        SQLiteDatabase db = getHelper(ctx).getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT SUM(LENGTH(signals)), COUNT(*) FROM " + TABLE, null);
        try {
            if (cursor.moveToFirst()) {
                long signalsLen = cursor.isNull(0) ? 0 : cursor.getLong(0);
                long count = cursor.getLong(1);
                return signalsLen + count * 200;
            }
        } finally {
            cursor.close();
        }
        return 0;
    }

    private static volatile long lastCompactMs = 0;

    /**
     * Offline backlog compaction: keep full resolution for the last
     * {@code fullWindowSec}, but beyond that keep at most one snapshot per
     * {@code bucketSec} (latest per bucket). Bounds a 24 h outage to ~8.5k rows
     * instead of 86k without losing the GPS track. Runs at most every 5 min.
     */
    public static void compact(Context ctx, long fullWindowSec, long bucketSec) {
        long now = System.currentTimeMillis();
        if (now - lastCompactMs < 300_000L) return;
        lastCompactMs = now;
        long cutoff = now / 1000L - fullWindowSec;
        try {
            SQLiteDatabase db = getHelper(ctx).getWritableDatabase();
            int deleted = db.delete(TABLE,
                    COL_TS + " < ? AND " + COL_ID + " NOT IN (SELECT MAX(" + COL_ID + ") FROM "
                            + TABLE + " WHERE " + COL_TS + " < ? GROUP BY (" + COL_TS + " / ?))",
                    new String[]{String.valueOf(cutoff), String.valueOf(cutoff),
                            String.valueOf(bucketSec)});
            if (deleted > 0) {
                LogBuffer.i("SnapshotQueue", "Compacted " + deleted + " old snapshots (1/"
                        + bucketSec + "s beyond " + fullWindowSec + "s)");
            }
        } catch (Exception e) {
            LogBuffer.w("SnapshotQueue", "compact: " + e.getMessage());
        }
    }

    public static void evictByAge(Context ctx, int maxDays) {
        if (maxDays <= 0) return;
        SQLiteDatabase db = getHelper(ctx).getWritableDatabase();
        long cutoff = System.currentTimeMillis() - (long) maxDays * 86400000L;
        int deleted = db.delete(TABLE, COL_CREATED + " < ?", new String[]{String.valueOf(cutoff)});
        if (deleted > 0) {
            LogBuffer.i("SnapshotQueue", "Evicted " + deleted + " old snapshots (age > " + maxDays + " days)");
        }
    }

    public static void evictBySize(Context ctx, int maxMb) {
        if (maxMb <= 0) return;
        long maxBytes = (long) maxMb * 1048576L;
        long currentBytes = getApproximateSize(ctx);
        if (currentBytes <= maxBytes) return;

        // Single pass: walk rows oldest-first accumulating each row's footprint and
        // remember the id where the accumulated size covers the overflow. Then one
        // DELETE removes everything up to that id. Old code called getApproximateSize()
        // (a full SUM(LENGTH(signals)) scan) once per 100 deleted rows — O(n^2).
        SQLiteDatabase db = getHelper(ctx).getReadableDatabase();
        long toFree = currentBytes - maxBytes;
        long acc = 0;
        long cutoffId = -1;
        Cursor cursor = db.rawQuery(
            "SELECT id, LENGTH(signals) + 200 AS row_bytes FROM " + TABLE + " ORDER BY id ASC",
            null);
        try {
            while (cursor.moveToNext()) {
                acc += cursor.getLong(1);
                cutoffId = cursor.getLong(0);
                if (acc >= toFree) break;
            }
        } finally {
            cursor.close();
        }
        if (cutoffId < 0) return;
        int totalDeleted = db.delete(TABLE, COL_ID + " <= ?", new String[]{String.valueOf(cutoffId)});
        if (totalDeleted > 0) {
            LogBuffer.i("SnapshotQueue", "Evicted " + totalDeleted + " oldest snapshots to stay under " + maxMb + " MB");
        }
    }

    public static void clear(Context ctx) {
        SQLiteDatabase db = getHelper(ctx).getWritableDatabase();
        int deleted = db.delete(TABLE, null, null);
        if (deleted > 0) {
            LogBuffer.i("SnapshotQueue", "Cleared " + deleted + " snapshots from queue");
        }
    }

    private static class QueueDbHelper extends SQLiteOpenHelper {
        QueueDbHelper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + TABLE + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_TS + " INTEGER NOT NULL, " +
                COL_LAT + " REAL, " +
                COL_LON + " REAL, " +
                COL_ACCURACY + " REAL, " +
                COL_FIX_TS + " INTEGER NOT NULL DEFAULT 0, " +
                COL_SIGNALS + " TEXT NOT NULL, " +
                COL_CREATED + " INTEGER NOT NULL, " +
                COL_DISABLED + " TEXT" +
                ")");
            db.execSQL("CREATE INDEX idx_queue_created ON " + TABLE + "(" + COL_CREATED + ")");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // Incremental migrations only — never drop user data here.
            if (oldVersion < 2) {
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_queue_created ON " + TABLE + "(" + COL_CREATED + ")");
            }
            if (oldVersion < 3) {
                db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN " + COL_FIX_TS +
                        " INTEGER NOT NULL DEFAULT 0");
            }
            if (oldVersion < 4) {
                db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN " + COL_DISABLED + " TEXT");
            }
        }
    }
}
