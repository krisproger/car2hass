package com.car2hass.vehicle;

/**
 * Outcome of probing one sensor on one channel. The {@link #status} mirrors the
 * spec's per-channel classification (ok / unsupported / sentinel / timeout / error).
 */
public final class ProbeResult {
    public enum Status { OK, UNSUPPORTED, SENTINEL, TIMEOUT, ERROR }

    public final Status status;
    public final String rawValue;

    public ProbeResult(Status status, String rawValue) {
        this.status = status;
        this.rawValue = rawValue;
    }

    public boolean isOk() {
        return status == Status.OK;
    }

    public static ProbeResult fromRaw(String raw, boolean apiUnsupported) {
        if (apiUnsupported) return new ProbeResult(Status.UNSUPPORTED, null);
        if (raw == null) return new ProbeResult(Status.ERROR, null);
        if (raw.equals("65535") || raw.equals("1048575")
                || raw.equals("-10013") || raw.equals("-10011")) {
            return new ProbeResult(Status.SENTINEL, raw);
        }
        return new ProbeResult(Status.OK, raw);
    }

    public static ProbeResult unsupported() {
        return new ProbeResult(Status.UNSUPPORTED, null);
    }

    public static ProbeResult timeout() {
        return new ProbeResult(Status.TIMEOUT, null);
    }

    public static ProbeResult error(String message) {
        return new ProbeResult(Status.ERROR, message);
    }
}
