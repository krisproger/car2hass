package com.diplustohass;

import java.util.List;

/**
 * Plain-Java unit tests for LogDedup (export-log deduplication).
 *
 * <p>No Android runtime dependency: compile LogDedup.java + this class with a
 * standard JDK and run the main method.
 */
public class LogDedupTest {

    public static void main(String[] args) {
        testDuplicateTimestampLinesDropped();
        testSameTextDifferentTimestampsKept();
        testEmptyAndBlankLinesSkipped();
        testOrderPreserved();
        testEmptyInput();
        testCrossSectionDedup();

        System.out.println("All LogDedup tests passed.");
    }

    /** The same timestamp+text line repeated (replay of one event from
     * another section) must appear only once. */
    private static void testDuplicateTimestampLinesDropped() {
        String input = "12:00:01.000 I/Service: connected\n"
                + "12:00:02.000 I/Service: flush ok\n"
                + "12:00:01.000 I/Service: connected\n";
        List<String> out = LogDedup.dedupeLines(input);
        assertEquals(2, out.size(), "duplicate timestamp line must be dropped, got " + out);
        assertEquals("12:00:01.000 I/Service: connected", out.get(0), "first line");
        assertEquals("12:00:02.000 I/Service: flush ok", out.get(1), "second line");
    }

    /** Identical text at different timestamps are legitimate repeats
     * (e.g. periodic "flush ok") and must all be kept. */
    private static void testSameTextDifferentTimestampsKept() {
        String input = "12:00:01.000 I/Service: flush ok\n"
                + "12:00:05.000 I/Service: flush ok\n"
                + "12:00:09.000 I/Service: flush ok\n";
        List<String> out = LogDedup.dedupeLines(input);
        assertEquals(3, out.size(), "same text with different timestamps must be kept");
    }

    private static void testEmptyAndBlankLinesSkipped() {
        String input = "12:00:01.000 I/Service: connected\n"
                + "\n"
                + "   \n"
                + "12:00:02.000 I/Service: done\n";
        List<String> out = LogDedup.dedupeLines(input);
        assertEquals(2, out.size(), "blank lines must be skipped, got " + out);
    }

    private static void testOrderPreserved() {
        String input = "12:00:03.000 I/a: third\n"
                + "12:00:01.000 I/a: first\n"
                + "12:00:02.000 I/a: second\n"
                + "12:00:01.000 I/a: first\n";
        List<String> out = LogDedup.dedupeLines(input);
        assertEquals(3, out.size(), "count");
        assertEquals("12:00:03.000 I/a: third", out.get(0), "order 0");
        assertEquals("12:00:01.000 I/a: first", out.get(1), "order 1");
        assertEquals("12:00:02.000 I/a: second", out.get(2), "order 2");
    }

    private static void testEmptyInput() {
        assertEquals(0, LogDedup.dedupeLines("").size(), "empty string");
        assertEquals(0, LogDedup.dedupeLines(null).size(), "null");
    }

    /** Lines appearing in both the in-memory and persistent sections must be
     * emitted once — dedup state carries across sections of one export. */
    private static void testCrossSectionDedup() {
        String inMemory = "12:00:01.000 I/Service: connected\n"
                + "12:00:02.000 I/Service: flush ok\n";
        String persistent = "12:00:01.000 I/Service: connected\n"
                + "12:00:03.000 I/Service: geofence inside\n";
        List<String> mem = LogDedup.dedupeLines(inMemory);
        List<String> file = LogDedup.dedupeLines(persistent, mem);
        assertEquals(2, mem.size(), "memory section size");
        assertEquals(1, file.size(), "persistent section must drop the line already seen in memory");
        assertEquals("12:00:03.000 I/Service: geofence inside", file.get(0), "only the new line survives");
    }

    private static void assertEquals(int expected, int actual, String what) {
        if (expected != actual) {
            throw new AssertionError(what + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertEquals(String expected, String actual, String what) {
        if (!expected.equals(actual)) {
            throw new AssertionError(what + ": expected '" + expected + "', got '" + actual + "'");
        }
    }
}
