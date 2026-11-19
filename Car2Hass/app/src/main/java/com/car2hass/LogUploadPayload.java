package com.car2hass;

/** A compressed, ready-to-chunk upload payload for one flush. */
public final class LogUploadPayload {
    public final String uploadId;
    public final byte[] gzipBytes;
    public final int recordCount;
    /** Optional user note attached to the manual send; empty for auto/crash uploads. */
    public final String message;

    public LogUploadPayload(String uploadId, byte[] gzipBytes, int recordCount) {
        this(uploadId, gzipBytes, recordCount, "");
    }

    public LogUploadPayload(String uploadId, byte[] gzipBytes, int recordCount, String message) {
        this.uploadId = uploadId;
        this.gzipBytes = gzipBytes;
        this.recordCount = recordCount;
        this.message = message == null ? "" : message;
    }
}
