package com.car2hass;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Builds the deterministic wire payload for a log upload.
 *
 * <p>The uncompressed form is a JSON object:</p>
 * <pre>
 * {
 *   "records": [ {"ts":..,"level":"..","tag":"..","msg":"..","source":".."}, ... ],
 *   "crashes": [ {"ts":..,"stacktrace":"..","log_snapshot":".."}, ... ]
 * }
 * </pre>
 *
 * <p>That object is UTF-8 encoded and gzip-compressed. The compressed bytes are
 * chunked (500 KiB) and base64-encoded by the transport, one HTTP chunk each.</p>
 */
public final class LogPayloadCodec {

    private LogPayloadCodec() {}

    public static LogUploadPayload build(String uploadId, List<LogRecord> logs,
                                         List<CrashRecord> crashes) {
        String json = buildJson(logs, crashes);
        byte[] gz = GzipCodec.compress(json.getBytes(StandardCharsets.UTF_8));
        int count = (logs == null ? 0 : logs.size()) + (crashes == null ? 0 : crashes.size());
        return new LogUploadPayload(uploadId, gz, count);
    }

    public static String buildJson(List<LogRecord> logs, List<CrashRecord> crashes) {
        try {
            JSONObject root = new JSONObject();
            JSONArray records = new JSONArray();
            if (logs != null) {
                for (LogRecord r : logs) {
                    JSONObject o = new JSONObject();
                    o.put("ts", r.ts);
                    o.put("level", r.level);
                    o.put("tag", r.tag);
                    o.put("msg", r.msg);
                    o.put("source", r.source);
                    records.put(o);
                }
            }
            root.put("records", records);
            JSONArray cr = new JSONArray();
            if (crashes != null) {
                for (CrashRecord c : crashes) {
                    JSONObject o = new JSONObject();
                    o.put("ts", c.ts);
                    o.put("stacktrace", c.stacktrace);
                    o.put("log_snapshot", c.logSnapshot);
                    cr.put(o);
                }
            }
            root.put("crashes", cr);
            return root.toString();
        } catch (JSONException e) {
            throw new IllegalStateException("buildJson failed", e);
        }
    }
}
