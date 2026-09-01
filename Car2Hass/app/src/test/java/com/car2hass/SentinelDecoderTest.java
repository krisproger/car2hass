package com.car2hass;

/**
 * Plain-Java unit tests for SentinelDecoder.
 *
 * <p>No Android runtime dependency: compile SentinelDecoder.java + this class
 * with a standard JDK and run the main method.
 */
public class SentinelDecoderTest {

    public static void main(String[] args) {
        testIntSentinels();
        testFloatSentinels();
        testParseFloatFromShellInt();

        System.out.println("All SentinelDecoder tests passed.");
    }

    private static void testIntSentinels() {
        assertEquals(null, SentinelDecoder.decodeInt(SentinelDecoder.FEATURE_LINK_ERROR), "feature link error");
        assertEquals(null, SentinelDecoder.decodeInt(1048575), "not initialized 0x000FFFFF");
        assertEquals(null, SentinelDecoder.decodeInt(-10013), "wrong transact code");
        assertEquals(null, SentinelDecoder.decodeInt(-10011), "wrong direction");
        assertEquals(Integer.valueOf(0), SentinelDecoder.decodeInt(0), "0 is valid");
        assertEquals(Integer.valueOf(42), SentinelDecoder.decodeInt(42), "42 is valid");
        assertEquals(Integer.valueOf(100), SentinelDecoder.decodeInt(100), "100 is valid");
    }

    private static void testFloatSentinels() {
        assertEquals(null, SentinelDecoder.decodeFloat(Float.NaN), "NaN sentinel");
        assertEquals(null, SentinelDecoder.decodeFloat(Float.POSITIVE_INFINITY), "+Inf sentinel");
        assertEquals(null, SentinelDecoder.decodeFloat(Float.NEGATIVE_INFINITY), "-Inf sentinel");
        assertEquals(null, SentinelDecoder.decodeFloat(-1.0f), "-1.0f not initialized");
        assertEquals(Float.valueOf(0.0f), SentinelDecoder.decodeFloat(0.0f), "0.0 is valid");
        assertEquals(Float.valueOf(3.5f), SentinelDecoder.decodeFloat(3.5f), "3.5 is valid");
        assertEquals(Float.valueOf(-0.5f), SentinelDecoder.decodeFloat(-0.5f), "-0.5 is valid");
    }

    private static void testParseFloatFromShellInt() {
        assertEquals(Float.valueOf(1.0f), SentinelDecoder.parseFloatFromShellInt(Float.floatToIntBits(1.0f)), "1.0 via int bits");
        assertEquals(Float.valueOf(50.0f), SentinelDecoder.parseFloatFromShellInt(Float.floatToIntBits(50.0f)), "50.0 via int bits");
        assertEquals(Float.valueOf(-1.5f), SentinelDecoder.parseFloatFromShellInt(Float.floatToIntBits(-1.5f)), "-1.5 via int bits");
        assertEquals(null, SentinelDecoder.parseFloatFromShellInt(Float.floatToIntBits(-1.0f)), "-1.0 bits sentinel");
        assertEquals(null, SentinelDecoder.parseFloatFromShellInt(Float.floatToIntBits(Float.NaN)), "NaN bits sentinel");
    }

    private static void assertEquals(Object expected, Object actual, String what) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(what + ": expected " + expected + ", got " + actual);
        }
    }
}
