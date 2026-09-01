package com.car2hass;

/**
 * Translates raw autoservice Binder return values to typed Java nullables.
 *
 * <p>Sentinels observed on Leopard 3 (see feedback_autoservice_validated.md):
 * <ul>
 *   <li>0x0000FFFF (65535) = DEVICE_THE_FEATURE_LINK_ERROR (fid↔CAN link not established)</li>
 *   <li>0x000FFFFF (1048575) = 20-bit "not initialized"</li>
 *   <li>0xFFFFD8E3 (-10013) = wrong transact code</li>
 *   <li>0xFFFFD8E5 (-10011) = fid not writable / wrong direction</li>
 *   <li>0xBF800000 = -1.0f = float "not initialized"</li>
 * </ul>
 *
 * <p>Transact 7 returns a 4-byte IEEE 754 float (not double): parse via
 * {@link Float#intBitsToFloat(int)} — see {@link #parseFloatFromShellInt(int)}.
 */
public final class SentinelDecoder {

    /** Public: callers that must tell "fid absent on this generation" from other
     * sentinels (window generation fallback / probe) compare against this value. */
    public static final int FEATURE_LINK_ERROR = 0x0000FFFF;
    private static final int NOT_INITIALIZED_20BIT = 0x000FFFFF;
    private static final int WRONG_TRANSACT = -10013;
    private static final int WRONG_DIRECTION = -10011;

    private SentinelDecoder() {}

    public static Integer decodeInt(int raw) {
        switch (raw) {
            case FEATURE_LINK_ERROR:
            case NOT_INITIALIZED_20BIT:
            case WRONG_TRANSACT:
            case WRONG_DIRECTION:
                return null;
            default:
                return raw;
        }
    }

    public static Float decodeFloat(float raw) {
        if (Float.isNaN(raw) || Float.isInfinite(raw) || raw == -1.0f) {
            return null;
        }
        return raw;
    }

    /**
     * {@code service call autoservice 7 i32 <dev> i32 <fid>} returns a 32-bit
     * value encoded as a hex int by the shell wrapper. The bytes are the IEEE
     * 754 representation of a float.
     */
    public static Float parseFloatFromShellInt(int rawBits) {
        return decodeFloat(Float.intBitsToFloat(rawBits));
    }
}
