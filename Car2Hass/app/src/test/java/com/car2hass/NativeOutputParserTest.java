package com.car2hass;

import java.util.Map;

/**
 * Plain-Java unit tests for NativeOutputParser.
 *
 * <p>Runs against realistic {@code service call autoservice} output captured on
 * a real head unit (see info/bypass_diplus_telemetry.md).
 */
public class NativeOutputParserTest {

    public static void main(String[] args) {
        testTwoEntries();
        testMarkerBeforeParcelHandshake();
        testMalformedParcelSkipped();
        testUnknownLinesSkipped();
        testEmptyAndNullInput();
        testRepeatedKeys();
        testFloatBitsPreserved();

        System.out.println("All NativeOutputParser tests passed.");
    }

    /** Two entries from the spec: soc (float) then range (int). */
    private static void testTwoEntries() {
        String output = "@soc\n"
                + "Result: Parcel(00000000 3f9df3b6   '....[...')\n"
                + "@range\n"
                + "Result: Parcel(00000000 0000035b   '.....[...')\n";
        Map<String, Long> parsed = NativeOutputParser.parse(output);
        assertEquals(Long.valueOf(0x3f9df3b6L), parsed.get("soc"), "soc raw bits");
        assertEquals(Long.valueOf(0x0000035bL), parsed.get("range"), "range raw 859");
        assertEquals(2, parsed.size(), "two keys parsed");
    }

    /** Marker line before the Parcel (echo first, then service call). */
    private static void testMarkerBeforeParcelHandshake() {
        String output = "@gear\n"
                + "Result: Parcel(00000000 00000001   '.....[...')\n";
        Map<String, Long> parsed = NativeOutputParser.parse(output);
        assertEquals(Long.valueOf(1L), parsed.get("gear"), "gear raw 1");
    }

    /** A Parcel line that fails the regex must leave the signal absent. */
    private static void testMalformedParcelSkipped() {
        String output = "@soc\n"
                + "Result: Parcel(0000000Z 3f9df3b6)\n";
        Map<String, Long> parsed = NativeOutputParser.parse(output);
        if (parsed.containsKey("soc")) {
            throw new AssertionError("malformed hex must be skipped: " + parsed);
        }
    }

    /** Noise lines (shell prompt, errors) between markers must not break parsing. */
    private static void testUnknownLinesSkipped() {
        String output = "shell@dilink:/ $ echo \"@trunk\"; service call autoservice 5 i32 1001 i32 1074790416\n"
                + "@trunk\n"
                + "Result: Parcel(00000000 00000001)\n"
                + "shell@dilink:/ $ \n";
        Map<String, Long> parsed = NativeOutputParser.parse(output);
        assertEquals(Long.valueOf(1L), parsed.get("trunk"), "trunk survives noise");
    }

    private static void testEmptyAndNullInput() {
        if (!NativeOutputParser.parse(null).isEmpty()) {
            throw new AssertionError("null input must give empty map");
        }
        if (!NativeOutputParser.parse("").isEmpty()) {
            throw new AssertionError("empty input must give empty map");
        }
    }

    /** Same key may appear twice in one batch (window fallback); last wins. */
    private static void testRepeatedKeys() {
        String output = "@window_rr\n"
                + "Result: Parcel(00000000 00000032)\n"
                + "@window_rr\n"
                + "Result: Parcel(00000000 00000064)\n";
        Map<String, Long> parsed = NativeOutputParser.parse(output);
        assertEquals(Long.valueOf(0x64L), parsed.get("window_rr"), "last value wins");
    }

    /** tx=7 returns IEEE-754 float bits; they must round-trip as the raw hex. */
    private static void testFloatBitsPreserved() {
        String output = "@soc\n"
                + "Result: Parcel(00000000 42480000   '....[...')\n";
        Map<String, Long> parsed = NativeOutputParser.parse(output);
        Long bits = parsed.get("soc");
        if (bits == null || bits.longValue() != 0x42480000L) {
            throw new AssertionError("float bits must round-trip, got " + bits);
        }
        float f = Float.intBitsToFloat(bits.intValue());
        assertEquals(50.0f, f, "0x42480000 = 50.0f");
    }

    private static void assertEquals(long expected, long actual, String what) {
        if (expected != actual) {
            throw new AssertionError(what + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertEquals(Object expected, Object actual, String what) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(what + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertEquals(int expected, int actual, String what) {
        if (expected != actual) {
            throw new AssertionError(what + ": expected " + expected + ", got " + actual);
        }
    }
}
