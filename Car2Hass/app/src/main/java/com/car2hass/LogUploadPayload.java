package com.car2hass;

/** A compressed, ready-to-chunk upload payload for one flush. */
public final class LogUploadPayload {
    public final String uploadId;
    public final byte[] gzipBytes;
    public final int recordCount;

    public LogUploadPayload(String uploadId, byte[] gzipBytes, int recordCount) {
        this.uploadId = uploadId;
        this.gzipBytes = gzipBytes;
        this.recordCount = recordCount;
    }
}
