package com.car2hass;

/** Automatic probe scheduling: full research only when no report exists. */
public final class ProbeSchedulePolicy {
    private ProbeSchedulePolicy() {}

    public static boolean shouldFullResearch(boolean hasReport) {
        return !hasReport;
    }
}
