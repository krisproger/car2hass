package com.car2hass;

/** Automatic probe scheduling: full research is manual-only. */
public final class ProbeSchedulePolicy {
    private ProbeSchedulePolicy() {}

    public static boolean shouldFullResearch(boolean hasReport) {
        return false;
    }
}
