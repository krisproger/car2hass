package com.car2hass;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Uploads the full app log (with the user's optional vehicle/conditions note)
 * to the developer server for analysis. Token-gated like the probe intake.
 */
public final class LogUploader {

    private LogUploader() {}

    /** Sends the log; returns true on HTTP 200. */
    public static boolean upload(Context ctx, String message, String logText) {
        try {
            String anonId = com.car2hass.vehicle.DeviceAnon.fromContext(ctx);
            JSONObject body = new JSONObject();
            body.put("device_anon_id", anonId == null ? "" : anonId);
            body.put("app_version", AppInfo.getVersionString(ctx));
            body.put("message", message == null ? "" : message);
            body.put("log", logText == null ? "" : logText);
            return post(body.toString());
        } catch (Exception e) {
            LogBuffer.e("LogUploader", "upload: " + e.getMessage());
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
            if (NetSafety.isPrivateHost(u.getHost())) return false;
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
            if (code != 200) {
                LogBuffer.w("LogUploader", "HTTP " + code);
                return false;
            }
            return true;
        } catch (Exception e) {
            LogBuffer.e("LogUploader", "post: " + e.getMessage());
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
