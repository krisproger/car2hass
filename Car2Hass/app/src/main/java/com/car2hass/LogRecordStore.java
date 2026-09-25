package com.car2hass;

import java.util.List;

/**
 * Android-free contract for the on-device log/crash store. The production
 * implementation is backed by SQLite; tests provide an in-memory store so the
 * batching, flag lifecycle and retention logic stay pure-JVM testable.
 *
 * <p>Sent-flag lifecycle: records are created {@link LogFlags#UNSENT}, claimed
 * with {@link LogFlags#SENDING} when a batch upload starts, and finally either
 * deleted ({@link LogFlags#SENT}) on server confirmation or reset back to
 * {@link LogFlags#UNSENT} on failure. Only {@code UNSENT} records are ever
 * picked for upload, so parallel/duplicate sends cannot re-send an in-flight or
 * confirmed record.
 */
public interface LogRecordStore {

    long insertLog(long ts, String level, String tag, String msg, String source);

    long insertCrash(long ts, String stacktrace, String logSnapshot);

    List<LogRecord> unsentLogs(int limit);

    List<CrashRecord> unsentCrashes(int limit);

    void markSendingLogs(List<Long> ids, String uploadId);

    void markSendingCrashes(List<Long> ids, String uploadId);

    /** Confirms delivery: sets {@code sent=2} and deletes the rows. */
    void markSentLogs(List<Long> ids);

    /** Confirms delivery: sets {@code sent=2} and deletes the rows. */
    void markSentCrashes(List<Long> ids);

    /** Failed send: resets {@code sent=1} back to {@code sent=0} for retry. */
    void resetLogs(List<Long> ids);

    /** Failed send: resets {@code sent=1} back to {@code sent=0} for retry. */
    void resetCrashes(List<Long> ids);

    int deleteLogsBefore(long ts);

    void purgeAllLogs();

    void purgeAllCrashes();

    long countLogs();

    long countCrashes();
}
