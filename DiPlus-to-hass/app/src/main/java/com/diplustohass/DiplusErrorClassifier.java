package com.diplustohass;

/**
 * Distinguishes "DiPlus is unavailable" from "signal genuinely unsupported".
 * A connect error (DiPlus down, timeout, DNS) must never be cached as
 * unsupported — it is transient. Only an HTTP 200 with {"success":false}
 * means the name is unknown to this firmware.
 */
public final class DiplusErrorClassifier {
    private DiplusErrorClassifier() {}

    public static boolean isConnectError(String message) {
        if (message == null) return false;
        String m = message.toLowerCase();
        return m.contains("failed to connect")
                || m.contains("connection refused")
                || m.contains("timeout")
                || m.contains("timed out")
                || m.contains("unknownhost")
                || m.contains("unable to resolve");
    }

    public static boolean isUnsupportedResponse(int httpCode, String body) {
        return httpCode == 200 && body != null && body.contains("\"success\":false");
    }

    public static boolean isNonOkHttp(int httpCode) {
        return httpCode != 200;
    }
}