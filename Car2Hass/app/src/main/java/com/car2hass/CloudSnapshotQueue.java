package com.car2hass;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Persistent queue of cloud (site) telemetry snapshots, mirroring
 * {@link SnapshotQueue}: rows are read WITHOUT deleting and removed only after a
 * confirmed send ({@link #deleteUpTo}). Survives offline periods and restarts.
 */
public final class CloudSnapshotQueue {

    public static final int DEQUEUE_CHUNK_SIZE = 500;

    private static final String DB_NAME = "cloud_queue.db";
    private static final int DB_VERSION = 1;
    private static final String TABLE = "queue";
    private static final String COL_ID = "id";
    private static final String COL_TS = "ts";
    private static final String COL_LAT = "lat";
    private static final String COL_LON = "lon";
    private static final String COL_SIGNALS = "signals";
    private static final String COL_CREATED = "created";

    private static QueueDbHelper dbHelper = null;

    /** One site snapshot {t, g{lat,lon}, s} staged in the queue. */
    public static final class Snapshot {
        public final long ts;
        public final double lat;
        public final double lon;
        public final String signalJson;
        public long queueId = -1;

        public Snapshot(long ts, double lat, double lon, String signalJson) {
            this.ts = ts;
            this.lat = lat;
            this.lon = lon;
            this.signalJson = signalJson == null ? "{}" : signalJson;
        }
    }

    private static synchronized QueueDbHelper getHelper(Context ctx) {
        if (dbHelper == null) {
            dbHelper = new QueueDbHelper(ctx.getApplicationContext());
        }
        return dbHelper;
    }

    public static void enqueueAll(Context ctx, List<Snapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) return;
        if (!AppConfig.isCloudQueueEnabled(ctx)) {
            LogBuffer.i("CloudSnapshotQueue", "Queue disabled, discarding " + snapshots.size() + " snapshots");
            return;
        }
        evictByAge(ctx, AppConfig.getCloudQueueMaxDays(ctx));
        evictBySize(ctx, AppConfig.getCloudQueueMaxMb(ctx));

        SQLiteDatabase db = getHelper(ctx).getWritableDatabase();
        db.beginTransaction();
        try {
            for (Snapshot snap : snapshots) {
                ContentValues cv = new ContentValues();
                cv.put(COL_TS, snap.ts);
                cv.put(COL_LAT, snap.lat);
                cv.put(COL_LON, snap.lon);
                cv.put(COL_SIGNALS, snap.signalJson);
                cv.put(COL_CREATED, System.currentTimeMillis());
                db.insert(TABLE, null, cv);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        LogBuffer.i("CloudSnapshotQueue", "Enqueued " + snapshots.size() + " snapshots (total: " + getCount(ctx) + ")");
    }

    /**
     * Reads the oldest {@code limit} snapshots WITHOUT deleting them; deletion
     * happens only after a confirmed send ({@link #deleteUpTo}).
     */
    public static List<Snapshot> dequeueChunk(Context ctx, int limit) {
        if (limit <= 0) return new ArrayList<>();
        List<Snapshot> result = new ArrayList<>();
        SQLiteDatabase db = getHelper(ctx).getReadableDatabase();
        Cursor cursor = db.rawQuery(
            "SELECT id, ts, lat, lon, signals FROM " + TABLE + " ORDER BY id ASC LIMIT ?",
            new String[]{String.valueOf(limit)});
        try {
            while (cursor.moveToNext()) {
                Snapshot snap = new Snapshot(
                    cursor.getLong(1),
                    cursor.isNull(2) ? Double.NaN : cursor.getDouble(2),
                    cursor.isNull(3) ? Double.NaN : cursor.getDouble(3),
                    cursor.isNull(4) ? "{}" : cursor.getString(4));
                snap.queueId = cursor.getLong(0);
                result.add(snap);
            }
        } finally {
            cursor.close();
        }
        return result;
    }

    /** Deletes all rows with id &lt;= maxId — called after a confirmed send. */
    public static void deleteUpTo(Context ctx, long maxId) {
        SQLiteDatabase db = getHelper(ctx).getWritableDatabase();
        int deleted = db.delete(TABLE, COL_ID + " <= ?", new String[]{String.valueOf(maxId)});
        if (deleted > 0) {
            LogBuffer.i("CloudSnapshotQueue", "Confirmed " + deleted + " snapshots sent, removed from queue");
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
            LogBuffer.i("CloudSnapshotQueue", "Evicted " + deleted + " old snapshots (age > " + maxDays + " days)");
        }
    }

    public static void evictBySize(Context ctx, int maxMb) {
        if (maxMb <= 0) return;
        long maxBytes = (long) maxMb * 1048576L;
        long currentBytes = getApproximateSize(ctx);
        if (currentBytes <= maxBytes) return;

        SQLiteDatabase db = getHelper(ctx).getReadableDatabase();
        long toFree = currentBytes - maxBytes;
        List<Long> ids = new ArrayList<>();
        List<Long> sizes = new ArrayList<>();
        Cursor cursor = db.rawQuery(
            "SELECT id, LENGTH(signals) + 200 FROM " + TABLE + " ORDER BY id ASC", null);
        try {
            while (cursor.moveToNext()) {
                ids.add(cursor.getLong(0));
                sizes.add(cursor.getLong(1));
            }
        } finally {
            cursor.close();
        }
        long[] idArr = new long[ids.size()];
        long[] sizeArr = new long[sizes.size()];
        for (int i = 0; i < idArr.length; i++) {
            idArr[i] = ids.get(i);
            sizeArr[i] = sizes.get(i);
        }
        long cutoffId = CloudQueuePolicy.evictionCutoffId(toFree, idArr, sizeArr);
        if (cutoffId < 0) return;
        int totalDeleted = db.delete(TABLE, COL_ID + " <= ?", new String[]{String.valueOf(cutoffId)});
        if (totalDeleted > 0) {
            LogBuffer.i("CloudSnapshotQueue", "Evicted " + totalDeleted + " oldest snapshots to stay under " + maxMb + " MB");
        }
    }

    public static void clear(Context ctx) {
        SQLiteDatabase db = getHelper(ctx).getWritableDatabase();
        int deleted = db.delete(TABLE, null, null);
        if (deleted > 0) {
            LogBuffer.i("CloudSnapshotQueue", "Cleared " + deleted + " snapshots from queue");
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
                COL_SIGNALS + " TEXT NOT NULL, " +
                COL_CREATED + " INTEGER NOT NULL" +
                ")");
            db.execSQL("CREATE INDEX idx_cloud_queue_created ON " + TABLE + "(" + COL_CREATED + ")");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // Incremental migrations only — never drop user data here.
            if (oldVersion < 1) {
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_cloud_queue_created ON " + TABLE + "(" + COL_CREATED + ")");
            }
        }
    }

    private CloudSnapshotQueue() {}
}
