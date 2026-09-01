package com.car2hass;

/**
 * Plain-Java unit tests for ParamDecoder.
 *
 * <p>No Android runtime dependency: compile ParamDecoder.java (plus
 * SentinelDecoder.java) and this class with a standard JDK, run main.
 */
public class ParamDecoderTest {

    public static void main(String[] args) {
        testIntRaw();
        testIntPercent();
        testIntTempC();
        testIntTempCOfs40();
        testIntScaled();
        testFloatDecoders();
        testIntDiv10();
        testSentinelsNeverLeak();

        System.out.println("All ParamDecoder tests passed.");
    }

    private static void testIntRaw() {
        assertEquals(Integer.valueOf(42), ParamDecoder.decodeInt(42, ParamDecoder.INT_RAW), "INT_RAW passthrough");
        assertEquals(Integer.valueOf(339738656), ParamDecoder.decodeInt(339738656, ParamDecoder.INT_RAW), "INT_RAW power fid");
        assertEquals(null, ParamDecoder.decodeInt(SentinelDecoder.FEATURE_LINK_ERROR, ParamDecoder.INT_RAW), "INT_RAW sentinel");
    }

    private static void testIntPercent() {
        assertEquals(Integer.valueOf(0), ParamDecoder.decodeInt(0, ParamDecoder.INT_PERCENT), "0%");
        assertEquals(Integer.valueOf(100), ParamDecoder.decodeInt(100, ParamDecoder.INT_PERCENT), "100%");
        assertEquals(Integer.valueOf(50), ParamDecoder.decodeInt(50, ParamDecoder.INT_PERCENT), "50%");
        assertEquals(null, ParamDecoder.decodeInt(-1, ParamDecoder.INT_PERCENT), "-1 invalid percent");
        assertEquals(null, ParamDecoder.decodeInt(101, ParamDecoder.INT_PERCENT), "101 invalid percent");
        assertEquals(null, ParamDecoder.decodeInt(SentinelDecoder.FEATURE_LINK_ERROR, ParamDecoder.INT_PERCENT), "sentinel percent");
    }

    private static void testIntTempC() {
        assertEquals(Integer.valueOf(24), ParamDecoder.decodeInt(24, ParamDecoder.INT_TEMP_C), "24°C");
        assertEquals(Integer.valueOf(-50), ParamDecoder.decodeInt(-50, ParamDecoder.INT_TEMP_C), "-50°C boundary");
        assertEquals(Integer.valueOf(80), ParamDecoder.decodeInt(80, ParamDecoder.INT_TEMP_C), "80°C boundary");
        assertEquals(null, ParamDecoder.decodeInt(-51, ParamDecoder.INT_TEMP_C), "below -50 invalid");
        assertEquals(null, ParamDecoder.decodeInt(81, ParamDecoder.INT_TEMP_C), "above 80 invalid");
    }

    private static void testIntTempCOfs40() {
        assertEquals(Integer.valueOf(11), ParamDecoder.decodeInt(51, ParamDecoder.INT_TEMP_C_OFS40), "raw 51 -> 11°C");
        assertEquals(Integer.valueOf(-40), ParamDecoder.decodeInt(0, ParamDecoder.INT_TEMP_C_OFS40), "raw 0 -> -40°C");
        assertEquals(null, ParamDecoder.decodeInt(SentinelDecoder.FEATURE_LINK_ERROR, ParamDecoder.INT_TEMP_C_OFS40), "sentinel temp");
    }

    private static void testIntScaled() {
        assertFloatEquals(18234.5, ParamDecoder.decodeScaled(182345, 0.1), "mileage ×0.1");
        assertFloatEquals(4.185, ParamDecoder.decodeScaled(4185, 0.001), "cell voltage ×0.001");
        assertEquals(null, ParamDecoder.decodeScaled(SentinelDecoder.FEATURE_LINK_ERROR, 0.1), "sentinel scaled");
    }

    private static void testFloatDecoders() {
        assertFloatEquals(50.0, ParamDecoder.decodeFloat(Float.floatToIntBits(50.0f), ParamDecoder.FLOAT_PERCENT), "soc FLOAT_PERCENT 50%");
        assertFloatEquals(0.0, ParamDecoder.decodeFloat(Float.floatToIntBits(0.0f), ParamDecoder.FLOAT_PERCENT), "soc FLOAT_PERCENT 0%");
        assertFloatEquals(11.5, ParamDecoder.decodeFloat(Float.floatToIntBits(11.5f), ParamDecoder.FLOAT_KW), "speed FLOAT_KW 11.5 km/h");
        assertFloatEquals(12.1, ParamDecoder.decodeFloat(Float.floatToIntBits(12.1f), ParamDecoder.FLOAT_VOLT), "voltage12v FLOAT_VOLT 12.1V");
        assertFloatEquals(3.25, ParamDecoder.decodeFloat(Float.floatToIntBits(3.25f), ParamDecoder.FLOAT_KWH), "consumption FLOAT_KWH 3.25");
        assertEquals(null, ParamDecoder.decodeFloat(Float.floatToIntBits(-1.0f), ParamDecoder.FLOAT_PERCENT), "float -1.0 sentinel");
        assertEquals(null, ParamDecoder.decodeFloat(Float.floatToIntBits(Float.NaN), ParamDecoder.FLOAT_KW), "float NaN sentinel");
    }

    private static void testIntDiv10() {
        assertEquals(Double.valueOf(1.0), ParamDecoder.decodeFloat(10, ParamDecoder.INT_DIV10), "INT_DIV10 10 -> 1.0");
        assertEquals(Double.valueOf(-0.5), ParamDecoder.decodeFloat(-5, ParamDecoder.INT_DIV10), "INT_DIV10 -5 -> -0.5");
        assertEquals(null, ParamDecoder.decodeFloat(SentinelDecoder.FEATURE_LINK_ERROR, ParamDecoder.INT_DIV10), "sentinel div10");
    }

    private static void testSentinelsNeverLeak() {
        assertEquals(null, ParamDecoder.decodeInt(1048575, ParamDecoder.INT_RAW), "0x000FFFFF not initialized");
        assertEquals(null, ParamDecoder.decodeInt(-10013, ParamDecoder.INT_RAW), "wrong transact");
        assertEquals(null, ParamDecoder.decodeInt(-10011, ParamDecoder.INT_ENUM), "wrong direction");
        assertEquals(null, ParamDecoder.decodeInt(SentinelDecoder.FEATURE_LINK_ERROR, ParamDecoder.INT_ENUM), "enum link error");
    }

    private static void assertFloatEquals(double expected, Double actual, String what) {
        if (actual == null || Math.abs(expected - actual) > 1e-4) {
            throw new AssertionError(what + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertEquals(Object expected, Object actual, String what) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(what + ": expected " + expected + ", got " + actual);
        }
    }
}
