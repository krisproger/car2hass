package com.car2hass;

public final class ProbeSchedulePolicyTest {
    public static void main(String[] args) {
        check(!ProbeSchedulePolicy.shouldFullResearch(false), "no report -> no auto full");
        check(!ProbeSchedulePolicy.shouldFullResearch(true), "report exists -> no auto full");
        System.out.println("All ProbeSchedulePolicy tests passed.");
    }
    private static void check(boolean ok, String name) { if (!ok) throw new AssertionError(name); }
}
