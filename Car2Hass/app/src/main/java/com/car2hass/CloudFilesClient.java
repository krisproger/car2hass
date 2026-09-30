package com.car2hass;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Bearer-authenticated access to the cloud settings files (project
 * filesettings). Reuses the account token from the telemetry cloud login.
 */
public final class CloudFilesClient {

    private static final String TAG = "CloudFilesClient";
    /** Server caps file content at 2 MiB; a get response can exceed it after JSON escaping. */
    private static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;
    private static final long EXPIRY_SKEW_MS = 60000;

    private CloudFilesClient() {}

    public static String deviceFolder(String anonId) {
        return "car2hass/" + anonId;
    }

    public static String filePath(String anonId, String fileName) {
        return deviceFolder(anonId) + "/" + fileName;
    }

    public static boolean join(Context ctx, String project) {
        try {
            JSONObject body = new JSONObject().put("project", project);
            HttpResult r = authorizedPost(ctx, "/api/project_join", body);
            return r != null && r.code == 200 && new JSONObject(r.body).optBoolean("ok", false);
        } catch (Exception e) {
            LogBuffer.e(TAG, "join: " + e.getMessage());
            return false;
        }
    }

    public static JSONArray list(Context ctx) {
        try {
            HttpResult r = authorizedPost(ctx, "/api/files", new JSONObject().put("op", "list"));
            if (r == null || r.code != 200) return null;
            JSONObject o = new JSONObject(r.body);
            return o.optBoolean("ok", false) ? o.optJSONArray("files") : null;
        } catch (Exception e) {
            LogBuffer.e(TAG, "list: " + e.getMessage());
            return null;
        }
    }

    public static JSONArray folders(Context ctx) {
        try {
            HttpResult r = authorizedPost(ctx, "/api/files", new JSONObject().put("op", "folders"));
            if (r == null || r.code != 200) return null;
            JSONObject o = new JSONObject(r.body);
            return o.optBoolean("ok", false) ? o.optJSONArray("folders") : null;
        } catch (Exception e) {
            LogBuffer.e(TAG, "folders: " + e.getMessage());
            return null;
        }
    }

    /** Returns {content:String, updatedAtSec:Long} or null. */
    public static Object[] get(Context ctx, String name) {
        try {
            JSONObject body = new JSONObject().put("op", "get").put("name", name);
            HttpResult r = authorizedPost(ctx, "/api/files", body);
            if (r == null || r.code != 200) return null;
            JSONObject o = new JSONObject(r.body);
            if (!o.optBoolean("ok", false)) return null;
            return new Object[]{o.optString("content", ""), o.optLong("updated_at", 0L)};
        } catch (Exception e) {
            LogBuffer.e(TAG, "get: " + e.getMessage());
            return null;
        }
    }

    /** Uploads one file; returns the server updated_at (seconds) or -1. */
    public static long put(Context ctx, String name, String content) {
        try {
            JSONObject body = new JSONObject().put("op", "put")
                    .put("name", name).put("content", content);
            HttpResult r = authorizedPost(ctx, "/api/files", body);
            if (r == null || r.code != 200) return -1;
            JSONObject o = new JSONObject(r.body);
            return o.optBoolean("ok", false) ? o.optLong("updated_at", -1) : -1;
        } catch (Exception e) {
            LogBuffer.e(TAG, "put: " + e.getMessage());
            return -1;
        }
    }

    private static HttpResult authorizedPost(Context ctx, String path, JSONObject body) throws Exception {
        if (!isNetworkAvailable(ctx)) return null;
        String token = AppConfig.getCloudAccessToken(ctx);
        if (token.isEmpty()) return null;
        if (System.currentTimeMillis() >= AppConfig.getCloudTokenExpiryMs(ctx) - EXPIRY_SKEW_MS) {
            if (!CloudSyncClient.refreshToken(ctx)) return null;
            token = AppConfig.getCloudAccessToken(ctx);
            if (token.isEmpty()) return null;
        }
        HttpResult r = post(ctx, path, token, body);
        if (r.code == 401) {
            if (!CloudSyncClient.refreshToken(ctx)) return r;
            String retry = AppConfig.getCloudAccessToken(ctx);
            if (retry.isEmpty()) return r;
            r = post(ctx, path, retry, body);
        }
        return r;
    }

    private static HttpResult post(Context ctx, String path, String bearer, JSONObject body) throws Exception {
        URL url = new URL(AppConfig.getCloudBaseUrl(ctx) + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + bearer);
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            boolean success = code >= 200 && code < 300;
            return new HttpResult(code, readBody(conn, success));
        } finally {
            conn.disconnect();
        }
    }

    private static String readBody(HttpURLConnection conn, boolean success) {
        try (InputStream is = success ? conn.getInputStream() : conn.getErrorStream()) {
            if (is == null) return "";
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[16384];
            int n;
            while (buf.size() < MAX_RESPONSE_BYTES && (n = is.read(chunk)) > 0) {
                buf.write(chunk, 0, n);
            }
            return new String(buf.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private static boolean isNetworkAvailable(Context ctx) {
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            Network nw = cm.getActiveNetwork();
            if (nw == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(nw);
            return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Exception e) {
            return false;
        }
    }

    private static final class HttpResult {
        final int code;
        final String body;

        HttpResult(int code, String body) {
            this.code = code;
            this.body = body;
        }
    }
}
