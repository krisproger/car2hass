package com.car2hass;

import java.util.Locale;

public final class CommandPollClassifier {
    public enum Kind {
        OK, INTEGRATION_NOT_LOADED, UNKNOWN_CAR_NAME,
        ENDPOINT_MISSING, AUTH_ERROR, TRANSIENT, OTHER
    }

    private CommandPollClassifier() {}

    public static Kind classify(int code, String body) {
        String b = body == null ? "" : body.toLowerCase(Locale.ROOT);
        if (code >= 200 && code < 300) return Kind.OK;
        if (code == 500 && b.contains("integration not loaded")) {
            return Kind.INTEGRATION_NOT_LOADED;
        }
        if (code == 404) {
            return b.contains("unknown car_name")
                    ? Kind.UNKNOWN_CAR_NAME : Kind.ENDPOINT_MISSING;
        }
        if (code == 401 || code == 403) return Kind.AUTH_ERROR;
        if (code == 502 || code == 503) return Kind.TRANSIENT;
        return Kind.OTHER;
    }

    public static long retryDelayMs(Kind kind) {
        switch (kind) {
            case INTEGRATION_NOT_LOADED: return 12_000L;
            case UNKNOWN_CAR_NAME: return 30_000L;
            case TRANSIENT: return 30_000L;
            case ENDPOINT_MISSING: return 300_000L;
            case AUTH_ERROR: return 300_000L;
            default: return 10_000L;
        }
    }
}
