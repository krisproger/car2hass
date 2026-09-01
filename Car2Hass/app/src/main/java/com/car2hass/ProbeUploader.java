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
        if (report != null) body.put("report", report);
        return body;
    }

    /**
     * Reads the stored report and uploads it; returns true on HTTP 200.
     * Callers must gate on AppConfig.isProbeUploadEnabled (kept out of this
     * class so the pure parts stay harness-testable).
     */
    public static boolean upload(Context ctx, String reportPath) {
        if (ctx == null || reportPath == null) return false;
        try {
            JSONObject report = new JSONObject(readFile(new File(reportPath)));
            String anonId = com.car2hass.vehicle.DeviceAnon.fromContext(ctx);
            return post(ENDPOINT, buildPayload(anonId, report).toString());
        } catch (Exception e) {
            LogBuffer.e("ProbeUploader", "upload: " + e.getMessage());
            return false;
        }
    }

    static boolean post(String url, String jsonBody) {
        HttpURLConnection conn = null;
        try {
            URL u = new URL(url);
            if (NetSafety.isPrivateHost(u.getHost())) return false;
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
            if (code != 200) {
                LogBuffer.w("ProbeUploader", "HTTP " + code);
                return false;
            }
            return true;
        } catch (Exception e) {
            LogBuffer.e("ProbeUploader", "post: " + e.getMessage());
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
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
