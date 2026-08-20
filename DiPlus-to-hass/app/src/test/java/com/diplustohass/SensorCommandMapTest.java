package com.diplustohass;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Plain-Java tests for the command-to-sensor verification map
 * ({@code app/src/main/assets/sensor_command_map.json}).
 *
 * <p>Each command's effective expected sensor state must be a real enum label
 * defined by {@link SignalTranslator}. This catches the false {@code state
 * mismatch} bugs that occur when the map uses a command-lifecycle value
 * (e.g. {@code close}, {@code on}) instead of the translated sensor state
 * (e.g. {@code closed}, {@code open}).</p>
 *
 * <p>Run from the project root (DiPlus-to-hass) so the asset can be read by
 * relative path. No Android runtime dependency.</p>
 */
public class SensorCommandMapTest {

    private static final String MAP_PATH = "app/src/main/assets/sensor_command_map.json";
    private static final int EXPECTED_VERSION = 5;

    /**
     * Sensor states that are translated directly from Chinese and have no
     * matching enum label (spec 6.4). E.g. drive_mode "snow" (雪地) is not a
     * distinct enum state — the sensor only reports NORMAL/ECO/SPORT.
     */
    private static final Set<String> CHINESE_DIRECT_EXCEPTIONS = new HashSet<>();
    static {
        CHINESE_DIRECT_EXCEPTIONS.add("snow");
    }

    public static void main(String[] args) throws Exception {
        JSONObject root = loadMap();
        testVersion(root);
        Map<String, Set<String>> sensorLabels = buildLabelSets();
        testEffectiveExpectedMatchesEnumLabels(root, sensorLabels);
        System.out.println("All SensorCommandMap tests passed.");
    }

    private static void testVersion(JSONObject root) {
        int version = root.optInt("version", 0);
        if (version != EXPECTED_VERSION) {
            throw new AssertionError("expected map version " + EXPECTED_VERSION + ", got " + version);
        }
    }

    /**
     * For every non-parameter link whose sensor has enum labels, the effective
     * expected value (explicit {@code expected} or fallback {@code value}) must
     * be one of the sensor's translated enum labels.
     */
    private static void testEffectiveExpectedMatchesEnumLabels(JSONObject root,
                                                               Map<String, Set<String>> sensorLabels) {
        JSONArray mappings = root.optJSONArray("mappings");
        if (mappings == null) throw new AssertionError("no mappings array");
        int checked = 0;
        for (int i = 0; i < mappings.length(); i++) {
            JSONObject mapping = mappings.optJSONObject(i);
            if (mapping == null) continue;
            String sensorKey = mapping.optString("sensor_key");
            if (sensorKey.isEmpty()) continue;
            Set<String> labels = sensorLabels.get(sensorKey);
            if (labels == null || labels.isEmpty()) continue; // no enum labels, e.g. numeric sensors

            JSONArray cmds = mapping.optJSONArray("commands");
            if (cmds == null) continue;
            for (int j = 0; j < cmds.length(); j++) {
                JSONObject c = cmds.optJSONObject(j);
                if (c == null) continue;
                boolean param = "parameter".equals(c.optString("value_source"));
                if (param) continue;
                String value = c.optString("value");
                String effective = c.has("expected") ? c.optString("expected") : value;
                if (effective.isEmpty()) {
                    throw new AssertionError(sensorKey + ": empty effective expected for " + c.optString("command_id"));
                }
                if (!labels.contains(effective) && !CHINESE_DIRECT_EXCEPTIONS.contains(effective)) {
                    throw new AssertionError(sensorKey + " (" + c.optString("command_id")
                            + ", value=" + value + "): expected state '" + effective
                            + "' is not a valid enum label " + labels);
                }
                checked++;
            }
        }
        System.out.println("  verified " + checked + " command-to-sensor expected states");
    }

    private static Map<String, Set<String>> buildLabelSets() {
        Map<String, Set<String>> result = new HashMap<>();
        Map<String, String> labels = SignalTranslator.getEnumLabels();
        if (labels == null) return result;
        for (Map.Entry<String, String> e : labels.entrySet()) {
            Set<String> set = new HashSet<>();
            for (String part : e.getValue().split(",")) {
                String[] kv = part.split(":", 2);
                if (kv.length == 2) set.add(kv[1].trim());
            }
            result.put(e.getKey(), set);
        }
        return result;
    }

    private static JSONObject loadMap() throws Exception {
        File f = new File(MAP_PATH);
        if (!f.exists()) {
            throw new AssertionError("map file not found at " + f.getAbsolutePath()
                    + " (run from the DiPlus-to-hass project root)");
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
        }
        return new JSONObject(sb.toString());
    }
}
