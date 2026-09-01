package com.car2hass.vehicle;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Standard OBD-II PID catalog and response codec. Keys must match existing
 * sensors_registry.json entries (kept in sync with OBD_PIDS in
 * scripts/gen_registry.py).
 */
public final class ObdPidCodec {

    /** pid -> registry key; order = request order. Sync with gen_registry.py. */
    public static final Map<String, String> PID_TO_KEY;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("010C", "engine_rpm");   // (A*256+B)/4 rpm
        m.put("010D", "speed");        // A km/h
        m.put("0105", "engine_coolant_temp"); // A-40 degC
        m.put("0111", "accel_pedal");  // A*100/255 %
        m.put("0104", "engine_load");  // A*100/255 %
        m.put("010F", "intake_air_temp"); // A-40 degC
        m.put("0110", "maf");          // (A*256+B)/100 g/s
        m.put("012F", "fuel_level");   // A*100/255 %
        m.put("0146", "ambient_temp"); // A-40 degC
        m.put("015C", "engine_oil_temp"); // A-40 degC
        m.put("015E", "fuel_rate");    // (A*256+B)/20 L/h
        PID_TO_KEY = Collections.unmodifiableMap(m);
    }

    private ObdPidCodec() {}

    public static String command(String pid) {
        return pid + "\r";
    }

    /** Registry key for a pid, or null. */
    public static String keyFor(String pid) {
        return PID_TO_KEY.get(pid);
    }

    /**
     * Parses a mode-01 response ("41 0D 50") into a numeric value using the
     * per-pid formula; returns null on garbage / error lines.
     */
    public static Integer parse(String pid, String rawResponse) {
        if (pid == null || rawResponse == null) return null;
        List<String> lines = Elm327Parser.splitLines(rawResponse);
        if (lines.isEmpty() || Elm327Parser.isError(lines)) return null;
        String expect = "41" + pid.substring(2);
        for (String line : lines) {
            line = line.replace(" ", "");
            if (!line.startsWith(expect)) continue;
            int[] data = hexBytes(line.substring(4));
            if (data.length == 0) return null;
            return applyFormula(pid, data);
        }
        return null;
    }

    private static Integer applyFormula(String pid, int[] d) {
        switch (pid) {
            case "010C":
                if (d.length < 2) return null;
                return (d[0] * 256 + d[1]) / 4;
            case "010D":
                return d[0];
            case "0105":
                return d[0] - 40;
            case "0111":
                return d[0] * 100 / 255;
            case "0104":
                return d[0] * 100 / 255;
            case "010F":
                return d[0] - 40;
            case "0110":
                if (d.length < 2) return null;
                return (d[0] * 256 + d[1]) / 100;
            case "012F":
                return d[0] * 100 / 255;
            case "0146":
                return d[0] - 40;
            case "015C":
                return d[0] - 40;
            case "015E":
                if (d.length < 2) return null;
                return (d[0] * 256 + d[1]) / 20;
            default:
                return null;
        }
    }

    private static int[] hexBytes(String hex) {
        if (hex.length() % 2 != 0) hex = hex.substring(0, hex.length() - 1);
        int[] out = new int[hex.length() / 2];
        try {
            for (int i = 0; i < out.length; i++) {
                out[i] = Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
            }
        } catch (NumberFormatException e) {
            return new int[0];
        }
        return out;
    }

    static List<String> pids() {
        return Arrays.asList(PID_TO_KEY.keySet().toArray(new String[0]));
    }
}
