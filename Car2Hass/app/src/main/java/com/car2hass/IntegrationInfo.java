package com.car2hass;

import android.content.Context;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Version/capability handshake with the CARTelemetry HA integration.
 *
 * <p>The app queries {@code GET /api/cartelemetry/info} and compares the
 * reported {@code api_version} against {@link #MIN_API_VERSION}. This replaces
 * the fragile HTTP-code heuristic (404/405/500) with an explicit check, so the
 * app can tell the user precisely why the integration must be updated.</p>
 */
public class IntegrationInfo {

    /** Minimum wire API version this app build is compatible with. */
    public static final int MIN_API_VERSION = 3;

    /** Wire API version at which the integration accepts delta snapshots. */
    public static final int DELTA_API_VERSION = 4;

    /** Last api_version successfully reported by the integration (0 = unknown). */
    private static volatile int reportedApiVersion = 0;

    /** Loads the last persisted api_version (call once at app/telemetry start). */
    public static void load(Context ctx) {
        reportedApiVersion = AppConfig.getIntegrationApiVersion(ctx);
    }

    /** True when the integration supports delta snapshots (api_version >= 4). */
    public static boolean supportsDelta() {
        return reportedApiVersion >= DELTA_API_VERSION;
    }

    public final String integrationVersion;
    public final int apiVersion;
    public final boolean commandsSupported;

    IntegrationInfo(String integrationVersion, int apiVersion, boolean commandsSupported) {
        this.integrationVersion = integrationVersion;
        this.apiVersion = apiVersion;
        this.commandsSupported = commandsSupported;
    }

    /** True when the reported api_version satisfies {@link #MIN_API_VERSION}. */
    public boolean isCompatible() {
        return apiVersion >= MIN_API_VERSION;
    }

    /**
     * Query {@code {baseUrl}/api/cartelemetry/info}.
     *
     * @return parsed info, or {@code null} when the endpoint is unavailable
     *         (integration predates it) or the response is malformed.
     */
    public static IntegrationInfo query(String baseUrl, String token) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(baseUrl + "/api/cartelemetry/info");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int code = conn.getResponseCode();
            InputStream is = code < 400 ? conn.getInputStream() : conn.getErrorStream();
            if (code < 200 || code >= 300 || is == null) {
                return null;
            }
            BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            br.close();

            JSONObject obj = new JSONObject(sb.toString());
            String version = obj.optString("integration_version", "");
            int api = obj.optInt("api_version", -1);
            if (api >= 0) {
                reportedApiVersion = api;
            }
            JSONObject caps = obj.optJSONObject("capabilities");
            boolean commands = caps == null || caps.optBoolean("commands", true);
            return new IntegrationInfo(version, api, commands);
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /** Like {@link #query(String, String)} but persists the reported api_version. */
    public static IntegrationInfo query(Context ctx, String baseUrl, String token) {
        IntegrationInfo info = query(baseUrl, token);
        if (info != null && info.apiVersion >= 0) {
            AppConfig.setIntegrationApiVersion(ctx, info.apiVersion);
        }
        return info;
    }
}
