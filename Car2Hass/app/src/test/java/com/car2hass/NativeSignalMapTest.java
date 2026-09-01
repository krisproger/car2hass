package com.car2hass;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Plain-Java unit tests for NativeSignalMap.
 *
 * <p>No Android runtime dependency: compile NativeSignalMap.java (+
 * SentinelDecoder.java, ParamDecoder.java) and this class, run main.
 */
public class NativeSignalMapTest {

    public static void main(String[] args) {
        testCoverageCount();
        testKeysUnique();
        testTransactAndDecoderInvariants();
        testScaleOnlyForIntScaled();
        testKnownFidEntries();
        testExcludedUnstableFids();
        testFallback();
        testTurnSignalOff();
        testTurnSignalLeft();
        testTurnSignalRight();
        testTurnSignalHazard();
        testTurnSignalSentinel();

        System.out.println("All NativeSignalMap tests passed.");
    }

    /** The map must cover the ~40 signals that have a verified fid card. */
    private static void testCoverageCount() {
        int size = NativeSignalMap.allEntries().size();
        if (size < 40) {
            throw new AssertionError("expected >= 40 entries, got " + size);
        }
    }

    private static void testKeysUnique() {
        Set<String> keys = new HashSet<>();
        for (NativeSignalMap.FidEntry e : NativeSignalMap.allEntries()) {
            if (!keys.add(e.key)) {
                throw new AssertionError("duplicate key: " + e.key);
            }
        }
    }

    /** Only tx 5 (getInt) or 7 (getFloat); decoders must be known to ParamDecoder. */
    private static void testTransactAndDecoderInvariants() {
        for (NativeSignalMap.FidEntry e : NativeSignalMap.allEntries()) {
            if (e.transact != 5 && e.transact != 7) {
                throw new AssertionError("bad transact for " + e.key + ": " + e.transact);
            }
            if (e.transact == 7 && e.decoder != ParamDecoder.FLOAT_VOLT
                    && e.decoder != ParamDecoder.FLOAT_PERCENT
                    && e.decoder != ParamDecoder.FLOAT_KW
                    && e.decoder != ParamDecoder.FLOAT_KWH) {
                throw new AssertionError("tx 7 requires a float decoder, got " + e.decoder + " for " + e.key);
            }
            if (e.transact == 5 && (e.decoder == ParamDecoder.FLOAT_VOLT
                    || e.decoder == ParamDecoder.FLOAT_PERCENT
                    || e.decoder == ParamDecoder.FLOAT_KW
                    || e.decoder == ParamDecoder.FLOAT_KWH)) {
                throw new AssertionError("tx 5 cannot use float decoder for " + e.key);
            }
        }
    }

    /** scale must only be set on INT_SCALED entries. */
    private static void testScaleOnlyForIntScaled() {
        for (NativeSignalMap.FidEntry e : NativeSignalMap.allEntries()) {
            if (e.decoder == ParamDecoder.INT_SCALED && e.scale == 1.0) {
                throw new AssertionError("INT_SCALED entry missing scale: " + e.key);
            }
            if (e.decoder != ParamDecoder.INT_SCALED && e.scale != 1.0) {
                throw new AssertionError("non-scaled entry carries scale: " + e.key);
            }
        }
    }

    private static void testKnownFidEntries() {
        assertEntry("soc", 1014, 1246777400, 7, ParamDecoder.FLOAT_PERCENT);
        assertEntry("range", 1014, 1246765072, 5, ParamDecoder.INT_SCALED);
        assertEntry("speed", 1013, -1807745016, 7, ParamDecoder.FLOAT_KW);
        assertEntry("turn_signal", 1004, 950009900, 5, ParamDecoder.INT_ENUM);
        assertEntry("window_rr", 1001, 947912752, 5, ParamDecoder.INT_PERCENT);
        assertEntry("driver_door_lock", 1032, 1081081864, 5, ParamDecoder.INT_ENUM);
    }

    /** Unstable/non-reading fids were removed from the native map (log analysis 2026-08-17). */
    private static void testExcludedUnstableFids() {
        if (NativeSignalMap.get("passenger_seatbelt") != null) {
            throw new AssertionError("passenger_seatbelt must be excluded from native reading");
        }
        if (NativeSignalMap.get("trunk") != null) {
            throw new AssertionError("trunk must be excluded from native reading");
        }
    }

    private static void testFallback() {
        NativeSignalMap.FidEntry fb = NativeSignalMap.getFallbackFid("window_rr");
        if (fb == null || fb.fid != 1267728408) {
            throw new AssertionError("window_rr fallback must be fid 1267728408, got " + fb);
        }
        if (NativeSignalMap.getFallbackFid("soc") != null) {
            throw new AssertionError("soc must not have a fallback");
        }
    }

    private static void testTurnSignalOff() {
        Map<String, Integer> v = NativeSignalMap.deriveTurnValues(1);
        assertEquals(Integer.valueOf(0), v.get("left_turn"), "off: left");
        assertEquals(Integer.valueOf(0), v.get("right_turn"), "off: right");
        assertEquals(Integer.valueOf(1), v.get("hazard"), "off: hazard");
    }

    private static void testTurnSignalLeft() {
        Map<String, Integer> v = NativeSignalMap.deriveTurnValues(2);
        assertEquals(Integer.valueOf(1), v.get("left_turn"), "left: left");
        assertEquals(Integer.valueOf(0), v.get("right_turn"), "left: right");
        assertEquals(Integer.valueOf(1), v.get("hazard"), "left: hazard off");
    }

    private static void testTurnSignalRight() {
        Map<String, Integer> v = NativeSignalMap.deriveTurnValues(4);
        assertEquals(Integer.valueOf(0), v.get("left_turn"), "right: left");
        assertEquals(Integer.valueOf(1), v.get("right_turn"), "right: right");
        assertEquals(Integer.valueOf(1), v.get("hazard"), "right: hazard off");
    }

    private static void testTurnSignalHazard() {
        Map<String, Integer> v = NativeSignalMap.deriveTurnValues(6);
        assertEquals(Integer.valueOf(1), v.get("left_turn"), "hazard: left");
        assertEquals(Integer.valueOf(1), v.get("right_turn"), "hazard: right");
        assertEquals(Integer.valueOf(2), v.get("hazard"), "hazard: on");
    }

    private static void testTurnSignalSentinel() {
        Map<String, Integer> v = NativeSignalMap.deriveTurnValues(SentinelDecoder.FEATURE_LINK_ERROR);
        if (!v.isEmpty()) {
            throw new AssertionError("sentinel mask must produce empty map, got " + v);
        }
    }

    private static void assertEntry(String key, int dev, int fid, int tx, int decoder) {
        NativeSignalMap.FidEntry e = NativeSignalMap.get(key);
        if (e == null) {
            throw new AssertionError("missing entry: " + key);
        }
        if (e.device != dev || e.fid != fid || e.transact != tx || e.decoder != decoder) {
            throw new AssertionError(key + ": expected dev=" + dev + " fid=" + fid
                    + " tx=" + tx + " decoder=" + decoder
                    + ", got dev=" + e.device + " fid=" + e.fid + " tx=" + e.transact
                    + " decoder=" + e.decoder);
        }
    }

    private static void assertEquals(Object expected, Object actual, String what) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(what + ": expected " + expected + ", got " + actual);
        }
    }
}
