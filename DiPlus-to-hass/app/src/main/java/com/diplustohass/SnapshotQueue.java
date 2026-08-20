package com.diplustohass;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class SnapshotQueue {
    // Half of the HA integration's MAX_BATCH_SNAPSHOTS (1000, custom_components/
    // diplus2hass/__init__.py). Keep the app chunk below that hard limit so a flush
    // never gets rejected as "too many snapshots" — keep both values in sync.
    public static final int DEQUEUE_CHUNK_SIZE = 500;

    private static final String DB_NAME = "snapshot_queue.db";
    private static final int DB_VERSION = 3;
    private static final String TABLE = "queue";
    private static final String COL_ID = "id";
    private static final String COL_TS = "ts";
    private static final String COL_LAT = "lat";
    private static final String COL_LON = "lon";
    private static final String COL_ACCURACY = "accuracy";
    private static final String COL_FIX_TS = "fix_ts";
    private static final String COL_SIGNALS = "signals";
    private static final String COL_CREATED = "created";

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
        cv.put(COL_CREATED, System.currentTimeMillis());
        db.insert(TABLE, null, cv);
    }

    public static void enqueueAll(Context ctx, List<HassClient.Snapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) return;
        if (!AppConfig.isQueueEnabled(ctx)) {
            LogBuffer.i("SnapshotQueue", "Queue disabled, discarding " + snapshots.size() + " snapshots");
            return;
        }
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
            "SELECT id, ts, lat, lon, accuracy, fix_ts, signals FROM " + TABLE + " ORDER BY id ASC LIMIT ?",
            new String[]{String.valueOf(limit)});
        try {
            while (cursor.moveToNext()) {
                double lat = cursor.isNull(2) ? Double.NaN : cursor.getDouble(2);
                double lon = cursor.isNull(3) ? Double.NaN : cursor.getDouble(3);
                float accuracy = cursor.isNull(4) ? 0 : cursor.getFloat(4);
                long fixTimeSec = cursor.isNull(5) ? 0 : cursor.getLong(5);
                HassClient.Snapshot snap = new HassClient.Snapshot(
                    cursor.getLong(1),
                    lat,
                    lon,
                    accuracy,
                    fixTimeSec,
                    cursor.getString(6)
                );
                snap.queueId = cursor.getLong(0);
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
                COL_CREATED + " INTEGER NOT NULL" +
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
        }
    }
}
