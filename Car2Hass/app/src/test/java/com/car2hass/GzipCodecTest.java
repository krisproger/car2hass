package com.car2hass;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Plain-Java tests for the gzip codec and the compressed upload payload. */
public class GzipCodecTest {

    public static void main(String[] args) {
        testRoundTrip();
        testPayloadRoundTripDeterministic();
        testEmptyCompress();

        System.out.println("All GzipCodec tests passed.");
    }

    private static void testRoundTrip() {
        String original = repeat("line of log text 0123456789\n", 500);
        byte[] compressed = GzipCodec.compress(original.getBytes(StandardCharsets.UTF_8));
        if (compressed.length >= original.length()) {
            throw new AssertionError("compressed size must be smaller than the repetitive original");
        }
        byte[] back = GzipCodec.decompress(compressed);
        assertEquals(original, new String(back, StandardCharsets.UTF_8), "round-trip mismatch");
    }

    private static void testPayloadRoundTripDeterministic() {
        List<LogRecord> logs = new ArrayList<>();
        logs.add(new LogRecord(1700000000000L, "I", "Service", "connected", "app"));
        logs.add(new LogRecord(1700000001000L, "E", "Worker", "boom", "app"));
        List<CrashRecord> crashes = new ArrayList<>();
        crashes.add(new CrashRecord(1700000002000L, "java.lang.Error", "snapshot line"));

        LogUploadPayload p1 = LogPayloadCodec.build("u1", logs, crashes);
        LogUploadPayload p2 = LogPayloadCodec.build("u1", logs, crashes);
        assertEquals(p1.recordCount, p2.recordCount, "record count deterministic");
        assertEquals(new String(p1.gzipBytes, StandardCharsets.ISO_8859_1),
                new String(p2.gzipBytes, StandardCharsets.ISO_8859_1),
                "gzip output must be deterministic for identical input");

        String json = new String(GzipCodec.decompress(p1.gzipBytes), StandardCharsets.UTF_8);
        if (!json.contains("\"records\"") || !json.contains("\"crashes\"")) {
            throw new AssertionError("payload must contain records and crashes arrays: " + json);
        }
        if (p1.recordCount != 3) throw new AssertionError("record count must be logs+crashes");
    }

    private static void testEmptyCompress() {
        byte[] compressed = GzipCodec.compress(new byte[0]);
        byte[] back = GzipCodec.decompress(compressed);
        if (back.length != 0) throw new AssertionError("empty round-trip must be empty");
    }

    private static String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder(s.length() * n);
        for (int i = 0; i < n; i++) sb.append(s);
        return sb.toString();
    }

    private static void assertEquals(String expected, String actual, String what) {
        if (!expected.equals(actual)) throw new AssertionError(what);
    }

    private static void assertEquals(int expected, int actual, String what) {
        if (expected != actual) throw new AssertionError(what + ": expected " + expected + ", got " + actual);
    }
}
