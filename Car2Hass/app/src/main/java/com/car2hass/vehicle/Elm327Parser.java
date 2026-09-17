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

    /**
     * Extracts the VIN from a Mode 09 PID 02 response. The VIN arrives
     * multi-frame (ISO-TP); ELM327 prints the data bytes per line, optionally
     * with the {@code 49 02} header and frame-index bytes. We collect every hex
     * byte, drop the {@code 49 02} header, keep only VIN-legal ASCII
     * (0-9 A-Z) and return the first 17-char run (SAE VIN excludes I/O/Q).
     */
    public static String parseVin(String raw) {
        if (raw == null) return null;
        StringBuilder hex = new StringBuilder();
        for (String line : splitLines(raw)) {
            for (String tok : line.split("\\s+")) {
                if (tok.matches("[0-9A-F]{2}")) hex.append(tok);
            }
        }
        String h = hex.toString();
        // Mode 09 PID 02 → "49 02 ...", or UDS ReadDataByIdentifier → "62 F1 90 ...".
        int start;
        int idx = h.indexOf("4902");
        if (idx >= 0) {
            start = idx + 4;
        } else {
            idx = h.indexOf("62F190");
            if (idx < 0) return null;
            start = idx + 6;
        }
        StringBuilder ascii = new StringBuilder();
        for (int i = start; i + 1 < h.length(); i += 2) {
            int b;
            try {
                b = Integer.parseInt(h.substring(i, i + 2), 16);
            } catch (NumberFormatException e) {
                break;
            }
            if ((b >= 0x30 && b <= 0x39) || (b >= 0x41 && b <= 0x5A)) {
                ascii.append((char) b);
            }
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("[A-HJ-NPR-Z0-9]{17}").matcher(ascii.toString());
        return m.find() ? m.group() : null;
    }
}
