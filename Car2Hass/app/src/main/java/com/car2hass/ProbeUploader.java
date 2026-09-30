package com.car2hass;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import com.car2hass.vehicle.ReportPrivacyFilter;

/**
 * Sends an anonymous probe report to the project site after a full research
 * run. Strictly opt-in (AppConfig.isProbeUploadEnabled), off by default.
 */
public final class ProbeUploader {
    static final String ENDPOINT = AppApi.PROBE_REPORT;

    private ProbeUploader() {}

    /** Pure payload construction, testable without Android. */
    public static JSONObject buildPayload(String anonId, JSONObject report) throws Exception {
        JSONObject body = new JSONObject();
        body.put("device_anon_id", nz(anonId));
        if (report != null) body.put("report", ReportPrivacyFilter.sanitize(report));
        return body;
    }

    /** Keep the boolean API for existing callers. */
    public static boolean upload(Context ctx, String reportPath) {
        return uploadResult(ctx, reportPath).ok;
    }

    /** Outcome of an upload attempt: success flag + HTTP status (0 = transport error). */
    public static final class Result {
        public final boolean ok;
        public final int code;
        public Result(boolean ok, int code) { this.ok = ok; this.code = code; }
    }

    /**
     * Reads the stored report and uploads it; returns the outcome and HTTP code
     * so callers can defer on 429. Callers must gate on AppConfig.isProbeUploadEnabled
     * (kept out of this class so the pure parts stay harness-testable).
     */
    public static Result uploadResult(Context ctx, String reportPath) {
        if (ctx == null || reportPath == null) return new Result(false, 0);
        try {
            JSONObject report = new JSONObject(readFile(new File(reportPath)));
            String anonId = com.car2hass.vehicle.DeviceAnon.fromContext(ctx);
            int code = postForCode(ENDPOINT, buildPayload(anonId, report).toString());
            return new Result(code >= 200 && code < 300, code);
        } catch (Exception e) {
            LogBuffer.e("ProbeUploader", "uploadResult: " + e.getMessage());
            return new Result(false, 0);
        }
    }

    static boolean post(String url, String jsonBody) {
        int code = postForCode(url, jsonBody);
        return code >= 200 && code < 300;
    }

    /** POSTs and returns the HTTP status (0 on transport/private-host failure). */
    static int postForCode(String url, String jsonBody) {
        HttpURLConnection conn = null;
        try {
            URL u = new URL(url);
            if (NetSafety.isPrivateHost(u.getHost())) return 0;
            conn = (HttpURLConnection) u.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("X-Cartelemetry-Token", AppApi.TOKEN);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                LogBuffer.w("ProbeUploader", "HTTP " + code + " body=" + readBody(conn));
            }
            return code;
        } catch (Exception e) {
            LogBuffer.e("ProbeUploader", "post: " + e.getMessage()
                    + " body=" + (conn == null ? "" : readBody(conn)));
            return 0;
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
            return truncate(sb.toString());
        } catch (Exception e) {
            return "";
        }
    }

    /** Collapses whitespace and caps the string at ~300 chars. */
    static String truncate(String s) {
        if (s == null) return "";
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() > 300 ? t.substring(0, 300) + "..." : t;
    }

    private static String readFile(File f) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (FileInputStream fis = new FileInputStream(f)) {
            BufferedReader br = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8));
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
