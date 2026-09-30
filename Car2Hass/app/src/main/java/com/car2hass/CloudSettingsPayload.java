package com.car2hass;

import android.content.Context;

import com.car2hass.rules.Rule;
import com.car2hass.rules.RuleRegistry;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Builds and applies the three cloud settings files. */
public final class CloudSettingsPayload {

    public static final String FILE_SETTINGS = "settings.json";
    public static final String FILE_RULES = "rules.json";
    public static final String FILE_GEOFENCES = "geofences.json";

    private CloudSettingsPayload() {}

    public static JSONObject coreSettings(Context ctx, boolean includeSecret) {
        JSONObject cfg = new JSONObject();
        try {
            cfg.put("hass_host", AppConfig.getHassHost(ctx));
            cfg.put("hass_port", AppConfig.getHassPort(ctx));
            if (includeSecret) cfg.put("hass_token", AppConfig.getHassToken(ctx));
            cfg.put("hass_https", AppConfig.isHassHttps(ctx));
            cfg.put("hass_enabled", AppConfig.isHassEnabled(ctx));
            cfg.put("boot_auto_start", AppConfig.isBootAutoStartEnabled(ctx));
            cfg.put("car_control_enabled", AppConfig.isCarControlEnabled(ctx));
            cfg.put("detailed_log_enabled", AppConfig.isDetailedLogEnabled(ctx));
            cfg.put("file_log_mode", AppConfig.getFileLogMode(ctx));
            cfg.put("queue_enabled", AppConfig.isQueueEnabled(ctx));
            cfg.put("queue_max_mb", AppConfig.getQueueMaxMb(ctx));
            cfg.put("queue_max_days", AppConfig.getQueueMaxDays(ctx));
            cfg.put("disabled_signals", new JSONArray(AppConfig.getDisabledSignals(ctx)));
        } catch (Exception e) {
            LogBuffer.e("CloudSettingsPayload", "coreSettings: " + e.getMessage());
        }
        return cfg;
    }

    public static String settingsJson(Context ctx, boolean includeSecret) {
        return coreSettings(ctx, includeSecret).toString();
    }

    public static String rulesJson(Context ctx) {
        String j = AppConfig.getRulesJson(ctx);
        return (j == null || j.isEmpty()) ? "[]" : j;
    }

    public static String geofencesJson(Context ctx) {
        JSONArray arr = new JSONArray();
        for (GeofenceZone z : AppConfig.loadGeofences(ctx)) arr.put(z.toJson());
        return arr.toString();
    }

    /** Applies a downloaded settings.json without touching the device identity. */
    public static void applySettings(Context ctx, String json) {
        try {
            applyCoreSettings(ctx, new JSONObject(json), AppConfig.getCarName(ctx));
        } catch (Exception e) {
            LogBuffer.e("CloudSettingsPayload", "applySettings: " + e.getMessage());
        }
    }

    /** Applies the core keys; `carName` is passed explicitly so import/cloud differ. */
    public static void applyCoreSettings(Context ctx, JSONObject cfg, String carName) {
        String host = cfg.optString("hass_host", AppConfig.getHassHost(ctx));
        int port = cfg.optInt("hass_port", AppConfig.getHassPort(ctx));
        String token = cfg.has("hass_token")
                ? cfg.optString("hass_token", "").trim() : AppConfig.getHassToken(ctx);
        boolean https = cfg.optBoolean("hass_https", AppConfig.isHassHttps(ctx));
        boolean enabled = cfg.optBoolean("hass_enabled", AppConfig.isHassEnabled(ctx));
        boolean bootAutoStart = cfg.optBoolean("boot_auto_start", AppConfig.isBootAutoStartEnabled(ctx));
        boolean carControl = cfg.optBoolean("car_control_enabled", AppConfig.isCarControlEnabled(ctx));
        int fileLogMode = cfg.has("file_log_mode")
                ? cfg.optInt("file_log_mode", AppConfig.FILE_LOG_BASIC)
                : (cfg.optBoolean("detailed_log_enabled", AppConfig.isDetailedLogEnabled(ctx))
                    ? AppConfig.FILE_LOG_DETAILED : AppConfig.FILE_LOG_BASIC);
        boolean detailedLog = fileLogMode == AppConfig.FILE_LOG_DETAILED;
        AppConfig.save(ctx, host, port, token, carName, enabled, https, bootAutoStart, carControl, detailedLog);
        AppConfig.saveFileLogMode(ctx, fileLogMode);
        AppConfig.saveQueueEnabled(ctx, cfg.optBoolean("queue_enabled", AppConfig.isQueueEnabled(ctx)));
        AppConfig.saveQueueMaxMb(ctx, cfg.optInt("queue_max_mb", AppConfig.getQueueMaxMb(ctx)));
        AppConfig.saveQueueMaxDays(ctx, cfg.optInt("queue_max_days", AppConfig.getQueueMaxDays(ctx)));

        Set<String> disabled = new HashSet<>();
        JSONArray arr = cfg.optJSONArray("disabled_signals");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                String s = arr.optString(i, null);
                if (s != null && !s.isEmpty()) disabled.add(s);
            }
        } else {
            // Backward compatibility: convert the legacy enabled-signals list.
            boolean useEnabledFilter = cfg.optBoolean("use_enabled_filter", false);
            JSONArray signalsArr = cfg.optJSONArray("enabled_signals");
            if (useEnabledFilter && signalsArr != null) {
                Set<String> enabledSignals = new HashSet<>();
                for (int i = 0; i < signalsArr.length(); i++) {
                    String s = signalsArr.optString(i, null);
                    if (s != null && !s.isEmpty()) enabledSignals.add(s);
                }
                for (String[] sig : CANDataReader.SIGNAL_REGISTRY) {
                    String k = sig[2];
                    if (!enabledSignals.contains(k)) disabled.add(k);
                }
            } else {
                disabled.addAll(AppConfig.getDisabledSignals(ctx));
            }
        }
        AppConfig.setDisabledSignals(ctx, disabled);
    }

    public static void applyRules(Context ctx, String json) {
        try {
            JSONArray arr = new JSONArray(json);
            List<Rule> rules = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) rules.add(Rule.fromJson(o));
            }
            RuleRegistry.save(ctx, rules);
        } catch (Exception e) {
            LogBuffer.e("CloudSettingsPayload", "applyRules: " + e.getMessage());
        }
    }

    public static void applyGeofences(Context ctx, String json) {
        try {
            JSONArray arr = new JSONArray(json);
            List<GeofenceZone> zones = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) zones.add(GeofenceZone.fromJson(o));
            }
            AppConfig.saveGeofences(ctx, zones);
        } catch (Exception e) {
            LogBuffer.e("CloudSettingsPayload", "applyGeofences: " + e.getMessage());
        }
    }
}
