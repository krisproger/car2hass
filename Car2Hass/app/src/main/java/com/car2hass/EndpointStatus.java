package com.car2hass;

/** Live connection state of one Home Assistant endpoint. */
public final class EndpointStatus {
    public final String endpointId;
    public final boolean enabled;
    public final boolean workerRunning;
    public final long lastAttemptMs;
    public final long lastSuccessMs;
    public final String lastError;
    public final long backoffMs;
    public final int queueCount;
    public final int bufferCount;
    public final String integrationVersion;
    public final boolean integrationLoaded;

    public EndpointStatus(String endpointId, boolean enabled, boolean workerRunning,
                          long lastAttemptMs, long lastSuccessMs, String lastError,
                          long backoffMs, int queueCount, int bufferCount,
                          String integrationVersion, boolean integrationLoaded) {
        this.endpointId = endpointId;
        this.enabled = enabled;
        this.workerRunning = workerRunning;
        this.lastAttemptMs = lastAttemptMs;
        this.lastSuccessMs = lastSuccessMs;
        this.lastError = lastError == null ? "" : lastError;
        this.backoffMs = Math.max(0L, backoffMs);
        this.queueCount = Math.max(0, queueCount);
        this.bufferCount = Math.max(0, bufferCount);
        this.integrationVersion = integrationVersion == null ? "" : integrationVersion;
        this.integrationLoaded = integrationLoaded;
    }

    public static EndpointStatus disabled(String endpointId) {
        return new EndpointStatus(endpointId, false, false, 0, 0, "", 0, 0, 0, "", false);
    }

    public boolean connected() {
        return lastSuccessMs > 0 && lastError.isEmpty() && workerRunning;
    }

    public boolean failed() {
        return !lastError.isEmpty();
    }
}
