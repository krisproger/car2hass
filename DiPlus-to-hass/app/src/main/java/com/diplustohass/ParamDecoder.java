package com.diplustohass;

/**
 * Decodes raw autoservice int values into typed signal values.
 *
 * <p>Port of BYDMate {@code ParamDecoder.kt}. A raw int is first cleaned by
 * {@link SentinelDecoder}; sentinels never leak out of the decoders.
 */
public final class ParamDecoder {

    /** Raw int: value as-is (no conversion). */
    public static final int INT_RAW = 0;
    /** Raw int divided by 10.0 (float-producing decoder). */
    public static final int INT_DIV10 = 1;
    /** Raw int multiplied by a per-fid scale (e.g. mileage ×0.1, cell voltage ×0.001). */
    public static final int INT_SCALED = 2;
    /** Raw int as percent, valid range 0..100. */
    public static final int INT_PERCENT = 3;
    /** Raw int mapped to an enum label (value as-is). */
    public static final int INT_ENUM = 4;
    /** Raw int as temperature in °C, valid range −50..80. */
    public static final int INT_TEMP_C = 5;
    /** Battery temp fids (dev=1014) encode °C with a −40 CAN offset. */
    public static final int INT_TEMP_C_OFS40 = 6;
    /** Raw int as kPa, value as-is. */
    public static final int INT_KPA = 7;
    /** Raw IEEE-754 bits decoded as a float (voltage). */
    public static final int FLOAT_VOLT = 8;
    /** Raw IEEE-754 bits decoded as a float (percent). */
    public static final int FLOAT_PERCENT = 9;
    /** Raw IEEE-754 bits decoded as a float (kW). */
    public static final int FLOAT_KW = 10;
    /** Raw IEEE-754 bits decoded as a float (kWh). */
    public static final int FLOAT_KWH = 11;

    private ParamDecoder() {}

    public static Integer decodeInt(int rawInt, int decoder) {
        Integer cleaned = SentinelDecoder.decodeInt(rawInt);
        if (cleaned == null) {
            return null;
        }
        switch (decoder) {
            case INT_RAW:
            case INT_ENUM:
            case INT_KPA:
                return cleaned;
            case INT_PERCENT:
                return (cleaned >= 0 && cleaned <= 100) ? cleaned : null;
            case INT_TEMP_C:
                return (cleaned >= -50 && cleaned <= 80) ? cleaned : null;
            case INT_TEMP_C_OFS40:
                return (cleaned - 40 >= -50 && cleaned - 40 <= 80) ? cleaned - 40 : null;
            case INT_DIV10:
                return cleaned;
            default:
                return null;
        }
    }

    public static Double decodeScaled(int rawInt, double scale) {
        Integer cleaned = SentinelDecoder.decodeInt(rawInt);
        if (cleaned == null) {
            return null;
        }
        return cleaned * scale;
    }

    public static Double decodeFloat(int rawInt, int decoder) {
        switch (decoder) {
            case INT_DIV10: {
                Integer v = SentinelDecoder.decodeInt(rawInt);
                if (v == null) {
                    return null;
                }
                return v / 10.0;
            }
            case FLOAT_VOLT:
            case FLOAT_PERCENT:
            case FLOAT_KW:
            case FLOAT_KWH: {
                Float f = SentinelDecoder.parseFloatFromShellInt(rawInt);
                if (f == null) {
                    return null;
                }
                return f.doubleValue();
            }
            default:
                return null;
        }
    }
}
