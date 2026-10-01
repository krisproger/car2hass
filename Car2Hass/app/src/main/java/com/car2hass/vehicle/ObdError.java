package com.car2hass.vehicle;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutionException;

/** Stable OBD failure codes (persisted in AppConfig and shown in the UI). */
public final class ObdError {

    public static final String BT_OFF = "bt_off";
    public static final String BT_NOT_PAIRED = "bt_not_paired";
    public static final String BT_CONNECT_IO = "bt_connect_io";
    public static final String BT_CONNECT_TIMEOUT = "bt_connect_timeout";
    public static final String INIT_FAILED = "init_failed";
    public static final String NO_DATA = "no_data";
    public static final String IO_ERROR = "io_error";
    public static final String UNKNOWN = "unknown";

    /** Unwraps ExecutionException/CompletionException layers to the real cause. */
    public static Throwable unwrap(Throwable t) {
        Throwable c = t;
        while (c != null && c.getCause() != null
                && (c instanceof ExecutionException
                    || c instanceof java.util.concurrent.CompletionException)) {
            c = c.getCause();
        }
        return c != null ? c : t;
    }

    /** Classifies a failure by phase ("open"/"warmUp"/"read"/...) + exception. */
    public static String classify(String phase, Throwable t) {
        Throwable c = unwrap(t);
        if (c instanceof IllegalStateException) return BT_OFF;
        if (c instanceof IllegalArgumentException) return BT_NOT_PAIRED;
        if (c instanceof SocketTimeoutException) return BT_CONNECT_TIMEOUT;
        if (c instanceof IOException) return "open".equals(phase) ? BT_CONNECT_IO : IO_ERROR;
        return UNKNOWN;
    }

    private ObdError() {}
}
