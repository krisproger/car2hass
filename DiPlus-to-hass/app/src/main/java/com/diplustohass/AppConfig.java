package com.diplustohass;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppConfig {
    static final String PREF_NAME = "hass_config";
    private static final String KEY_HOST = "hass_host";
    private static final String KEY_PORT = "hass_port";
    private static final String KEY_ADB_HOST = "adb_host";
    private static final String KEY_ADB_PORT = "adb_port";
    private static final String KEY_DIPLUS_AUTH = "diplus_auth";
    // Token is stored encrypted via SecureStorage; legacy key kept for migration.
    static final String KEY_TOKEN = "hass_token";
    private static final String KEY_CAR_NAME = "car_name";
    private static final String KEY_ENABLED = "hass_enabled";
    private static final String KEY_HTTPS = "hass_https";
    private static final String KEY_BOOT_AUTO_START = "boot_auto_start";
    private static final String KEY_ENABLED_SIGNALS = "enabled_signals";
    private static final String KEY_USE_ENABLED_FILTER = "use_enabled_filter";
    private static final String KEY_DISABLED_SIGNALS = "disabled_signals";
    private static final String KEY_CAR_CONTROL_ENABLED = "car_control_enabled";
    private static final String KEY_DETAILED_LOG_ENABLED = "detailed_log_enabled";
    private static final String KEY_QUEUE_ENABLED = "queue_enabled";
    private static final String KEY_QUEUE_MAX_MB = "queue_max_mb";
    private static final String KEY_QUEUE_MAX_DAYS = "queue_max_days";
    private static final String KEY_DASHBOARD_TILES = "dashboard_tiles";
    private static final String KEY_RULES_JSON = "rules_json";
    private static final String KEY_SENSOR_VALUE_HISTORY_JSON = "sensor_value_history_json";
    private static final String KEY_REGISTRY_VERSION = "registry_version";
    private static final String KEY_REGISTRY_LAST_CHECK = "registry_last_check";
    private static final String KEY_GEOFENCES = "geofences";
    private static final String CACHED_REGISTRY_FILE = "sensor_command_map.json";
    private static final String CACHED_REGISTRY_META = "sensor_command_map.meta.json";

    private static String cachedCarName = null;
    private static SecureStorage secureStorage = null;

    private static synchronized SecureStorage getSecureStorage(Context ctx) {
        if (secureStorage == null) {
            secureStorage = new SecureStorage(ctx.getApplicationContext());
        }
        return secureStorage;
    }

    public static String getHassHost(Context ctx) {
        return prefs(ctx).getString(KEY_HOST, "");
    }

    public static int getHassPort(Context ctx) {
        try {
            return Integer.parseInt(prefs(ctx).getString(KEY_PORT, "8123"));
        } catch (Exception e) {
            return 8123;
        }
    }

    public static String getAdbHost(Context ctx) {
        String host = prefs(ctx).getString(KEY_ADB_HOST, "127.0.0.1");
        return host != null && !host.isEmpty() ? host : "127.0.0.1";
    }

    public static int getAdbPort(Context ctx) {
        try {
            return Integer.parseInt(prefs(ctx).getString(KEY_ADB_PORT, "5555"));
        } catch (Exception e) {
            return 5555;
        }
    }

    public static String getDiplusAuth(Context ctx) {
        return prefs(ctx).getString(KEY_DIPLUS_AUTH, "");
    }

    public static void setDiplusAuth(Context ctx, String auth) {
        prefs(ctx).edit().putString(KEY_DIPLUS_AUTH, auth == null ? "" : auth.trim()).apply();
    }

    public static String getHassToken(Context ctx) {
        return getSecureStorage(ctx).getToken();
    }

    public static String getCarName(Context ctx) {
        String name = prefs(ctx).getString(KEY_CAR_NAME, "");
        if (name.isEmpty() && cachedCarName != null) {
            return cachedCarName;
        }
        return name;
    }

    public static boolean isHassEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_ENABLED, false);
    }

    public static boolean isHassHttps(Context ctx) {
        return prefs(ctx).getBoolean(KEY_HTTPS, false);
    }

    public static boolean isBootAutoStartEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_BOOT_AUTO_START, true);
    }

    /**
     * Returns true when the user has allowed Home Assistant to send control
     * commands to the vehicle via DiPlus.
     */
    public static boolean isCarControlEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_CAR_CONTROL_ENABLED, false);
    }

    /**
     * Returns true when detailed logging of command processing and server
     * responses is enabled. Default is off to reduce memory use.
     */
    public static boolean isDetailedLogEnabled(Context ctx) {
        return getFileLogMode(ctx) == FILE_LOG_DETAILED;
    }

    /** File logging modes: what reaches the on-disk log continuously. */
    public static final int FILE_LOG_OFF = 0;
    public static final int FILE_LOG_BASIC = 1;
    public static final int FILE_LOG_DETAILED = 2;

    private static final String KEY_FILE_LOG_MODE = "file_log_mode";

    /**
     * File log mode: OFF (file written only on user export), BASIC (I/W/E
     * lines continuously), DETAILED (everything incl. RAW/D lines).
     * Default BASIC. Migrates from the legacy detailed_log_enabled boolean.
     */
    public static int getFileLogMode(Context ctx) {
        if (!prefs(ctx).contains(KEY_FILE_LOG_MODE)) {
            return prefs(ctx).getBoolean(KEY_DETAILED_LOG_ENABLED, false)
                ? FILE_LOG_DETAILED : FILE_LOG_BASIC;
        }
        return prefs(ctx).getInt(KEY_FILE_LOG_MODE, FILE_LOG_BASIC);
    }

    public static void saveFileLogMode(Context ctx, int mode) {
        prefs(ctx).edit().putInt(KEY_FILE_LOG_MODE, mode).apply();
        LogBuffer.setFileLogMode(mode);
    }

    /**
     * Returns true when offline telemetry queue is enabled.
     * When disabled, failed sends are discarded.
     */
    public static boolean isQueueEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_QUEUE_ENABLED, true);
    }

    public static void saveQueueEnabled(Context ctx, boolean enabled) {
        prefs(ctx).edit().putBoolean(KEY_QUEUE_ENABLED, enabled).apply();
    }

    /**
     * Returns the maximum queue size in megabytes.
     * Oldest snapshots are evicted when the queue exceeds this limit.
     */
    public static int getQueueMaxMb(Context ctx) {
        try {
            return Integer.parseInt(prefs(ctx).getString(KEY_QUEUE_MAX_MB, "100"));
        } catch (Exception e) {
            return 100;
        }
    }

    public static void saveQueueMaxMb(Context ctx, int mb) {
        prefs(ctx).edit().putString(KEY_QUEUE_MAX_MB, String.valueOf(mb)).apply();
    }

    /**
     * Returns the maximum age of queued snapshots in days.
     * Snapshots older than this are evicted before enqueueing new ones.
     */
    public static int getQueueMaxDays(Context ctx) {
        try {
            return Integer.parseInt(prefs(ctx).getString(KEY_QUEUE_MAX_DAYS, "7"));
        } catch (Exception e) {
            return 7;
        }
    }

    public static void saveQueueMaxDays(Context ctx, int days) {
        prefs(ctx).edit().putString(KEY_QUEUE_MAX_DAYS, String.valueOf(days)).apply();
    }

    /**
     * Returns the set of signal keys explicitly enabled for HA transmission.
     * When {@link #isUseEnabledFilter(Context)} is false, an empty set means
     * "all signals enabled" (backward compatible default). When the filter is
     * active, only keys contained in this set are transmitted.
     *
     * @deprecated This enabled-list model is replaced by the disabled-signals
     * list in v1.8. Kept only for one-time migration.
     */
    @Deprecated
    public static Set<String> getEnabledSignals(Context ctx) {
        return new HashSet<>(prefs(ctx).getStringSet(KEY_ENABLED_SIGNALS, Collections.emptySet()));
    }

    /**
     * Returns true when the user has explicitly narrowed the signal list.
     * In this mode only {@link #getEnabledSignals(Context)} are sent to HA.
     *
     * @deprecated Replaced by the disabled-signals list in v1.8.
     */
    @Deprecated
    public static boolean isUseEnabledFilter(Context ctx) {
        return prefs(ctx).getBoolean(KEY_USE_ENABLED_FILTER, false);
    }

    /**
     * Returns the set of signal keys explicitly disabled for HA transmission.
     * An empty set means "all signals enabled".
     */
    public static Set<String> getDisabledSignals(Context ctx) {
        String json = prefs(ctx).getString(KEY_DISABLED_SIGNALS, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            Set<String> set = new HashSet<>();
            for (int i = 0; i < arr.length(); i++) set.add(arr.optString(i));
            return set;
        } catch (Exception e) {
            return new HashSet<>();
        }
    }

    /**
     * Persist the full set of disabled signal keys.
     */
    public static void setDisabledSignals(Context ctx, Set<String> disabled) {
        JSONArray arr = new JSONArray();
        for (String k : disabled) arr.put(k);
        prefs(ctx).edit().putString(KEY_DISABLED_SIGNALS, arr.toString()).apply();
    }

    /**
     * Add a single signal key to the disabled set.
     */
    public static void addDisabledSignal(Context ctx, String key) {
        if (key == null || key.isEmpty()) return;
        Set<String> disabled = getDisabledSignals(ctx);
        if (disabled.add(key)) {
            setDisabledSignals(ctx, disabled);
        }
    }

    /**
     * Remove a single signal key from the disabled set.
     */
    public static void removeDisabledSignal(Context ctx, String key) {
        if (key == null || key.isEmpty()) return;
        Set<String> disabled = getDisabledSignals(ctx);
        if (disabled.remove(key)) {
            setDisabledSignals(ctx, disabled);
        }
    }

    /**
     * Returns true when a signal is allowed to be transmitted to HA.
     * Null or empty keys are always disabled.
     */
    public static boolean isSignalEnabled(Context ctx, String key) {
        if (key == null || key.isEmpty()) return false;
        return !getDisabledSignals(ctx).contains(key);
    }

    /**
     * Migrate legacy v1.7 enabled-list filter settings to the v1.8
     * disabled-signals list. Safe to call repeatedly; it is a no-op once the
     * legacy {@code KEY_USE_ENABLED_FILTER} preference is absent.
     */
    public static void migrateEnabledToDisabledIfNeeded(Context ctx) {
        if (!prefs(ctx).contains(KEY_USE_ENABLED_FILTER)) return;

        if (CANDataReader.SIGNAL_REGISTRY == null || CANDataReader.SIGNAL_REGISTRY.length == 0) {
            LogBuffer.w("AppConfig", "SIGNAL_REGISTRY unavailable; skipping enabled-filter migration");
            return;
        }

        boolean useEnabled = prefs(ctx).getBoolean(KEY_USE_ENABLED_FILTER, false);
        Set<String> enabled = getEnabledSignals(ctx);

        Set<String> disabled = new HashSet<>();
        if (useEnabled) {
            for (String[] sig : CANDataReader.SIGNAL_REGISTRY) {
                if (sig == null || sig.length < 3) continue;
                String k = sig[2];
                if (!enabled.contains(k)) disabled.add(k);
            }
        }
        setDisabledSignals(ctx, disabled);
        prefs(ctx).edit()
            .remove(KEY_USE_ENABLED_FILTER)
            .remove(KEY_ENABLED_SIGNALS)
            .apply();
    }

    /**
     * Persist the selected signal set and whether the filter is active.
     * Passing {@code useFilter=false} means "send all signals".
     *
     * @deprecated Use {@link #setDisabledSignals(Context, Set)} instead.
     */
    @Deprecated
    public static void saveEnabledSignals(Context ctx, Set<String> keys, boolean useFilter) {
        prefs(ctx).edit()
            .putStringSet(KEY_ENABLED_SIGNALS, keys != null ? keys : Collections.emptySet())
            .putBoolean(KEY_USE_ENABLED_FILTER, useFilter)
            .apply();
    }

    public static void save(Context ctx, String host, int port, String token, String carName,
                            boolean enabled, boolean https) {
        save(ctx, host, port, token, carName, enabled, https,
                isBootAutoStartEnabled(ctx), isCarControlEnabled(ctx), isDetailedLogEnabled(ctx));
    }

    public static void save(Context ctx, String host, int port, String token, String carName,
                            boolean enabled, boolean https, boolean bootAutoStart) {
        save(ctx, host, port, token, carName, enabled, https, bootAutoStart,
                isCarControlEnabled(ctx), isDetailedLogEnabled(ctx));
    }

    public static void save(Context ctx, String host, int port, String token, String carName,
                            boolean enabled, boolean https, boolean bootAutoStart,
                            boolean carControl, boolean detailedLog) {
        // Persist sensitive token encrypted
        getSecureStorage(ctx).saveToken(token != null ? token : "");

        prefs(ctx).edit()
            .putString(KEY_HOST, host)
            .putString(KEY_PORT, String.valueOf(port))
            .putString(KEY_CAR_NAME, carName)
            .putBoolean(KEY_ENABLED, enabled)
            .putBoolean(KEY_HTTPS, https)
            .putBoolean(KEY_BOOT_AUTO_START, bootAutoStart)
            .putBoolean(KEY_CAR_CONTROL_ENABLED, carControl)
            .putBoolean(KEY_DETAILED_LOG_ENABLED, detailedLog)
            .apply();
        cachedCarName = carName;
    }

    public static void saveCarControlEnabled(Context ctx, boolean enabled) {
        prefs(ctx).edit().putBoolean(KEY_CAR_CONTROL_ENABLED, enabled).apply();
    }

    public static void saveDetailedLogEnabled(Context ctx, boolean enabled) {
        prefs(ctx).edit().putBoolean(KEY_DETAILED_LOG_ENABLED, enabled).apply();
    }

    public static void saveAdbHost(Context ctx, String host) {
        prefs(ctx).edit().putString(KEY_ADB_HOST, host != null ? host : "127.0.0.1").apply();
    }

    public static void saveAdbPort(Context ctx, int port) {
        prefs(ctx).edit().putString(KEY_ADB_PORT, String.valueOf(port)).apply();
    }

    public static String autoGenerateCarName(Context ctx) {
        String vin = CANDataReader.sVin;
        if ("---".equals(vin)) vin = "";

        String defaultName = "";
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"getprop", "persist.sys.byd.default_name"});
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            defaultName = br.readLine();
            br.close();
            p.waitFor();
        } catch (Exception e) {
            LogBuffer.d("AppConfig", "getprop persist.sys.byd.default_name failed: " + e.getMessage());
        }
        if (defaultName == null || defaultName.isEmpty()) {
            defaultName = android.os.Build.MODEL;
        }

        String safeName = defaultName.replaceAll("[^a-zA-Z0-9_-]", "_").replaceAll("_+", "_").replaceAll("^_|_$", "");
        String shortVin = vin.length() > 8 ? vin.substring(0, 8) : vin;
        String carName = safeName + (shortVin.isEmpty() ? "" : "_" + shortVin);
        if (carName.isEmpty()) carName = "byd_car";

        cachedCarName = carName;
        return carName;
    }

    /**
     * Persist the ordered list of dashboard tiles. Each tile is encoded via
     * {@link DashboardTile#toJson()}.
     */
    public static void saveDashboardTiles(Context ctx, List<DashboardTile> tiles) {
        if (tiles == null || tiles.isEmpty()) {
            prefs(ctx).edit().remove(KEY_DASHBOARD_TILES).apply();
            return;
        }
        JSONArray arr = new JSONArray();
        for (DashboardTile tile : tiles) {
                try {
                    arr.put(tile.toJson());
                } catch (Exception e) {
                    LogBuffer.e("AppConfig", "saveDashboardTiles error: " + e.getMessage());
                }
            }
        prefs(ctx).edit().putString(KEY_DASHBOARD_TILES, arr.toString()).apply();
    }

    /**
     * Load the ordered list of dashboard tiles. Returns null when the user has
     * never configured the dashboard, so the caller can fall back to defaults.
     */
    public static List<DashboardTile> loadDashboardTiles(Context ctx) {
        String json = prefs(ctx).getString(KEY_DASHBOARD_TILES, null);
        if (json == null || json.isEmpty()) {
            return null;
        }
        List<DashboardTile> tiles = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj == null) continue;
                String typeStr = obj.optString("type", "sensor");
                String key = obj.optString("key", "");
                String value = obj.optString("value", null);
                if (key.isEmpty()) continue;
                tiles.add(DashboardTileFactory.create(ctx, typeStr, key, value));
            }
        } catch (Exception e) {
            LogBuffer.e("AppConfig", "loadDashboardTiles error: " + e.getMessage());
            return null;
        }
        if (tiles.isEmpty()) return null;
        return tiles;
    }

    public static String getRulesJson(Context ctx) {
        return prefs(ctx).getString(KEY_RULES_JSON, null);
    }

    public static void saveRulesJson(Context ctx, String json) {
        prefs(ctx).edit().putString(KEY_RULES_JSON, json).apply();
    }

    public static String getSensorValueHistoryJson(Context ctx) {
        return prefs(ctx).getString(KEY_SENSOR_VALUE_HISTORY_JSON, null);
    }

    public static void saveSensorValueHistoryJson(Context ctx, String json) {
        prefs(ctx).edit().putString(KEY_SENSOR_VALUE_HISTORY_JSON, json).apply();
    }

    public static void saveGeofences(Context ctx, List<GeofenceZone> zones) {
        org.json.JSONArray arr = new org.json.JSONArray();
        if (zones != null) {
            for (GeofenceZone z : zones) arr.put(z.toJson());
        }
        prefs(ctx).edit().putString(KEY_GEOFENCES, arr.toString()).apply();
    }

    public static List<GeofenceZone> loadGeofences(Context ctx) {
        String json = prefs(ctx).getString(KEY_GEOFENCES, "[]");
        List<GeofenceZone> zones = new ArrayList<>();
        try {
            org.json.JSONArray arr = new org.json.JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                zones.add(GeofenceZone.fromJson(arr.getJSONObject(i)));
            }
        } catch (Exception e) {
            LogBuffer.e("AppConfig", "loadGeofences error: " + e.getMessage());
        }
        return zones;
    }

    public static void saveRegistryMeta(Context ctx, int version, long lastCheck) {
        prefs(ctx).edit()
            .putInt(KEY_REGISTRY_VERSION, version)
            .putLong(KEY_REGISTRY_LAST_CHECK, lastCheck)
            .apply();
    }

    public static int getRegistryVersion(Context ctx) {
        return prefs(ctx).getInt(KEY_REGISTRY_VERSION, 0);
    }

    public static long getRegistryLastCheck(Context ctx) {
        return prefs(ctx).getLong(KEY_REGISTRY_LAST_CHECK, 0);
    }

    private static final String KEY_DENIED_PERMISSIONS = "denied_permissions";
    private static final String KEY_PRESET_VERSION = "preset_version";
    private static final String KEY_PRESET_LAST_CHECK = "preset_last_check";

    public static void savePresetMeta(Context ctx, int version, long lastCheck) {
        prefs(ctx).edit()
            .putInt(KEY_PRESET_VERSION, version)
            .putLong(KEY_PRESET_LAST_CHECK, lastCheck)
            .apply();
    }

    public static int getPresetVersion(Context ctx) {
        return prefs(ctx).getInt(KEY_PRESET_VERSION, 0);
    }

    public static long getPresetLastCheck(Context ctx) {
        return prefs(ctx).getLong(KEY_PRESET_LAST_CHECK, 0);
    }

    public static Set<String> getDeniedPermissions(Context ctx) {
        return prefs(ctx).getStringSet(KEY_DENIED_PERMISSIONS, new HashSet<>());
    }

    public static void addDeniedPermission(Context ctx, String permission) {
        Set<String> denied = new HashSet<>(getDeniedPermissions(ctx));
        denied.add(permission);
        prefs(ctx).edit().putStringSet(KEY_DENIED_PERMISSIONS, denied).apply();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
}
