package com.car2hass;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Plain-Java tests for the flag lifecycle, dedup and retention rules. */
public class LogStoreTest {

    public static void main(String[] args) {
        testFlagLifecycle();
        testDedupOnlyResendsUnsent();
        testRetentionPruneFiveDays();
        testVersionPurge();

        System.out.println("All LogStore tests passed.");
    }

    /** 0 (unsent) -> 1 (sending) -> 2 (sent) -> deleted. */
    private static void testFlagLifecycle() {
        InMemoryLogStore store = new InMemoryLogStore();
        long id = store.insertLog(1000L, "I", "tag", "msg", "app");
        assertEquals(LogFlags.UNSENT, store.sentOfLog(id), "new record must be unsent");

        store.markSendingLogs(java.util.Collections.singletonList(id), "u1");
        assertEquals(LogFlags.SENDING, store.sentOfLog(id), "claimed record must be sending");

        store.markSentLogs(java.util.Collections.singletonList(id));
        if (store.containsLog(id)) throw new AssertionError("sent record must be deleted");
        assertEquals(0L, store.countLogs(), "no logs remain after confirmed send");
    }

    /** Only sent=0 records are claimed for resend; in-flight (1) and sent (2) are skipped. */
    private static void testDedupOnlyResendsUnsent() {
        InMemoryLogStore store = new InMemoryLogStore();
        long a = store.insertLog(1L, "I", "t", "a", "app");
        long b = store.insertLog(2L, "I", "t", "b", "app");
        long c = store.insertLog(3L, "I", "t", "c", "app");

        store.markSendingLogs(java.util.Arrays.asList(a, b), "u1");
        List<LogRecord> unsent = store.unsentLogs(10);
        assertEquals(1, unsent.size(), "only the unclaimed record is unsent");
        assertEquals(c, unsent.get(0).id, "the unclaimed record id");

        store.resetLogs(java.util.Arrays.asList(a, b));
        List<LogRecord> all = store.unsentLogs(10);
        assertEquals(3, all.size(), "failed send resets to unsent for retry");

        store.markSendingLogs(java.util.Arrays.asList(a), "u2");
        store.markSentLogs(java.util.Arrays.asList(a));
        List<LogRecord> after = store.unsentLogs(10);
        assertEquals(2, after.size(), "sent record is deleted, not resent");
    }

    /** Records older than 5 days are pruned; fresh ones survive. */
    private static void testRetentionPruneFiveDays() {
        InMemoryLogStore store = new InMemoryLogStore();
        long now = 1_700_000_000_000L;
        store.insertLog(now, "I", "t", "fresh", "app");
        store.insertLog(now - 6L * 24 * 3600 * 1000, "I", "t", "old", "app");
        assertEquals(2L, store.countLogs(), "two logs before prune");

        LogRetention.apply(store, now, "1.0.0", "1.0.0");
        assertEquals(1L, store.countLogs(), "only the old log must be pruned");
        List<LogRecord> left = store.unsentLogs(10);
        assertEquals("fresh", left.get(0).msg, "the fresh log survives");
    }

    /** A new app version purges every previous record. */
    private static void testVersionPurge() {
        InMemoryLogStore store = new InMemoryLogStore();
        store.insertLog(1L, "I", "t", "old-version-log", "app");
        store.insertCrash(1L, "boom", "snapshot");
        assertEquals(1L, store.countLogs(), "one log before purge");
        assertEquals(1L, store.countCrashes(), "one crash before purge");

        LogRetention.apply(store, 2L, "1.0.0", "2.0.0");
        assertEquals(0L, store.countLogs(), "all logs purged on version change");
        assertEquals(0L, store.countCrashes(), "all crashes purged on version change");
    }

    private static void assertEquals(long expected, long actual, String what) {
        if (expected != actual) throw new AssertionError(what + ": expected " + expected + ", got " + actual);
    }

    private static void assertEquals(String expected, String actual, String what) {
        if (!expected.equals(actual)) throw new AssertionError(what + ": expected '" + expected + "', got '" + actual + "'");
    }

    /** In-memory LogRecordStore for pure-JVM tests. */
    static final class InMemoryLogStore implements LogRecordStore {
        private final List<Object[]> logs = new ArrayList<>();
        private final List<Object[]> crashes = new ArrayList<>();
        private long nextId = 1;

        @Override public long insertLog(long ts, String level, String tag, String msg, String source) {
            logs.add(new Object[]{nextId, ts, level, tag, msg, source, LogFlags.UNSENT, null});
            return nextId++;
        }

        @Override public long insertCrash(long ts, String stacktrace, String logSnapshot) {
            crashes.add(new Object[]{nextId, ts, stacktrace, logSnapshot, LogFlags.UNSENT, null});
            return nextId++;
        }

        @Override public List<LogRecord> unsentLogs(int limit) {
            List<LogRecord> out = new ArrayList<>();
            for (Object[] r : logs) {
                if ((int) r[6] == LogFlags.UNSENT) {
                    out.add(new LogRecord((long) r[0], (long) r[1], (String) r[2], (String) r[3], (String) r[4], (String) r[5]));
                    if (out.size() >= limit) break;
                }
            }
            return out;
        }

        @Override public List<CrashRecord> unsentCrashes(int limit) {
            List<CrashRecord> out = new ArrayList<>();
            for (Object[] r : crashes) {
                if ((int) r[4] == LogFlags.UNSENT) {
                    out.add(new CrashRecord((long) r[0], (long) r[1], (String) r[2], (String) r[3]));
                    if (out.size() >= limit) break;
                }
            }
            return out;
        }

        @Override public void markSendingLogs(List<Long> ids, String uploadId) {
            setLogFlag(ids, LogFlags.SENDING, uploadId);
        }

        @Override public void markSendingCrashes(List<Long> ids, String uploadId) {
            setCrashFlag(ids, LogFlags.SENDING, uploadId);
        }

        @Override public void markSentLogs(List<Long> ids) {
            setLogFlag(ids, LogFlags.SENT, null);
            logs.removeIf(r -> (int) r[6] == LogFlags.SENT);
        }

        @Override public void markSentCrashes(List<Long> ids) {
            setCrashFlag(ids, LogFlags.SENT, null);
            crashes.removeIf(r -> (int) r[4] == LogFlags.SENT);
        }

        @Override public void resetLogs(List<Long> ids) {
            setLogFlag(ids, LogFlags.UNSENT, null);
        }

        @Override public void resetCrashes(List<Long> ids) {
            setCrashFlag(ids, LogFlags.UNSENT, null);
        }

        @Override public int deleteLogsBefore(long ts) {
            int before = logs.size();
            logs.removeIf(r -> (long) r[1] < ts);
            return before - logs.size();
        }

        @Override public void purgeAllLogs() { logs.clear(); }

        @Override public void purgeAllCrashes() { crashes.clear(); }

        @Override public long countLogs() { return logs.size(); }

        @Override public long countCrashes() { return crashes.size(); }

        private void setLogFlag(List<Long> ids, int flag, String uploadId) {
            for (Object[] r : logs) {
                if (ids.contains((long) r[0])) { r[6] = flag; r[7] = uploadId; }
            }
        }

        private void setCrashFlag(List<Long> ids, int flag, String uploadId) {
            for (Object[] r : crashes) {
                if (ids.contains((long) r[0])) { r[4] = flag; r[5] = uploadId; }
            }
        }

        int sentOfLog(long id) {
            for (Object[] r : logs) if ((long) r[0] == id) return (int) r[6];
            return -1;
        }

        boolean containsLog(long id) {
            for (Object[] r : logs) if ((long) r[0] == id) return true;
            return false;
        }
    }
}
