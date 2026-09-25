package com.car2hass;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * SQLite implementation of {@link LogRecordStore}. Tables:
 * {@code log_records(id, ts, level, tag, msg, sent, upload_id, source)} indexed
 * on {@code (sent, ts)}, and {@code crash_records(id, ts, stacktrace,
 * log_snapshot, sent, upload_id)}.
 */
public final class LogDb extends SQLiteOpenHelper implements LogRecordStore {

    private static final String DB_NAME = "car2hass_logs.db";
    private static final int DB_VERSION = 1;

    private static final String T_LOG = "log_records";
    private static final String T_CRASH = "crash_records";

    public LogDb(Context ctx) {
        super(ctx, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + T_LOG + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "ts INTEGER NOT NULL,"
                + "level TEXT,"
                + "tag TEXT,"
                + "msg TEXT,"
                + "sent INTEGER DEFAULT 0,"
                + "upload_id TEXT,"
                + "source TEXT)");
        db.execSQL("CREATE INDEX idx_log_sent_ts ON " + T_LOG + " (sent, ts)");
        db.execSQL("CREATE TABLE " + T_CRASH + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "ts INTEGER NOT NULL,"
                + "stacktrace TEXT,"
                + "log_snapshot TEXT,"
                + "sent INTEGER DEFAULT 0,"
                + "upload_id TEXT)");
        db.execSQL("CREATE INDEX idx_crash_sent_ts ON " + T_CRASH + " (sent, ts)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + T_LOG);
        db.execSQL("DROP TABLE IF EXISTS " + T_CRASH);
        onCreate(db);
    }

    @Override
    public long insertLog(long ts, String level, String tag, String msg, String source) {
        ContentValues cv = new ContentValues();
        cv.put("ts", ts);
        cv.put("level", level);
        cv.put("tag", tag);
        cv.put("msg", msg);
        cv.put("sent", LogFlags.UNSENT);
        cv.put("source", source);
        return getWritableDatabase().insert(T_LOG, null, cv);
    }

    @Override
    public long insertCrash(long ts, String stacktrace, String logSnapshot) {
        ContentValues cv = new ContentValues();
        cv.put("ts", ts);
        cv.put("stacktrace", stacktrace);
        cv.put("log_snapshot", logSnapshot);
        cv.put("sent", LogFlags.UNSENT);
        return getWritableDatabase().insert(T_CRASH, null, cv);
    }

    @Override
    public List<LogRecord> unsentLogs(int limit) {
        List<LogRecord> out = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query(T_LOG,
                new String[]{"id", "ts", "level", "tag", "msg", "source"},
                "sent=?", new String[]{String.valueOf(LogFlags.UNSENT)},
                null, null, "ts ASC", String.valueOf(limit))) {
            while (c.moveToNext()) {
                out.add(new LogRecord(c.getLong(0), c.getLong(1), c.getString(2),
                        c.getString(3), c.getString(4), c.getString(5)));
            }
        }
        return out;
    }

    @Override
    public List<CrashRecord> unsentCrashes(int limit) {
        List<CrashRecord> out = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query(T_CRASH,
                new String[]{"id", "ts", "stacktrace", "log_snapshot"},
                "sent=?", new String[]{String.valueOf(LogFlags.UNSENT)},
                null, null, "ts ASC", String.valueOf(limit))) {
            while (c.moveToNext()) {
                out.add(new CrashRecord(c.getLong(0), c.getLong(1), c.getString(2), c.getString(3)));
            }
        }
        return out;
    }

    @Override
    public void markSendingLogs(List<Long> ids, String uploadId) {
        setFlag(T_LOG, ids, LogFlags.SENDING, uploadId);
    }

    @Override
    public void markSendingCrashes(List<Long> ids, String uploadId) {
        setFlag(T_CRASH, ids, LogFlags.SENDING, uploadId);
    }

    @Override
    public void markSentLogs(List<Long> ids) {
        deleteRows(T_LOG, ids);
    }

    @Override
    public void markSentCrashes(List<Long> ids) {
        deleteRows(T_CRASH, ids);
    }

    @Override
    public void resetLogs(List<Long> ids) {
        setFlag(T_LOG, ids, LogFlags.UNSENT, null);
    }

    @Override
    public void resetCrashes(List<Long> ids) {
        setFlag(T_CRASH, ids, LogFlags.UNSENT, null);
    }

    @Override
    public int deleteLogsBefore(long ts) {
        return getWritableDatabase().delete(T_LOG, "ts<?", new String[]{String.valueOf(ts)});
    }

    @Override
    public void purgeAllLogs() {
        getWritableDatabase().delete(T_LOG, null, null);
    }

    @Override
    public void purgeAllCrashes() {
        getWritableDatabase().delete(T_CRASH, null, null);
    }

    @Override
    public long countLogs() {
        return count(T_LOG);
    }

    @Override
    public long countCrashes() {
        return count(T_CRASH);
    }

    private void setFlag(String table, List<Long> ids, int flag, String uploadId) {
        if (ids == null || ids.isEmpty()) return;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (Long id : ids) {
                ContentValues cv = new ContentValues();
                cv.put("sent", flag);
                if (uploadId == null) cv.putNull("upload_id");
                else cv.put("upload_id", uploadId);
                db.update(table, cv, "id=?", new String[]{String.valueOf(id)});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private void deleteRows(String table, List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (Long id : ids) {
                db.delete(table, "id=?", new String[]{String.valueOf(id)});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private long count(String table) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + table, null)) {
            return c.moveToFirst() ? c.getLong(0) : 0;
        }
    }
}
