package com.car2hass;

import android.content.Context;
import android.util.Log;
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
import java.util.HashMap;
import java.util.List;

public class SensorCommandRegistry {
    private static final String TAG = "SensorCmdRegistry";
    private static final String REMOTE_BASE = "https://mytechnic.ru/cartelemetry/";
    private static final String META_FILE = "sensor_command_map.meta.json";
    private static final String FULL_FILE = "sensor_command_map.json";
    private static final String FULL_URL = REMOTE_BASE + "download.php?file=mappings";
    private static final long CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L;

    public static class LinkedCommand {
        public final String commandId;
        public final String value;
        public final boolean needsParameter;
        public final String labelKey;

        LinkedCommand(String commandId, String value, boolean needsParameter, String labelKey) {
            this.commandId = commandId;
            this.value = value;
            this.needsParameter = needsParameter;
            this.labelKey = labelKey;
        }
    }

    /**
     * Reverse mapping entry: which sensor can be used to verify that a command
     * actually changed the vehicle state, and what value is expected.
     *
     * <p>{@code value} is the command value (used to select the right link and
     * to pre-fill dashboard commands). {@code expectedValue} is the sensor state
     * that should be observed after the command runs (falls back to {@code value}
     * when the map does not provide an explicit {@code expected}). For
     * parameter-driven commands both are {@code null}.</p>
     */
    public static class SensorLink {
        public final String sensorKey;
        public final String value;
        public final boolean needsParameter;
        public final String expectedValue;

        SensorLink(String sensorKey, String value, boolean needsParameter, String expectedValue) {
            this.sensorKey = sensorKey;
            this.value = value;
            this.needsParameter = needsParameter;
            this.expectedValue = expectedValue;
        }
    }

    public static class RegistryMeta {
        public final int version;
        public final String updatedAt;

        RegistryMeta(int version, String updatedAt) {
            this.version = version;
            this.updatedAt = updatedAt;
        }
    }

    private static SensorCommandRegistry instance;
    private final Context appCtx;
    private volatile JSONObject root;
    private final HashMap<String, List<SensorLink>> commandToSensors = new HashMap<>();

    public static synchronized SensorCommandRegistry getInstance(Context ctx) {
        if (instance == null) instance = new SensorCommandRegistry(ctx.getApplicationContext());
        return instance;
    }

    private SensorCommandRegistry(Context ctx) {
        this.appCtx = ctx;
        load();
    }

    public synchronized void load() {
        JSONObject cachedRoot = null;
        File cached = new File(appCtx.getFilesDir(), FULL_FILE);
        if (cached.exists()) {
            try (FileInputStream fis = new FileInputStream(cached)) {
                cachedRoot = new JSONObject(readStream(fis));
            } catch (Exception e) {
                Log.w(TAG, "Failed to load cached registry", e);
            }
        }
        JSONObject assetRoot = null;
        try (InputStream is = appCtx.getAssets().open(FULL_FILE)) {
            assetRoot = new JSONObject(readStream(is));
        } catch (Exception e) {
            Log.w(TAG, "Failed to load bundled registry", e);
        }
        if (cachedRoot == null && assetRoot == null) {
            Log.e(TAG, "Failed to load fallback registry");
            root = new JSONObject();
        } else if (assetRoot == null) {
            root = cachedRoot;
        } else if (cachedRoot == null) {
            root = assetRoot;
        } else {
            // On tie prefer the cached file.
            root = cachedRoot.optInt("version", 0) >= assetRoot.optInt("version", 0)
                    ? cachedRoot : assetRoot;
        }
        buildReverseIndex();
    }

    private void buildReverseIndex() {
        commandToSensors.clear();
        if (root == null) return;
        JSONArray mappings = root.optJSONArray("mappings");
        if (mappings == null) return;
        for (int i = 0; i < mappings.length(); i++) {
            JSONObject mapping = mappings.optJSONObject(i);
            if (mapping == null) continue;
            String sensorKey = mapping.optString("sensor_key");
            if (sensorKey.isEmpty()) continue;
            JSONArray cmds = mapping.optJSONArray("commands");
            if (cmds == null) continue;
            for (int j = 0; j < cmds.length(); j++) {
                JSONObject c = cmds.optJSONObject(j);
                if (c == null) continue;
                String id = c.optString("command_id");
                if (id.isEmpty()) continue;
                boolean param = "parameter".equals(c.optString("value_source"));
                String value = param ? null : c.optString("value");
                String expected = value == null ? null : c.optString("expected", value);
                List<SensorLink> list = commandToSensors.get(id);
                if (list == null) {
                    list = new ArrayList<>();
                    commandToSensors.put(id, list);
                }
                list.add(new SensorLink(sensorKey, value, param, expected));
            }
        }
    }

    public RegistryMeta getMeta() {
        if (root == null) return new RegistryMeta(0, "");
        return new RegistryMeta(root.optInt("version", 0), root.optString("generated_at", ""));
    }

    public List<SensorLink> getSensorsForCommand(String commandId) {
        if (commandId == null) return Collections.emptyList();
        List<SensorLink> list = commandToSensors.get(commandId);
        return list != null ? Collections.unmodifiableList(list) : Collections.emptyList();
    }

    public List<LinkedCommand> getCommandsForSensor(String sensorKey) {
        if (root == null) return Collections.emptyList();
        JSONArray mappings = root.optJSONArray("mappings");
        if (mappings == null) return Collections.emptyList();
        List<LinkedCommand> result = new ArrayList<>();
        for (int i = 0; i < mappings.length(); i++) {
            JSONObject mapping = mappings.optJSONObject(i);
            if (mapping == null) continue;
            if (!sensorKey.equals(mapping.optString("sensor_key"))) continue;
            JSONArray cmds = mapping.optJSONArray("commands");
            if (cmds == null) continue;
            for (int j = 0; j < cmds.length(); j++) {
                JSONObject c = cmds.optJSONObject(j);
                if (c == null) continue;
                String id = c.optString("command_id");
                if (id.isEmpty()) continue;
                boolean param = "parameter".equals(c.optString("value_source"));
                result.add(new LinkedCommand(
                    id,
                    param ? null : c.optString("value"),
                    param,
                    c.optString("label_key", null)
                ));
            }
        }
        return result;
    }

    public void checkForUpdates() {
        long now = System.currentTimeMillis();
        long lastCheck = AppConfig.getRegistryLastCheck(appCtx);
        if (now - lastCheck < CHECK_INTERVAL_MS) return;

        // Mark the check attempt now so the 6-hour throttle is honored
        // even when no update is available or the network call fails.
        AppConfig.saveRegistryMeta(appCtx, AppConfig.getRegistryVersion(appCtx), now);
        new Thread(this::doCheck).start();
    }

    private void doCheck() {
        try {
            URL metaUrl = new URL(REMOTE_BASE + META_FILE);
            HttpURLConnection conn = (HttpURLConnection) metaUrl.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            JSONObject meta = new JSONObject(readStream(conn.getInputStream()));
            int remoteVersion = meta.optInt("version", 0);
            int localVersion = AppConfig.getRegistryVersion(appCtx);
            if (remoteVersion <= localVersion) return;

            URL fullUrl = new URL(FULL_URL);
            HttpURLConnection fullConn = (HttpURLConnection) fullUrl.openConnection();
            fullConn.setConnectTimeout(15000);
            fullConn.setReadTimeout(15000);
            String payload = readStream(fullConn.getInputStream());
            JSONObject parsed = new JSONObject(payload);
            if (parsed.optInt("version", 0) != remoteVersion) return;

            try (FileOutputStream fos = new FileOutputStream(new File(appCtx.getFilesDir(), FULL_FILE))) {
                fos.write(payload.getBytes(StandardCharsets.UTF_8));
            }
            try (FileOutputStream fos = new FileOutputStream(new File(appCtx.getFilesDir(), META_FILE))) {
                fos.write(meta.toString().getBytes(StandardCharsets.UTF_8));
            }
            AppConfig.saveRegistryMeta(appCtx, remoteVersion, System.currentTimeMillis());
            load();
            Log.i(TAG, "Updated registry to version " + remoteVersion);
        } catch (Exception e) {
            Log.w(TAG, "Registry update check failed", e);
        }
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
