package com.car2hass;

import android.content.Context;
import android.util.Base64;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Uploads the full app log (with the user's optional vehicle/conditions note)
 * to the developer server for analysis. Token-gated like the probe intake.
 */
public final class LogUploader {

    private LogUploader() {}

    /** Sends the log in chunks; returns true when every chunk got HTTP 200. */
    public static boolean upload(Context ctx, String message, String logText, String uploadId) {
        try {
            String anonId = com.car2hass.vehicle.DeviceAnon.fromContext(ctx);
            byte[] logBytes = (logText == null ? "" : logText).getBytes(StandardCharsets.UTF_8);
            final int MAX_CHUNK = 500 * 1024; // 500 KiB per chunk (server cap 4 MiB)
            int total = Math.max(1, (logBytes.length + MAX_CHUNK - 1) / MAX_CHUNK);
            String chunkId = uploadId == null || uploadId.isEmpty()
                    ? "u" + System.currentTimeMillis() : uploadId;
            for (int i = 0; i < total; i++) {
                int from = i * MAX_CHUNK;
                int len = Math.min(MAX_CHUNK, logBytes.length - from);
                String chunk = new String(logBytes, from, len, StandardCharsets.UTF_8);
                JSONObject body = new JSONObject();
                body.put("device_anon_id", anonId == null ? "" : anonId);
                body.put("app_version", AppInfo.getVersionString(ctx));
                body.put("message", message == null ? "" : message);
                body.put("log", chunk);
                body.put("chunk_index", i);
                body.put("chunk_total", total);
                body.put("chunk_upload_id", chunkId);
                if (!post(body.toString())) return false;
            }
            return true;
        } catch (Exception e) {
            LogBuffer.e("LogUploader", "upload: " + e.getMessage());
            return false;
        }
    }

    /**
     * Uploads one compressed batch (see {@link LogPayloadCodec}): the gzip bytes
     * are split into 500 KiB chunks, base64-encoded and posted one envelope per
     * chunk. The server concatenates decoded chunks, gunzips and parses the
     * {@code records}/{@code crashes} JSON object. Returns true when every chunk
     * got HTTP 200.
     */
    public static boolean uploadPayload(Context ctx, LogUploadPayload payload) {
        try {
            String anonId = com.car2hass.vehicle.DeviceAnon.fromContext(ctx);
            byte[] gz = payload.gzipBytes == null ? new byte[0] : payload.gzipBytes;
            final int MAX_CHUNK = 500 * 1024; // 500 KiB per chunk (server cap 4 MiB)
            int total = Math.max(1, (gz.length + MAX_CHUNK - 1) / MAX_CHUNK);
            for (int i = 0; i < total; i++) {
                int from = i * MAX_CHUNK;
                int len = Math.min(MAX_CHUNK, gz.length - from);
                byte[] chunk = Arrays.copyOfRange(gz, from, from + len);
                String b64 = Base64.encodeToString(chunk, Base64.NO_WRAP);
                JSONObject body = new JSONObject();
                body.put("device_anon_id", anonId == null ? "" : anonId);
                body.put("app_version", AppInfo.getVersionString(ctx));
                body.put("message", "");
                body.put("upload_id", payload.uploadId);
                body.put("chunk_index", i);
                body.put("chunk_total", total);
                body.put("compression", "gzip");
                body.put("payload", b64);
                body.put("record_count", payload.recordCount);
                if (!post(body.toString())) return false;
            }
            return true;
        } catch (Exception e) {
            LogBuffer.e("LogUploader", "uploadPayload: " + e.getMessage());
            return false;
        }
    }

    /** Reads the full exported log file as UTF-8 text. */
    public static String readLogFile(String path) {
        try {
            File f = new File(path);
            if (!f.exists() || f.length() == 0) return "";
            StringBuilder sb = new StringBuilder((int) Math.min(f.length(), 4 * 1024 * 1024));
            try (FileInputStream fis = new FileInputStream(f)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = fis.read(buf)) > 0 && sb.length() < 4 * 1024 * 1024) {
                    sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
                }
            }
            return sb.toString();
        } catch (Exception e) {
            LogBuffer.e("LogUploader", "readLogFile: " + e.getMessage());
            return "";
        }
    }

    private static boolean post(String jsonBody) {
        HttpURLConnection conn = null;
        try {
            URL u = new URL(AppApi.LOG_INTAKE);
            if (NetSafety.isPrivateHost(u.getHost())) {
                LogBuffer.e("LogUploader", "post: private host blocked " + u.getHost());
                return false;
            }
            conn = (HttpURLConnection) u.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("X-Cartelemetry-Token", AppApi.TOKEN);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            LogBuffer.i("LogUploader", "POST " + AppApi.LOG_INTAKE + " -> HTTP " + code
                    + " bytes=" + jsonBody.length());
            if (code < 200 || code >= 300) {
                LogBuffer.w("LogUploader", "HTTP " + code + " body=" + readBody(conn));
                return false;
            }
            return true;
        } catch (Exception e) {
            LogBuffer.e("LogUploader", "post: " + e.getMessage()
                    + " body=" + (conn == null ? "" : readBody(conn)));
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** Reads the response body (error stream first), collapsed and truncated. */
    private static String readBody(HttpURLConnection conn) {
        if (conn == null) return "";
        try {
            java.io.InputStream is = conn.getErrorStream();
            if (is == null) is = conn.getInputStream();
            if (is == null) return "";
            StringBuilder sb = new StringBuilder();
            byte[] buf = new byte[512];
            int n;
            while ((n = is.read(buf)) > 0 && sb.length() < 600) {
                sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
            }
            return ProbeUploader.truncate(sb.toString());
        } catch (Exception e) {
            return "";
        }
    }
}
