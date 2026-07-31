package com.diplustohass;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class SnapshotQueue {
    private static final String DB_NAME = "snapshot_queue.db";
    private static final int DB_VERSION = 2;
    private static final String TABLE = "queue";
    private static final String COL_ID = "id";
    private static final String COL_TS = "ts";
    private static final String COL_LAT = "lat";
    private static final String COL_LON = "lon";
    private static final String COL_ACCURACY = "accuracy";
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

    public static List<HassClient.Snapshot> dequeueAll(Context ctx) {
        List<HassClient.Snapshot> result = new ArrayList<>();
        SQLiteDatabase db = getHelper(ctx).getWritableDatabase();
        Cursor cursor = db.rawQuery("SELECT ts, lat, lon, accuracy, signals FROM " + TABLE + " ORDER BY id ASC", null);
        try {
            while (cursor.moveToNext()) {
                double lat = cursor.isNull(1) ? Double.NaN : cursor.getDouble(1);
                double lon = cursor.isNull(2) ? Double.NaN : cursor.getDouble(2);
                float accuracy = cursor.isNull(3) ? 0 : cursor.getFloat(3);
                result.add(new HassClient.Snapshot(
                    cursor.getLong(0),
                    lat,
                    lon,
                    accuracy,
                    cursor.getString(4)
                ));
            }
        } finally {
            cursor.close();
        }
        db.delete(TABLE, null, null);
        if (!result.isEmpty()) {
            LogBuffer.i("SnapshotQueue", "Dequeued " + result.size() + " snapshots for flush");
        }
        return result;
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
        int totalDeleted = 0;
        while (getApproximateSize(ctx) > maxBytes) {
            SQLiteDatabase db = getHelper(ctx).getWritableDatabase();
            int deleted = db.delete(TABLE, COL_ID + " IN (SELECT " + COL_ID + " FROM " + TABLE + " ORDER BY " + COL_ID + " ASC LIMIT 100)", null);
            if (deleted <= 0) break;
            totalDeleted += deleted;
        }
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
            // Future migrations: add `if (oldVersion < N)` blocks below.
        }
    }
}
