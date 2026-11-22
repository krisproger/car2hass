package com.car2hass;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SnapshotQueue {
    public static final int DEQUEUE_CHUNK_SIZE = 100;
    private static final String PRIMARY = "primary";

    private static final String DB_NAME = "snapshot_queue.db";
    private static final int DB_VERSION = 6;
    private static final String TABLE = "queue";
    private static final String DEAD_TABLE = "dead_letter";
    private static final String COL_ID = "id";
    private static final String COL_ENDPOINT = "endpoint_id";
    private static final String COL_TS = "ts";
    private static final String COL_LAT = "lat";
    private static final String COL_LON = "lon";
    private static final String COL_ACCURACY = "accuracy";
    private static final String COL_FIX_TS = "fix_ts";
    private static final String COL_SIGNALS = "signals";
    private static final String COL_CREATED = "created";
    private static final String COL_DISABLED = "disabled";
    private static final String COL_PAYLOAD = "payload";
    private static final String COL_REASON = "reason";

    private static QueueDbHelper dbHelper = null;

    private static synchronized QueueDbHelper getHelper(Context ctx) {
        if (dbHelper == null) {
            dbHelper = new QueueDbHelper(ctx.getApplicationContext());
        }
        return dbHelper;
    }

    public static void enqueue(Context ctx, String endpointId, HassClient.Snapshot snap) {
        SQLiteDatabase db = getHelper(ctx).getWritableDatabase();
        ContentValues cv = values(endpointId, snap);
        db.insert(TABLE, null, cv);
    }

    public static void enqueue(Context ctx, HassClient.Snapshot snap) {
        enqueue(ctx, PRIMARY, snap);
    }

    public static void enqueueAll(Context ctx, String endpointId,
                                  List<HassClient.Snapshot> snapshots) {
        enqueueAll(ctx, endpointId, snapshots, AppConfig.getQueueMaxMb(ctx));
    }

    public static void enqueueAll(Context ctx, String endpointId,
                                  List<HassClient.Snapshot> snapshots, int maxMb) {
        if (snapshots == null || snapshots.isEmpty()) return;
        if (!AppConfig.isQueueEnabled(ctx)) {
            LogBuffer.i("SnapshotQueue", "Queue disabled, discarding " + snapshots.size() + " snapshots");
            return;
        }
        compact(ctx, endpointId, 2 * 3600, 60);
        evictByAge(ctx, endpointId, AppConfig.getQueueMaxDays(ctx));
        evictBySize(ctx, endpointId, maxMb);

        SQLiteDatabase db = getHelper(ctx).getWritableDatabase();
        db.beginTransaction();
        try {
            for (HassClient.Snapshot snap : snapshots) {
                db.insert(TABLE, null, values(endpointId, snap));
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        LogBuffer.i("SnapshotQueue", "Enqueued " + snapshots.size() + " snapshots for "
                + endpointId + " (total: " + getCount(ctx, endpointId) + ")");
    }

    public static void enqueueAll(Context ctx, List<HassClient.Snapshot> snapshots) {
        enqueueAll(ctx, PRIMARY, snapshots);
    }

    private static ContentValues values(String endpointId, HassClient.Snapshot snap) {
        ContentValues cv = new ContentValues();
        cv.put(COL_ENDPOINT, safeId(endpointId));
        cv.put(COL_TS, snap.timestamp);
        cv.put(COL_LAT, snap.lat);
        cv.put(COL_LON, snap.lon);
        cv.put(COL_ACCURACY, snap.accuracy);
        cv.put(COL_FIX_TS, snap.fixTimeSec);
        cv.put(COL_SIGNALS, snap.signalJson);
        cv.put(COL_DISABLED, snap.disabledJson == null ? "" : snap.disabledJson);
        cv.put(COL_CREATED, System.currentTimeMillis());
        return cv;
    }

    public static List<HassClient.Snapshot> dequeueChunk(Context ctx, String endpointId, int limit) {
        if (limit <= 0) return new ArrayList<>();
        List<HassClient.Snapshot> result = new ArrayList<>();
        SQLiteDatabase db = getHelper(ctx).getReadableDatabase();
        Cursor cursor = db.rawQuery(
            "SELECT id, ts, lat, lon, accuracy, fix_ts, signals, disabled FROM " + TABLE
                    + " WHERE " + COL_ENDPOINT + "=? ORDER BY id ASC LIMIT ?",
            new String[]{safeId(endpointId), String.valueOf(limit)});
        try {
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
                    cursor.getLong(iTs), lat, lon, accuracy, fixTimeSec,
                    cursor.getString(iSig), cursor.isNull(iDis) ? "" : cursor.getString(iDis));
                snap.queueId = cursor.getLong(iId);
                result.add(snap);
            }
        } finally {
            cursor.close();
        }
        if (!result.isEmpty()) {
            LogBuffer.i("SnapshotQueue", "Dequeued " + result.size() + " snapshots for "
                    + endpointId + " (kept until confirmed)");
        }
        return result;
    }

    public static List<HassClient.Snapshot> dequeueChunk(Context ctx, int limit) {
        return dequeueChunk(ctx, PRIMARY, limit);
    }

    public static void deleteUpTo(Context ctx, String endpointId, long maxId) {
        SQLiteDatabase db = getHelper(ctx).getWritableDatabase();
        int deleted = db.delete(TABLE, COL_ENDPOINT + "=? AND " + COL_ID + " <= ?",
                new String[]{safeId(endpointId), String.valueOf(maxId)});
        if (deleted > 0) {
            LogBuffer.i("SnapshotQueue", "Confirmed " + deleted + " snapshots sent for "
                    + endpointId + ", removed from queue");
        }
    }

    public static void deleteUpTo(Context ctx, long maxId) {
        deleteUpTo(ctx, PRIMARY, maxId);
    }

    public static int getCount(Context ctx, String endpointId) {
        SQLiteDatabase db = getHelper(ctx).getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE + " WHERE "
                + COL_ENDPOINT + "=?", new String[]{safeId(endpointId)});
        try {
            if (cursor.moveToFirst()) return cursor.getInt(0);
        } finally {
            cursor.close();
        }
        return 0;
    }

    public static int getCount(Context ctx) {
        return getCount(ctx, PRIMARY);
    }

    /** Persist a batch the server refused to apply (e.g. out_of_order). */
    public static void deadLetter(Context ctx, String endpointId,
                                  HassClient.Snapshot snapshot, String reason) {
        if (snapshot == null) return;
        try {
            SQLiteDatabase db = getHelper(ctx).getWritableDatabase();
            pruneDeadLetters(db, endpointId);
            ContentValues cv = new ContentValues();
            cv.put(COL_ENDPOINT, safeId(endpointId));
            cv.put(COL_TS, snapshot.timestamp);
            cv.put(COL_PAYLOAD, snapshot.toJson().toString());
            cv.put(COL_REASON, reason == null ? "" : reason);
            cv.put(COL_CREATED, System.currentTimeMillis());
            db.insert(DEAD_TABLE, null, cv);
            LogBuffer.w("SnapshotQueue", "Dead-lettered snapshot " + snapshot.timestamp
                    + " for " + endpointId + ": " + reason);
        } catch (Exception e) {
            LogBuffer.e("SnapshotQueue", "deadLetter: " + e.getMessage());
        }
    }

    public static int getDeadLetterCount(Context ctx, String endpointId) {
        SQLiteDatabase db = getHelper(ctx).getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + DEAD_TABLE + " WHERE "
                + COL_ENDPOINT + "=?", new String[]{safeId(endpointId)});
        try {
            if (cursor.moveToFirst()) return cursor.getInt(0);
        } finally {
            cursor.close();
        }
        return 0;
    }

    private static void pruneDeadLetters(SQLiteDatabase db, String endpointId) {
        long cutoff = System.currentTimeMillis() - 7L * 24 * 3600 * 1000;
        db.delete(DEAD_TABLE, COL_ENDPOINT + "=? AND " + COL_CREATED + " < ?",
                new String[]{safeId(endpointId), String.valueOf(cutoff)});
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + DEAD_TABLE + " WHERE "
                + COL_ENDPOINT + "=?", new String[]{safeId(endpointId)});
        int count;
        try {
            count = cursor.moveToFirst() ? cursor.getInt(0) : 0;
        } finally {
            cursor.close();
        }
        if (count > 100) {
            db.execSQL("DELETE FROM " + DEAD_TABLE + " WHERE " + COL_ENDPOINT + "=? AND "
                            + COL_ID + " IN (SELECT " + COL_ID + " FROM " + DEAD_TABLE
                            + " WHERE " + COL_ENDPOINT + "=? ORDER BY " + COL_ID + " ASC LIMIT ?)",
                    new Object[]{safeId(endpointId), safeId(endpointId), count - 100});
        }
    }

    public static int getCountAll(Context ctx) {
        SQLiteDatabase db = getHelper(ctx).getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE, null);
        try {
            if (cursor.moveToFirst()) return cursor.getInt(0);
        } finally {
            cursor.close();
        }
        return 0;
    }

    public static long getApproximateSize(Context ctx, String endpointId) {
        SQLiteDatabase db = getHelper(ctx).getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT SUM(LENGTH(signals)), COUNT(*) FROM " + TABLE
                + " WHERE " + COL_ENDPOINT + "=?", new String[]{safeId(endpointId)});
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

    private static final Map<String, Long> lastCompactMs = new ConcurrentHashMap<>();

    public static void compact(Context ctx, String endpointId, long fullWindowSec, long bucketSec) {
        long now = System.currentTimeMillis();
        String id = safeId(endpointId);
        Long last = lastCompactMs.get(id);
        if (last != null && now - last < 300_000L) return;
        lastCompactMs.put(id, now);
        long cutoff = now / 1000L - fullWindowSec;
        try {
            SQLiteDatabase db = getHelper(ctx).getWritableDatabase();
            int deleted = db.delete(TABLE,
                    COL_ENDPOINT + "=? AND " + COL_TS + " < ? AND " + COL_ID
                            + " NOT IN (SELECT MAX(" + COL_ID + ") FROM " + TABLE
                            + " WHERE " + COL_ENDPOINT + "=? AND " + COL_TS
                            + " < ? GROUP BY (" + COL_TS + " / ?))",
                    new String[]{id, String.valueOf(cutoff), id,
                            String.valueOf(cutoff), String.valueOf(bucketSec)});
            if (deleted > 0) {
                LogBuffer.i("SnapshotQueue", "Compacted " + deleted + " old snapshots for "
                        + id + " (1/" + bucketSec + "s beyond " + fullWindowSec + "s)");
            }
        } catch (Exception e) {
            LogBuffer.w("SnapshotQueue", "compact: " + e.getMessage());
        }
    }

    public static void compact(Context ctx, long fullWindowSec, long bucketSec) {
        compact(ctx, PRIMARY, fullWindowSec, bucketSec);
    }

    public static void evictByAge(Context ctx, String endpointId, int maxDays) {
        if (maxDays <= 0) return;
        SQLiteDatabase db = getHelper(ctx).getWritableDatabase();
        long cutoff = System.currentTimeMillis() - (long) maxDays * 86400000L;
        int deleted = db.delete(TABLE, COL_ENDPOINT + "=? AND " + COL_CREATED + " < ?",
                new String[]{safeId(endpointId), String.valueOf(cutoff)});
        if (deleted > 0) {
            LogBuffer.i("SnapshotQueue", "Evicted " + deleted + " old snapshots for "
                    + endpointId + " (age > " + maxDays + " days)");
        }
    }

    public static void evictByAge(Context ctx, int maxDays) {
        evictByAge(ctx, PRIMARY, maxDays);
    }

    public static void evictBySize(Context ctx, String endpointId, int maxMb) {
        if (maxMb <= 0) return;
        long maxBytes = (long) maxMb * 1048576L;
        String id = safeId(endpointId);
        long currentBytes = getApproximateSize(ctx, id);
        if (currentBytes <= maxBytes) return;

        SQLiteDatabase db = getHelper(ctx).getReadableDatabase();
        long toFree = currentBytes - maxBytes;
        long acc = 0;
        long cutoffId = -1;
        Cursor cursor = db.rawQuery(
            "SELECT id, LENGTH(signals) + 200 AS row_bytes FROM " + TABLE
                    + " WHERE " + COL_ENDPOINT + "=? ORDER BY id ASC",
            new String[]{id});
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
        int totalDeleted = db.delete(TABLE, COL_ENDPOINT + "=? AND " + COL_ID + " <= ?",
                new String[]{id, String.valueOf(cutoffId)});
        if (totalDeleted > 0) {
            LogBuffer.i("SnapshotQueue", "Evicted " + totalDeleted + " oldest snapshots for "
                    + id + " to stay under " + maxMb + " MB");
        }
    }

    public static void evictBySize(Context ctx, int maxMb) {
        evictBySize(ctx, PRIMARY, maxMb);
    }

    public static void clear(Context ctx, String endpointId) {
        SQLiteDatabase db = getHelper(ctx).getWritableDatabase();
        int deleted = db.delete(TABLE, COL_ENDPOINT + "=?", new String[]{safeId(endpointId)});
        if (deleted > 0) {
            LogBuffer.i("SnapshotQueue", "Cleared " + deleted + " snapshots for " + endpointId);
        }
    }

    public static void clear(Context ctx) {
        clearAll(ctx);
    }

    public static void clearAll(Context ctx) {
        SQLiteDatabase db = getHelper(ctx).getWritableDatabase();
        int deleted = db.delete(TABLE, null, null);
        if (deleted > 0) {
            LogBuffer.i("SnapshotQueue", "Cleared " + deleted + " snapshots from queue");
        }
    }

    private static String safeId(String endpointId) {
        return endpointId == null || endpointId.isEmpty() ? PRIMARY : endpointId;
    }

    private static class QueueDbHelper extends SQLiteOpenHelper {
        QueueDbHelper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + TABLE + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_ENDPOINT + " TEXT NOT NULL DEFAULT '', " +
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
            db.execSQL("CREATE INDEX idx_queue_endpoint_id ON " + TABLE + "(" + COL_ENDPOINT + ", id)");
            db.execSQL("CREATE INDEX idx_queue_endpoint_created ON " + TABLE + "(" + COL_ENDPOINT + ", created)");
            createDeadLetterTable(db);
        }

        private void createDeadLetterTable(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS " + DEAD_TABLE + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_ENDPOINT + " TEXT NOT NULL DEFAULT '', " +
                COL_TS + " INTEGER NOT NULL, " +
                COL_PAYLOAD + " TEXT NOT NULL, " +
                COL_REASON + " TEXT NOT NULL DEFAULT '', " +
                COL_CREATED + " INTEGER NOT NULL" +
                ")");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_dead_endpoint_id ON " + DEAD_TABLE
                    + "(" + COL_ENDPOINT + ", " + COL_ID + ")");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
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
            if (oldVersion < 6) {
                createDeadLetterTable(db);
            }
            if (oldVersion < 5) {
                db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN " + COL_ENDPOINT +
                        " TEXT NOT NULL DEFAULT ''");
                db.execSQL("UPDATE " + TABLE + " SET " + COL_ENDPOINT + "='primary' WHERE "
                        + COL_ENDPOINT + "=''");
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_queue_endpoint_id ON " + TABLE +
                        "(" + COL_ENDPOINT + ", id)");
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_queue_endpoint_created ON " + TABLE +
                        "(" + COL_ENDPOINT + ", created)");
            }
        }
    }
}
