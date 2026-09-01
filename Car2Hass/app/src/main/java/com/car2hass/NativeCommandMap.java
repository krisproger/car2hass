package com.car2hass;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Command map for the native write channel: stable HA command id → autoservice
 * setInt address(es) + optional verify sensor + DiPlus Chinese template.
 *
 * <p>Backed by {@code assets/native_commands.json}. A single command resolves to
 * one write op ({@code value} or parametric {@code valueExpr} with clamping), to
 * a fixed fan-out list (e.g. all four windows), or to a per-value map (sunroof
 * position enums, seat switch+level pairs). Resolving with a value that has no
 * native path returns null — the caller falls back to the DiPlus interface.
 */
public final class NativeCommandMap {

    private static final String ASSET_NAME = "native_commands.json";
    private static final String TAG = "NativeCmdMap";

    /** One autoservice write: dev/fid/value for {@code service call autoservice 6}. */
    public static final class WriteOp {
        public final int device;
        public final int fid;
        public final int value;

        WriteOp(int device, int fid, int value) {
            this.device = device;
            this.fid = fid;
            this.value = value;
        }
    }

    private static final class CommandEntry {
        final boolean hasNative;
        final String verifySensor;
        final String diplusChinese;
        final List<WriteOp> fixedWrites;
        final int exprDev;
        final int exprFid;
        final int min;
        final int max;
        final Map<String, List<WriteOp>> valueMap;

        CommandEntry(boolean hasNative, String verifySensor, String diplusChinese,
                     List<WriteOp> fixedWrites, int exprDev, int exprFid,
                     int min, int max, Map<String, List<WriteOp>> valueMap) {
            this.hasNative = hasNative;
            this.verifySensor = verifySensor;
            this.diplusChinese = diplusChinese;
            this.fixedWrites = fixedWrites;
            this.exprDev = exprDev;
            this.exprFid = exprFid;
            this.min = min;
            this.max = max;
            this.valueMap = valueMap;
        }
    }

    private static final Map<String, CommandEntry> ENTRIES = new LinkedHashMap<>();
    private static boolean loaded = false;

    private NativeCommandMap() {}

    /** Loads the command map from the bundled asset. Safe to call repeatedly. */
    public static synchronized void load(Context ctx) {
        if (loaded || ctx == null) {
            return;
        }
        try (InputStream is = ctx.getAssets().open(ASSET_NAME);
             BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
            parse(sb.toString());
            loaded = true;
            LogBuffer.i(TAG, "Loaded " + ENTRIES.size() + " command entries from assets");
        } catch (Exception e) {
            LogBuffer.e(TAG, "Failed to load " + ASSET_NAME + ": " + e.getMessage());
        }
    }

    /**
     * Parses a map JSON payload. Public so plain-Java tests can load the asset
     * by relative path without an Android runtime.
     */
    public static synchronized void parse(String json) {
        ENTRIES.clear();
        try {
            JSONObject root = new JSONObject(json);
            JSONObject commands = root.optJSONObject("commands");
            if (commands == null) {
                LogBuffer.w(TAG, "native_commands.json has no \"commands\" object");
                return;
            }
            for (String id : keySet(commands)) {
                JSONObject cmd = commands.optJSONObject(id);
                if (cmd == null) {
                    continue;
                }
                ENTRIES.put(id, parseEntry(id, cmd));
            }
        } catch (Exception e) {
            LogBuffer.e(TAG, "Failed to parse native command map: " + e.getMessage());
        }
    }

    private static CommandEntry parseEntry(String id, JSONObject cmd) {
        JSONObject nativeObj = cmd.optJSONObject("native");
        JSONArray nativeArr = cmd.optJSONArray("native");

        boolean hasNative = nativeObj != null || nativeArr != null;
        String verify = cmd.optString("verify", null);
        JSONObject diplus = cmd.optJSONObject("diplus");
        String chinese = diplus != null ? diplus.optString("chinese", null) : null;

        if (!hasNative) {
            return new CommandEntry(false, verify, chinese,
                    null, 0, 0, 0, 0, null);
        }

        if (nativeArr != null) {
            return new CommandEntry(true, verify, chinese,
                    parseFanOut(nativeArr), 0, 0, 0, 0, null);
        }

        if (verify == null) {
            verify = nativeObj.optString("verify", null);
        }
        int dev = nativeObj.optInt("dev", 0);
        int fid = nativeObj.optInt("fid", 0);

        if (nativeObj.has("valueExpr")) {
            int min = nativeObj.optInt("min", 0);
            int max = nativeObj.optInt("max", 0);
            return new CommandEntry(true, verify, chinese,
                    null, dev, fid, min, max, null);
        }

        JSONObject valueMapObj = nativeObj.optJSONObject("valueMap");
        if (valueMapObj != null) {
            Map<String, List<WriteOp>> valueMap = new LinkedHashMap<>();
            for (String key : keySet(valueMapObj)) {
                int mapValue = valueMapObj.optInt(key, Integer.MIN_VALUE);
                if (valueMapObj.has(key) && mapValue != Integer.MIN_VALUE) {
                    List<WriteOp> single = new ArrayList<>(1);
                    single.add(new WriteOp(dev, fid, mapValue));
                    valueMap.put(key, single);
                    continue;
                }
                JSONArray arr = valueMapObj.optJSONArray(key);
                if (arr != null) {
                    valueMap.put(key, parseFanOut(arr));
                }
            }
            return new CommandEntry(true, verify, chinese,
                    null, 0, 0, 0, 0, valueMap);
        }

        List<WriteOp> fixed = new ArrayList<>(1);
        fixed.add(new WriteOp(dev, fid, nativeObj.optInt("value", 0)));
        return new CommandEntry(true, verify, chinese,
                fixed, 0, 0, 0, 0, null);
    }

    private static List<WriteOp> parseFanOut(JSONArray arr) {
        List<WriteOp> ops = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) {
                continue;
            }
            ops.add(new WriteOp(o.optInt("dev", 0), o.optInt("fid", 0), o.optInt("value", 0)));
        }
        return ops;
    }

    /** Whether the command has a native write path in the map. */
    public static boolean hasNative(String commandId) {
        CommandEntry e = ENTRIES.get(commandId);
        return e != null && e.hasNative;
    }

    /**
     * Resolves the command to native write ops for a given value.
     *
     * @return ordered write ops, or null when the command has no native path or
     *         the value does not map to one (caller falls back to DiPlus)
     */
    public static List<WriteOp> resolve(String commandId, String value) {
        CommandEntry e = ENTRIES.get(commandId);
        if (e == null || !e.hasNative) {
            return null;
        }
        if (e.fixedWrites != null) {
            return e.fixedWrites;
        }
        if (e.valueMap != null) {
            if (value == null || value.isEmpty()) {
                return null;
            }
            return e.valueMap.get(value);
        }
        if (e.exprFid != 0 || e.exprDev != 0) {
            if (value == null || value.trim().isEmpty()) {
                return null;
            }
            int v;
            try {
                v = (int) Math.floor(Double.parseDouble(value.trim().replace(',', '.')));
            } catch (NumberFormatException ex) {
                return null;
            }
            v = Math.max(e.min, Math.min(e.max, v));
            List<WriteOp> ops = new ArrayList<>(1);
            ops.add(new WriteOp(e.exprDev, e.exprFid, v));
            return ops;
        }
        return null;
    }

    /** Whether the command has a native fan-out (multiple write ops). */
    public static boolean isFanOut(String commandId) {
        CommandEntry e = ENTRIES.get(commandId);
        if (e == null) {
            return false;
        }
        if (e.fixedWrites != null) {
            return e.fixedWrites.size() > 1;
        }
        if (e.valueMap != null) {
            for (List<WriteOp> ops : e.valueMap.values()) {
                if (ops.size() > 1) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * All distinct (dev,fid) targets of a command across every value path.
     * Used by the banned-dev invariant test and live-command logging.
     */
    public static java.util.Set<String> allNativeTargets(String commandId) {
        java.util.Set<String> targets = new java.util.HashSet<>();
        CommandEntry e = ENTRIES.get(commandId);
        if (e == null) {
            return targets;
        }
        addTargets(targets, e.fixedWrites);
        if (e.valueMap != null) {
            for (List<WriteOp> ops : e.valueMap.values()) {
                addTargets(targets, ops);
            }
        }
        if (e.exprFid != 0 || e.exprDev != 0) {
            targets.add(e.exprDev + ":" + e.exprFid);
        }
        return targets;
    }

    private static void addTargets(java.util.Set<String> targets, List<WriteOp> ops) {
        if (ops == null) {
            return;
        }
        for (WriteOp op : ops) {
            targets.add(op.device + ":" + op.fid);
        }
    }

    /** Verify sensor key for the command, or null. */
    public static String getVerifySensor(String commandId) {
        CommandEntry e = ENTRIES.get(commandId);
        return e != null ? e.verifySensor : null;
    }

    /** DiPlus Chinese template for the command (with {{value}}), or null. */
    public static String getDiplusChinese(String commandId) {
        CommandEntry e = ENTRIES.get(commandId);
        return e != null ? e.diplusChinese : null;
    }

    /** All command ids present in the map (for tests / live-command logging). */
    public static List<String> allCommandIds() {
        return Collections.unmodifiableList(new ArrayList<>(ENTRIES.keySet()));
    }

    private static List<String> keySet(JSONObject obj) {
        List<String> keys = new ArrayList<>();
        java.util.Iterator<String> it = obj.keys();
        while (it.hasNext()) {
            keys.add(it.next());
        }
        return keys;
    }
}
