package com.car2hass.vehicle;

import java.util.ArrayList;
import java.util.List;

/** Pure parser for ELM327 adapter responses (testable without Android). */
public final class Elm327Parser {

    private Elm327Parser() {}

    /** Normalizes raw adapter output into clean uppercase lines without prompts. */
    public static List<String> splitLines(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) return out;
        for (String line : raw.replace('\r', '\n').replace('>', '\n').split("\n")) {
            String t = line.trim().toUpperCase();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    /** True when an ATI response identifies an ELM-compatible adapter. */
    public static boolean isAtiResponse(String raw) {
        return extractVersion(raw) != null;
    }

    /** Extracts e.g. "V1.5" from "ELM327 v1.5", null when not an ELM response. */
    public static String extractVersion(String raw) {
        for (String line : splitLines(raw)) {
            if (line.startsWith("ELM")) {
                int i = line.indexOf('V');
                if (i >= 0 && i + 1 < line.length()) return line.substring(i);
                return "ELM";
            }
        }
        return null;
    }

    /** True when the adapter reported an error token for a request. */
    public static boolean isError(List<String> lines) {
        for (String l : lines) {
            if (l.contains("NO DATA") || l.contains("CAN ERROR")
                    || l.contains("UNABLE TO CONNECT") || l.contains("BUS INIT")) {
                return true;
            }
        }
        return false;
    }
}
