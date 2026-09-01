package com.car2hass;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DashboardPresetRegistry {
    private static final String TAG = "DashPresetRegistry";
    private static final String REMOTE_BASE = "https://mytechnic.ru/cartelemetry/";
    private static final String META_FILE = "dashboard_presets.meta.json";
    private static final String FULL_FILE = "dashboard_presets.json";
    private static final String FULL_URL = REMOTE_BASE + "download.php?file=presets";
    private static final long CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L;
    private static final long FIRST_CHECK_INTERVAL_MS = 60 * 1000L;

    public static class PresetAction {
        public final String id;
        public final String label;
        public final String labelRu;
        public final String icon;
        public final String command;
        public final String value;
        PresetAction(JSONObject o) {
            this.id = o.optString("id", "");
            this.label = o.optString("label", "");
            this.labelRu = o.optString("label_ru", "");
            this.icon = o.optString("icon", "");
            this.command = o.optString("command", "");
            this.value = o.has("value") && !o.isNull("value") ? o.optString("value", null) : null;
        }
    }

    /** Pick the RU variant when the app language is Russian and a non-empty RU string exists. */
    public static String pick(Context ctx, String en, String ru) {
        if (ru != null && !ru.isEmpty() && LocaleHelper.LANG_RU.equals(LocaleHelper.getLanguage(ctx))) {
            return ru;
        }
        return en != null ? en : "";
    }

    public static class PresetParam {
        public final String id;
        public final String label;
        public final String labelRu;
        public final String command;
        public final String sensor;
        public final double min;
        public final double max;
        public final double step;
        public final String unit;
        PresetParam(JSONObject o) {
            this.id = o.optString("id", "");
            this.label = o.optString("label", "");
            this.labelRu = o.optString("label_ru", "");
            this.command = o.optString("command", "");
            this.sensor = o.optString("sensor", "");
            this.min = o.optDouble("min", 0);
            this.max = o.optDouble("max", 100);
            this.step = o.optDouble("step", 1);
            this.unit = o.optString("unit", "");
        }
    }

    public static class PresetZone {
        public final String side;
        public final String icon;
        public final String command;
        public final String value;
        PresetZone(JSONObject o) {
            this.side = o.optString("side", "");
            this.icon = o.optString("icon", "");
            this.command = o.optString("command", "");
            this.value = o.has("value") && !o.isNull("value") ? o.optString("value", null) : null;
        }
    }

    public static class PresetOption {
        public final String value;
        public final String label;
        public final String labelRu;
        public final String command;
        public final String commandValue;
        PresetOption(JSONObject o) {
            this.value = o.optString("value", "");
            this.label = o.optString("label", "");
            this.labelRu = o.optString("label_ru", "");
            this.command = o.optString("command", "");
            this.commandValue = o.optString("command_value", "");
        }
    }

    public static class PresetStateCommands {
        public final String onId;
        public final String onValue;
        public final String offId;
        public final String offValue;
        public final String actionId;
        public final String actionValue;
        PresetStateCommands(JSONObject o) {
            JSONObject on = o.optJSONObject("on");
            JSONObject off = o.optJSONObject("off");
            JSONObject action = o.optJSONObject("action");
            if (on != null) {
                this.onId = on.optString("id", "");
                this.onValue = on.has("value") && !on.isNull("value") ? on.optString("value", null) : null;
            } else {
                this.onId = ""; this.onValue = null;
            }
            if (off != null) {
                this.offId = off.optString("id", "");
                this.offValue = off.has("value") && !off.isNull("value") ? off.optString("value", null) : null;
            } else {
                this.offId = ""; this.offValue = null;
            }
            if (action != null) {
                this.actionId = action.optString("id", "");
                this.actionValue = action.has("value") && !action.isNull("value") ? action.optString("value", null) : null;
            } else {
                this.actionId = ""; this.actionValue = null;
            }
        }
    }

    public static class PresetStateDisplay {
        public final String onLabel;
        public final String onLabelRu;
        public final String onSub;
        public final String onSubRu;
        public final boolean onAlert;
        public final String offLabel;
        public final String offLabelRu;
        public final String offSub;
        public final String offSubRu;
        public final boolean offAlert;
        public final String alertLabel;
        public final String alertLabelRu;
        public final String alertSub;
        public final String alertSubRu;
        public final boolean alertAlert;
        public final String okLabel;
        public final String okLabelRu;
        public final String okSub;
        public final String okSubRu;
        public final boolean okAlert;
        public final String valueFormat;
        public final String valueFormatRu;
        PresetStateDisplay(JSONObject o, JSONObject alerts) {
            JSONObject on = o != null ? o.optJSONObject("on") : null;
            JSONObject off = o != null ? o.optJSONObject("off") : null;
            JSONObject alert = o != null ? o.optJSONObject("alert") : null;
            JSONObject ok = o != null ? o.optJSONObject("ok") : null;
            JSONObject value = o != null ? o.optJSONObject("value") : null;

            if (on != null) {
                this.onLabel = on.optString("label", "");
                this.onLabelRu = on.optString("label_ru", "");
                this.onSub = on.has("sub") && !on.isNull("sub") ? on.optString("sub", null) : null;
                this.onSubRu = on.has("sub_ru") && !on.isNull("sub_ru") ? on.optString("sub_ru", null) : null;
                this.onAlert = alerts != null ? alerts.optBoolean("on", false) : on.optBoolean("alert", false);
            } else {
                this.onLabel = ""; this.onLabelRu = ""; this.onSub = null; this.onSubRu = null; this.onAlert = false;
            }
            if (off != null) {
                this.offLabel = off.optString("label", "");
                this.offLabelRu = off.optString("label_ru", "");
                this.offSub = off.has("sub") && !off.isNull("sub") ? off.optString("sub", null) : null;
                this.offSubRu = off.has("sub_ru") && !off.isNull("sub_ru") ? off.optString("sub_ru", null) : null;
                this.offAlert = alerts != null ? alerts.optBoolean("off", false) : off.optBoolean("alert", false);
            } else {
                this.offLabel = ""; this.offLabelRu = ""; this.offSub = null; this.offSubRu = null; this.offAlert = false;
            }
            if (alert != null) {
                this.alertLabel = alert.optString("label", "");
                this.alertLabelRu = alert.optString("label_ru", "");
                this.alertSub = alert.has("sub") && !alert.isNull("sub") ? alert.optString("sub", null) : null;
                this.alertSubRu = alert.has("sub_ru") && !alert.isNull("sub_ru") ? alert.optString("sub_ru", null) : null;
                this.alertAlert = alerts != null ? alerts.optBoolean("alert", false) : alert.optBoolean("alert", false);
            } else {
                this.alertLabel = ""; this.alertLabelRu = ""; this.alertSub = null; this.alertSubRu = null; this.alertAlert = false;
            }
            if (ok != null) {
                this.okLabel = ok.optString("label", "");
                this.okLabelRu = ok.optString("label_ru", "");
                this.okSub = ok.has("sub") && !ok.isNull("sub") ? ok.optString("sub", null) : null;
                this.okSubRu = ok.has("sub_ru") && !ok.isNull("sub_ru") ? ok.optString("sub_ru", null) : null;
                this.okAlert = alerts != null ? alerts.optBoolean("ok", false) : ok.optBoolean("alert", false);
            } else {
                this.okLabel = ""; this.okLabelRu = ""; this.okSub = null; this.okSubRu = null; this.okAlert = false;
            }
            this.valueFormat = value != null ? value.optString("value", "") : "";
            this.valueFormatRu = value != null ? value.optString("value_ru", "") : "";
        }
    }

    public static class PresetState {
        public final String source;
        public final String primarySensor;
        public final String truthySensor;
        public final List<String> truthy;
        public final String truthyMode;
        public final PresetStateDisplay display;
        public final double thresholdOpValue;
        public final String thresholdState;

        PresetState(JSONObject o) {
            this.source = o.optString("source", "binary_any");
            this.primarySensor = o.has("primary_sensor") && !o.isNull("primary_sensor")
                    ? o.optString("primary_sensor", null) : null;
            this.truthySensor = o.has("truthy_sensor") && !o.isNull("truthy_sensor")
                    ? o.optString("truthy_sensor", null) : null;
            JSONArray t = o.optJSONArray("truthy");
            if (t != null) {
                List<String> list = new ArrayList<>();
                for (int i = 0; i < t.length(); i++) {
                    Object val = t.opt(i);
                    list.add(val != null ? val.toString() : "");
                }
                this.truthy = list;
            } else {
                this.truthy = Collections.emptyList();
            }
            this.truthyMode = o.optString("truthy_mode", "list");

            JSONObject alerts = o.optJSONObject("alert");
            JSONObject d = o.optJSONObject("display");
            this.display = new PresetStateDisplay(d, alerts);

            double tv = 0;
            String ts = "ok";
            JSONArray thresholds = o.optJSONArray("thresholds");
            if (thresholds != null && thresholds.length() > 0) {
                JSONObject th = thresholds.optJSONObject(0);
                if (th != null) {
                    tv = th.optDouble("value", 0);
                    ts = th.optString("state", "ok");
                }
            }
            this.thresholdOpValue = tv;
            this.thresholdState = ts;
        }
    }

    /**
     * Preset behaviors:
     *  - toggle: tap toggles between on/off commands using primary sensor.
     *  - command: tap sends a single action command.
     *  - dual_action: tap alternates left/right zone commands.
     *  - dual_action_toggle: tile has left (-), right (+) zones and a separate
     *    on/off toggle using commands.on / commands.off.
     *  - select: tap cycles through options.
     *  - composite: read-only aggregation of multiple sensors with optional actions.
     */
    public static class DashboardPreset {
        public final String id;
        public final String label;
        public final String labelRu;
        public final String icon;
        public final String behavior;
        public final List<String> sensors;
        public final PresetState state;
        public final PresetStateCommands commands;
        public final List<PresetAction> actions;
        public final List<PresetParam> params;
        public final List<PresetZone> zones;
        public final List<PresetOption> options;

        DashboardPreset(JSONObject o) {
            this.id = o.optString("id", "");
            this.label = o.optString("label", "");
            this.labelRu = o.optString("label_ru", "");
            this.icon = o.optString("icon", "");
            this.behavior = o.optString("behavior", "command");
            JSONArray s = o.optJSONArray("sensors");
            if (s != null) {
                List<String> list = new ArrayList<>();
                for (int i = 0; i < s.length(); i++) list.add(s.optString(i, ""));
                this.sensors = list;
            } else {
                this.sensors = Collections.emptyList();
            }
            this.state = o.has("state") ? new PresetState(o.optJSONObject("state")) : null;

            JSONObject cmd = o.optJSONObject("commands");
            this.commands = cmd != null ? new PresetStateCommands(cmd) : null;

            JSONArray a = o.optJSONArray("actions");
            if (a != null) {
                List<PresetAction> list = new ArrayList<>();
                for (int i = 0; i < a.length(); i++) list.add(new PresetAction(a.optJSONObject(i)));
                this.actions = list;
            } else {
                this.actions = Collections.emptyList();
            }

            JSONArray p = o.optJSONArray("params");
            if (p != null) {
                List<PresetParam> list = new ArrayList<>();
                for (int i = 0; i < p.length(); i++) list.add(new PresetParam(p.optJSONObject(i)));
                this.params = list;
            } else {
                this.params = Collections.emptyList();
            }

            JSONArray z = o.optJSONArray("zones");
            if (z != null) {
                List<PresetZone> list = new ArrayList<>();
                for (int i = 0; i < z.length(); i++) list.add(new PresetZone(z.optJSONObject(i)));
                this.zones = list;
            } else {
                this.zones = Collections.emptyList();
            }

            JSONArray opt = o.optJSONArray("options");
            if (opt != null) {
                List<PresetOption> list = new ArrayList<>();
                for (int i = 0; i < opt.length(); i++) list.add(new PresetOption(opt.optJSONObject(i)));
                this.options = list;
            } else {
                this.options = Collections.emptyList();
            }
        }
    }

    private static DashboardPresetRegistry instance;
    private final Context appCtx;
    private volatile Map<String, DashboardPreset> presets;
    private volatile int loadedVersion;

    public static synchronized DashboardPresetRegistry getInstance(Context ctx) {
        if (instance == null) instance = new DashboardPresetRegistry(ctx.getApplicationContext());
        return instance;
    }

    private DashboardPresetRegistry(Context ctx) {
        this.appCtx = ctx;
        this.presets = new LinkedHashMap<>();
        load();
    }

    public synchronized void load() {
        JSONObject cachedRoot = null;
        File cached = new File(appCtx.getFilesDir(), FULL_FILE);
        if (cached.exists()) {
            try (FileInputStream fis = new FileInputStream(cached)) {
                cachedRoot = new JSONObject(readStream(fis));
            } catch (Exception e) {
                LogBuffer.w(TAG, "Failed to load cached presets: " + e.getMessage());
            }
        }
        JSONObject assetRoot = null;
        try (InputStream is = appCtx.getAssets().open(FULL_FILE)) {
            assetRoot = new JSONObject(readStream(is));
        } catch (Exception e) {
            LogBuffer.w(TAG, "Failed to load bundled presets: " + e.getMessage());
        }
        JSONObject root;
        if (cachedRoot == null && assetRoot == null) {
            LogBuffer.e(TAG, "Failed to load any preset file");
            return;
        } else if (assetRoot == null) {
            root = cachedRoot;
        } else if (cachedRoot == null) {
            root = assetRoot;
        } else {
            root = cachedRoot.optInt("version", 0) >= assetRoot.optInt("version", 0)
                    ? cachedRoot : assetRoot;
        }
        if (root == null) return;

        Map<String, DashboardPreset> map = new LinkedHashMap<>();
        JSONArray arr = root.optJSONArray("presets");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                DashboardPreset p = new DashboardPreset(arr.optJSONObject(i));
                if (!p.id.isEmpty()) map.put(p.id, p);
            }
        }
        this.presets = map;
        this.loadedVersion = root.optInt("version", 0);
        LogBuffer.i(TAG, "Loaded " + map.size() + " presets v" + this.loadedVersion + " (cached=" + (cachedRoot != null) + ", assets=" + (assetRoot != null) + ")");
    }

    /** Version of the preset file currently in use (cached OTA or bundled). */
    public int getLoadedVersion() {
        return loadedVersion;
    }

    public DashboardPreset getPreset(String id) {
        return presets != null ? presets.get(id) : null;
    }

    public List<DashboardPreset> getAllPresets() {
        if (presets == null) return Collections.emptyList();
        return new ArrayList<>(presets.values());
    }

    public static final int UPDATE_UPDATED = 0;
    public static final int UPDATE_ALREADY_LATEST = 1;
    public static final int UPDATE_FAILED = 2;

    /** Result of a manual update check; always delivered on the main thread. */
    public interface UpdateCheckListener {
        void onUpdateCheckResult(int result, int version);
    }

    public void checkForUpdates() {
        checkForUpdates(false, null);
    }

    public void checkForUpdates(boolean force) {
        checkForUpdates(force, null);
    }

    public void checkForUpdates(boolean force, UpdateCheckListener listener) {
        long now = System.currentTimeMillis();
        long lastCheck = AppConfig.getPresetLastCheck(appCtx);
        boolean hasPresets = presets != null && !presets.isEmpty();
        long interval = (lastCheck == 0 || !hasPresets) ? FIRST_CHECK_INTERVAL_MS : CHECK_INTERVAL_MS;
        if (!force && now - lastCheck < interval) return;
        AppConfig.savePresetMeta(appCtx, AppConfig.getPresetVersion(appCtx), now);
        LogBuffer.i(TAG, "Checking for preset updates (force=" + force + ", last=" + lastCheck + ", hasPresets=" + hasPresets + ")");
        new Thread(() -> doCheck(listener)).start();
    }

    private void doCheck(UpdateCheckListener listener) {
        try {
            URL metaUrl = new URL(REMOTE_BASE + META_FILE);
            HttpURLConnection conn = (HttpURLConnection) metaUrl.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("X-Car2Hass-Id", AppConfig.getAppInstanceId(appCtx));
            JSONObject meta = new JSONObject(readStream(conn.getInputStream()));
            int remoteVersion = meta.optInt("version", 0);
            int localVersion = AppConfig.getPresetVersion(appCtx);
            if (remoteVersion <= localVersion) {
                notifyUpdateResult(listener, UPDATE_ALREADY_LATEST, loadedVersion);
                return;
            }

            URL fullUrl = new URL(FULL_URL);
            HttpURLConnection fullConn = (HttpURLConnection) fullUrl.openConnection();
            fullConn.setConnectTimeout(15000);
            fullConn.setReadTimeout(15000);
            fullConn.setRequestProperty("X-Car2Hass-Id", AppConfig.getAppInstanceId(appCtx));
            String payload = readStream(fullConn.getInputStream());
            JSONObject parsed = new JSONObject(payload);
            if (parsed.optInt("version", 0) != remoteVersion) {
                notifyUpdateResult(listener, UPDATE_FAILED, loadedVersion);
                return;
            }

            try (FileOutputStream fos = new FileOutputStream(new File(appCtx.getFilesDir(), FULL_FILE))) {
                fos.write(payload.getBytes(StandardCharsets.UTF_8));
            }
            try (FileOutputStream fos = new FileOutputStream(new File(appCtx.getFilesDir(), META_FILE))) {
                fos.write(meta.toString().getBytes(StandardCharsets.UTF_8));
            }
            AppConfig.savePresetMeta(appCtx, remoteVersion, System.currentTimeMillis());
            load();
            LogBuffer.i(TAG, "Updated presets to version " + remoteVersion);
            notifyUpdateResult(listener, UPDATE_UPDATED, remoteVersion);
        } catch (Exception e) {
            LogBuffer.w(TAG, "Preset update check failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            notifyUpdateResult(listener, UPDATE_FAILED, loadedVersion);
        }
    }

    private void notifyUpdateResult(UpdateCheckListener listener, int result, int version) {
        if (listener == null) return;
        new Handler(Looper.getMainLooper()).post(() -> listener.onUpdateCheckResult(result, version));
    }

    private static String readStream(InputStream is) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString();
    }
}
