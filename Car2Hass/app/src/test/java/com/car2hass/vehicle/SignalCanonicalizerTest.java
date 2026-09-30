package com.car2hass.vehicle;

public class SignalCanonicalizerTest {
    public static void main(String[] args) {
        eq("on", SignalCanonicalizer.canonical("sidelights", "open", "adb"), "adb light open->on");
        eq("on", SignalCanonicalizer.canonical("low_beam", "on", "diplus"), "diplus on stays");
        eq("off", SignalCanonicalizer.canonical("drl", "invalid", "adb"), "adb drl invalid->off");
        eq("fresh", SignalCanonicalizer.canonical("ac_recirculation", "external", "diplus"), "diplus external->fresh");
        eq("57104", SignalCanonicalizer.canonical("range", "57104.0", "diplus"), "trailing .0 stripped");
        eq("2.5", SignalCanonicalizer.canonical("tyre_pressure_fl", "250", "adb"), "adb kPa->bar");
        eq("2.5", SignalCanonicalizer.canonical("tyre_pressure_fl", "2.50", "diplus"), "diplus bar normalized");
        eq("70", SignalCanonicalizer.canonical("soc", "70", "diplus"), "soc passthrough");
        eq("3.333", SignalCanonicalizer.canonical("cell_voltage_max", "3.333", "diplus"), "float passthrough");
        System.out.println("All SignalCanonicalizer tests passed.");
    }

    private static void eq(String want, String got, String what) {
        if (!want.equals(got)) throw new AssertionError(what + ": want=" + want + " got=" + got);
    }
}
