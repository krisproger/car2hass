package com.car2hass;

import java.util.List;

/**
 * Tests for the pure in-memory core of SensorValueHistory.
 * org.json-based persistence is NOT exercised here (android.jar stubs throw).
 */
public class SensorValueHistoryTest {

    public static void main(String[] args) {
        SensorValueHistory.clearAll();

        testRecordAndRetrieve();
        testDedupKeepsSingleCopy();
        testReRecordMovesToFront();
        testCapEvictsOldest();
        testCapKeepsNewestOnReRecord();
        testIgnoreNullEmptyDashes();
        testMultipleKeysIndependent();
        testClearAll();

        System.out.println("All SensorValueHistory tests passed.");
    }

    private static void testRecordAndRetrieve() {
        SensorValueHistory.clearAll();
        assertTrue(SensorValueHistory.recordValue("gear", "P"), "record P");
        assertTrue(SensorValueHistory.recordValue("gear", "D"), "record D");
        List<String> values = SensorValueHistory.getValues("gear");
        assertEquals(2, values.size(), "gear size");
        assertEquals("D", values.get(0), "newest first");
        assertEquals("P", values.get(1), "oldest last");
    }

    private static void testDedupKeepsSingleCopy() {
        SensorValueHistory.clearAll();
        SensorValueHistory.recordValue("gear", "P");
        SensorValueHistory.recordValue("gear", "D");
        assertTrue(SensorValueHistory.recordValue("gear", "P"), "re-record P is a change (reorder)");
        List<String> values = SensorValueHistory.getValues("gear");
        assertEquals(2, values.size(), "still 2 values after dedup");
    }

    private static void testReRecordMovesToFront() {
        SensorValueHistory.clearAll();
        SensorValueHistory.recordValue("gear", "P");
        SensorValueHistory.recordValue("gear", "D");
        SensorValueHistory.recordValue("gear", "P");
        List<String> values = SensorValueHistory.getValues("gear");
        assertEquals("P", values.get(0), "re-recorded value moves to front");
        assertEquals("D", values.get(1), "other value pushed back");

        // Recording the already-newest value again is a no-op.
        assertFalse(SensorValueHistory.recordValue("gear", "P"), "re-record newest is no-op");
    }

    private static void testCapEvictsOldest() {
        SensorValueHistory.clearAll();
        int cap = SensorValueHistory.MAX_VALUES_PER_KEY;
        for (int i = 0; i < cap + 5; i++) {
            SensorValueHistory.recordValue("speed", String.valueOf(i));
        }
        List<String> values = SensorValueHistory.getValues("speed");
        assertEquals(cap, values.size(), "capped at MAX_VALUES_PER_KEY");
        assertEquals("5", values.get(0), "numeric sort: smallest kept");
        assertEquals(String.valueOf(cap + 4), values.get(values.size() - 1), "numeric sort: largest kept");
    }

    private static void testCapKeepsNewestOnReRecord() {
        SensorValueHistory.clearAll();
        int cap = SensorValueHistory.MAX_VALUES_PER_KEY;
        for (int i = 0; i < cap; i++) {
            SensorValueHistory.recordValue("speed", String.valueOf(i));
        }
        // Re-record the oldest ("0") so it becomes newest, then add a new value.
        SensorValueHistory.recordValue("speed", "0");
        SensorValueHistory.recordValue("speed", "999");
        List<String> values = SensorValueHistory.getValues("speed");
        assertEquals(cap, values.size(), "still capped");
        assertEquals("0", values.get(0), "numeric sort: 0 smallest");
        assertEquals("2", values.get(1), "numeric sort: 2 next (1 evicted)");
        assertTrue(values.contains("999"), "999 present");
        assertFalse(values.contains("1"), "1 was evicted as oldest (before refresh)");
    }

    private static void testIgnoreNullEmptyDashes() {
        SensorValueHistory.clearAll();
        assertFalse(SensorValueHistory.recordValue(null, "x"), "null key");
        assertFalse(SensorValueHistory.recordValue("", "x"), "empty key");
        assertFalse(SensorValueHistory.recordValue("gear", null), "null value");
        assertFalse(SensorValueHistory.recordValue("gear", ""), "empty value");
        assertFalse(SensorValueHistory.recordValue("gear", "   "), "blank value");
        assertFalse(SensorValueHistory.recordValue("gear", "---"), "placeholder value");
        assertEquals(0, SensorValueHistory.keyCount(), "nothing stored");
    }

    private static void testMultipleKeysIndependent() {
        SensorValueHistory.clearAll();
        SensorValueHistory.recordValue("gear", "P");
        SensorValueHistory.recordValue("drive_mode", "ECO");
        assertEquals(2, SensorValueHistory.keyCount(), "two keys");
        assertEquals(1, SensorValueHistory.getValues("gear").size(), "gear one value");
        assertEquals(1, SensorValueHistory.getValues("drive_mode").size(), "drive_mode one value");
        assertEquals(0, SensorValueHistory.getValues("unknown").size(), "unknown key empty");
    }

    private static void testClearAll() {
        SensorValueHistory.clearAll();
        SensorValueHistory.recordValue("gear", "P");
        SensorValueHistory.clearAll();
        assertEquals(0, SensorValueHistory.keyCount(), "empty after clearAll");
        assertFalse(SensorValueHistory.isDirty(), "not dirty after clearAll");
    }

    private static void assertTrue(boolean v, String msg) {
        if (!v) throw new AssertionError(msg + " expected=true actual=false");
    }

    private static void assertFalse(boolean v, String msg) {
        if (v) throw new AssertionError(msg + " expected=false actual=true");
    }

    private static void assertEquals(Object expected, Object actual, String msg) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(msg + " expected=" + expected + " actual=" + actual);
        }
    }
}
