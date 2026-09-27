package com.car2hass;

/**
 * Pure formatting for the log preamble (header + diagnostics block) that
 * precedes an uploaded/exported journal. Kept free of Android dependencies so
 * the marker layout stays testable on a plain JVM.
 */
public final class LogPreamble {

    private LogPreamble() {}

    public static String header(String appVer, String deviceInfo, String timeText) {
        return "=== Car2Hass v" + appVer + " Log ===\n"
                + "Device: " + deviceInfo + "\n"
                + "Time: " + timeText + "\n"
                + "Source: All signals (merged)\n";
    }

    public static String diagnosticsBlock(String... lines) {
        StringBuilder sb = new StringBuilder("--- Diagnostics ---\n");
        if (lines != null) {
            for (String line : lines) {
                if (line == null) continue;
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }
}
