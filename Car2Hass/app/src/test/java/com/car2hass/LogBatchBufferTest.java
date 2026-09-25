package com.car2hass;

import java.util.ArrayList;
import java.util.List;

/** Plain-Java test for LogBatchBuffer block flushing (10-20 messages per block). */
public class LogBatchBufferTest {

    public static void main(String[] args) {
        testFlushSplitsIntoTwentyBlocks();
        testSingleMessageBlockAccepted();
        testFlushEmptyIsNoOp();

        System.out.println("All LogBatchBuffer tests passed.");
    }

    private static void testFlushSplitsIntoTwentyBlocks() {
        List<List<LogRecord>> blocks = new ArrayList<>();
        LogBatchBuffer buf = new LogBatchBuffer(blocks::add, 20);
        for (int i = 0; i < 25; i++) buf.add(i, "I", "t", "m" + i, "app");
        assertEquals(25, buf.pendingCount(), "all 25 buffered before flush");
        buf.flush();
        assertEquals(2, blocks.size(), "25 messages flush into 20 + 5 blocks");
        assertEquals(20, blocks.get(0).size(), "first block size");
        assertEquals(5, blocks.get(1).size(), "second (partial) block size");
        assertEquals(0, buf.pendingCount(), "buffer empty after flush");
        for (List<LogRecord> b : blocks) {
            if (b.size() > 20) throw new AssertionError("block exceeds 20 messages");
        }
    }

    private static void testSingleMessageBlockAccepted() {
        List<List<LogRecord>> blocks = new ArrayList<>();
        LogBatchBuffer buf = new LogBatchBuffer(blocks::add, 20);
        buf.add(1L, "E", "t", "only", "app");
        buf.flush();
        assertEquals(1, blocks.size(), "a single-message block is acceptable");
        assertEquals(1, blocks.get(0).size(), "single-message block size");
        assertEquals("only", blocks.get(0).get(0).msg, "message preserved");
    }

    private static void testFlushEmptyIsNoOp() {
        List<List<LogRecord>> blocks = new ArrayList<>();
        LogBatchBuffer buf = new LogBatchBuffer(blocks::add, 20);
        buf.flush();
        assertEquals(0, blocks.size(), "flushing an empty buffer writes nothing");
    }

    private static void assertEquals(int expected, int actual, String what) {
        if (expected != actual) throw new AssertionError(what + ": expected " + expected + ", got " + actual);
    }

    private static void assertEquals(String expected, String actual, String what) {
        if (!expected.equals(actual)) throw new AssertionError(what + ": expected '" + expected + "', got '" + actual + "'");
    }
}
