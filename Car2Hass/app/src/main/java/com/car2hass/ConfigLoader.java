package com.car2hass;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Loads bundled preset configuration from assets/config.json.
 *
 * <p>This allows pre-building APKs with Home Assistant connection details filled in.
 * Values from assets are applied only once (on first app launch) and only if the
 * corresponding user setting is still empty. After that the user can override them
 * via the in-app settings screen.</p>
 */
public class ConfigLoader {

    private static final String ASSET_NAME = "config.json";
    private static final String PREFS_NAME = "config_loader";
    private static final String KEY_APPLIED = "presets_applied";

    public static void applyBundledPresets(Context context) {
        SharedPreferences loaderPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (loaderPrefs.getBoolean(KEY_APPLIED, false)) {
            return;
        }

        JSONObject cfg = loadAssetConfig(context);
        if (cfg == null) {
            loaderPrefs.edit().putBoolean(KEY_APPLIED, true).apply();
            return;
        }

        // Read bundled values.
        String bundledHost = cfg.optString("hass_host", "").trim();
        int bundledPort = cfg.optInt("hass_port", 8123);
        String bundledToken = cfg.optString("hass_token", "").trim();
        String bundledCarName = cfg.optString("car_name", "").trim();
        boolean bundledHttps = cfg.optBoolean("hass_https", false);
        boolean bundledEnabled = cfg.optBoolean("hass_enabled", false);

        // Read current values.
        String currentHost = AppConfig.getHassHost(context);
        String currentToken = AppConfig.getHassToken(context);
        String currentCarName = AppConfig.getCarName(context);
        int currentPort = AppConfig.getHassPort(context);
        boolean currentHttps = AppConfig.isHassHttps(context);
        boolean currentEnabled = AppConfig.isHassEnabled(context);

        // Apply bundled values only where current values are empty.
        String newHost = currentHost.isEmpty() ? bundledHost : currentHost;
        String newToken = currentToken.isEmpty() ? bundledToken : currentToken;
        String newCarName = currentCarName.isEmpty() ? bundledCarName : currentCarName;
        int newPort = currentPort == 8123 && bundledPort != 8123 ? bundledPort : currentPort;
        boolean newHttps = currentHttps || bundledHttps;
        boolean newEnabled = currentEnabled || bundledEnabled;

        boolean changed = !newHost.equals(currentHost) || !newToken.equals(currentToken)
                || !newCarName.equals(currentCarName) || newPort != currentPort
                || newHttps != currentHttps || newEnabled != currentEnabled;

        if (changed) {
            AppConfig.save(context, newHost, newPort, newToken, newCarName, newEnabled, newHttps,
                    AppConfig.isBootAutoStartEnabled(context));
            LogBuffer.i("ConfigLoader", "Applied bundled HA presets from assets/config.json");
        } else {
            LogBuffer.i("ConfigLoader", "No bundled presets to apply");
        }

        loaderPrefs.edit().putBoolean(KEY_APPLIED, true).apply();
    }

    private static JSONObject loadAssetConfig(Context context) {
        try (InputStream is = context.getAssets().open(ASSET_NAME);
             BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return new JSONObject(sb.toString());
        } catch (Exception e) {
            LogBuffer.d("ConfigLoader", "No assets/config.json or parse error: " + e.getMessage());
            return null;
        }
    }
}
