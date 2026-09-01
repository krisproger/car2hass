package com.car2hass;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Plain-Java tests for the native write command map
 * ({@code app/src/main/assets/native_commands.json} + {@link NativeCommandMap}).
 *
 * <p>Invariants (spec 2026-08-14-native-write-design.md §8):
 * <ul>
 *   <li>every {@code commandId} in the JSON exists in {@link CommandRegistry};</li>
 *   <li>no native target uses a banned dev namespace (with carve-out);</li>
 *   <li>parametric entries have min &lt; max and the value is clamped;</li>
 *   <li>every {@code verify} sensor exists in {@code SIGNAL_REGISTRY};</li>
 *   <li>fan-out arrays keep their written order.</li>
 * </ul>
 *
 * <p>No Android runtime dependency: the JSON is read by relative path and fed to
 * {@link NativeCommandMap#parse}; {@link CommandRegistry} is compiled against the
 * test-only {@link R} stub. Run from the project root (DiPlus-to-hass).
 */
public class NativeCommandMapTest {

    private static final String MAP_PATH = "app/src/main/assets/native_commands.json";

    /** BYDMate {@code WriteAllowlist.BANNED_DEVS}. */
    private static final Set<Integer> BANNED_DEVS = new HashSet<>();
    static {
        int[] banned = {1004, 1006, 1007, 1009, 1011, 1012, 1013, 1014, 1016, 1023, 1032};
        for (int b : banned) BANNED_DEVS.add(b);
    }

    /** BYDMate {@code WriteAllowlist.BANNED_DEV_FID_EXCEPTIONS} as "dev:fid". */
    private static final Set<String> CARVE_OUT = new HashSet<>();
    static {
        CARVE_OUT.add("1023:1330643002"); // interior light
        CARVE_OUT.add("1023:1069547536"); // ambient light
        CARVE_OUT.add("1004:1125122118"); // DRL
        CARVE_OUT.add("1004:871366669");  // hazard
        CARVE_OUT.add("1023:850427920");  // fridge mode
        CARVE_OUT.add("1023:850427928");  // fridge temp
    }

    public static void main(String[] args) throws Exception {
        String json = loadMap();
        NativeCommandMap.parse(json);

        testVersion(json);
        testCommandIdsExistInRegistry();
        testNoBannedDevs();
        testParametricClamping();
        testVerifySensorsExist();
        testFanOutOrderPreserved();

        System.out.println("All NativeCommandMap tests passed.");
    }

    private static void testVersion(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        int version = root.optInt("version", 0);
        if (version != 1) {
            throw new AssertionError("expected map version 1, got " + version);
        }
    }

    private static void testCommandIdsExistInRegistry() {
        for (String id : NativeCommandMap.allCommandIds()) {
            if (CommandRegistry.getById(id) == null) {
                throw new AssertionError("command id not in CommandRegistry: " + id);
            }
        }
        if (NativeCommandMap.allCommandIds().size() < 10) {
            throw new AssertionError("expected >= 10 commands, got " + NativeCommandMap.allCommandIds().size());
        }
    }

    /** No native target may sit on a banned dev namespace unless carved out. */
    private static void testNoBannedDevs() {
        for (String id : NativeCommandMap.allCommandIds()) {
            if (!NativeCommandMap.hasNative(id)) continue;
            for (String target : NativeCommandMap.allNativeTargets(id)) {
                String[] parts = target.split(":");
                int dev = Integer.parseInt(parts[0]);
                int fid = Integer.parseInt(parts[1]);
                if (BANNED_DEVS.contains(dev) && !CARVE_OUT.contains(target)) {
                    throw new AssertionError(id + ": banned dev " + dev + " fid " + fid);
                }
            }
        }
    }

    private static void testParametricClamping() throws Exception {
        JSONObject root = new JSONObject(loadMap());
        JSONObject commands = root.getJSONObject("commands");
        for (String id : keySet(commands)) {
            JSONObject cmd = commands.getJSONObject(id);
            JSONObject nativeObj = cmd.optJSONObject("native");
            if (nativeObj == null || !nativeObj.has("valueExpr")) continue;
            int min = nativeObj.getInt("min");
            int max = nativeObj.getInt("max");
            if (min >= max) {
                throw new AssertionError(id + ": min " + min + " >= max " + max);
            }
            List<NativeCommandMap.WriteOp> below = NativeCommandMap.resolve(id, String.valueOf(min - 5));
            List<NativeCommandMap.WriteOp> above = NativeCommandMap.resolve(id, String.valueOf(max + 5));
            if (below == null || above == null
                    || below.get(0).value != min || above.get(0).value != max) {
                throw new AssertionError(id + ": valueExpr must clamp into [" + min + "," + max + "]");
            }
        }
    }

    private static void testVerifySensorsExist() {
        Set<String> registry = signalRegistryKeys();
        for (String id : NativeCommandMap.allCommandIds()) {
            String verify = NativeCommandMap.getVerifySensor(id);
            if (verify == null || verify.isEmpty()) continue;
            if (!registry.contains(verify)) {
                throw new AssertionError(id + ": verify sensor '" + verify + "' not in SIGNAL_REGISTRY");
            }
        }
    }

    /** Fan-out commands keep their written order: check the target list preserves dev/fid order. */
    private static void testFanOutOrderPreserved() throws Exception {
        JSONObject root = new JSONObject(loadMap());
        JSONObject commands = root.getJSONObject("commands");
        for (String id : keySet(commands)) {
            JSONObject cmd = commands.getJSONObject(id);
            if (cmd.optJSONArray("native") == null) continue;
            JSONArray arr = cmd.getJSONArray("native");
            if (arr.length() < 2) continue;
            if (!NativeCommandMap.isFanOut(id)) {
                throw new AssertionError(id + ": native array of " + arr.length() + " ops must be a fan-out");
            }
            // Resolve with any value: fixed fan-out ignores the value.
            List<NativeCommandMap.WriteOp> ops = NativeCommandMap.resolve(id, "50");
            if (ops == null || ops.size() != arr.length()) {
                throw new AssertionError(id + ": resolved fan-out size mismatch");
            }
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (ops.get(i).device != o.getInt("dev") || ops.get(i).fid != o.getInt("fid")) {
                    throw new AssertionError(id + ": fan-out order broken at index " + i);
                }
            }
        }
    }

    /** Extracts stable HA keys from CANDataReader.SIGNAL_REGISTRY by parsing the source. */
    private static Set<String> signalRegistryKeys() {
        Set<String> keys = new HashSet<>();
        try {
            File f = new File("app/src/main/java/com/car2hass/CANDataReader.java");
            BufferedReader br = new BufferedReader(new InputStreamReader(
                    new FileInputStream(f), StandardCharsets.UTF_8));
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                    "\"([a-z_0-9]+)\"\\s*,\\s*\"(num|enum)\"");
            String line;
            while ((line = br.readLine()) != null) {
                // {"中文", "English name", "key", "type"},
                java.util.regex.Matcher m = p.matcher(line);
                if (m.find()) keys.add(m.group(1));
            }
            br.close();
        } catch (Exception e) {
            throw new AssertionError("failed to parse SIGNAL_REGISTRY: " + e.getMessage());
        }
        return keys;
    }

    private static List<String> keySet(JSONObject obj) {
        List<String> keys = new java.util.ArrayList<>();
        java.util.Iterator<String> it = obj.keys();
        while (it.hasNext()) keys.add(it.next());
        return keys;
    }

    private static String loadMap() throws Exception {
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
        return sb.toString();
    }
}
