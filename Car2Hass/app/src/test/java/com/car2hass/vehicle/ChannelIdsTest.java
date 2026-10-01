package com.car2hass.vehicle;

public class ChannelIdsTest {
    public static void main(String[] args) {
        // phone is a real channel now (was a registry-only phantom id).
        if (!ChannelIds.hasImplementation("phone"))
            throw new AssertionError("phone must have an implementation");
        for (String id : new String[]{"diplus", "adb", "native", "dumpsys", "system", "obd",
                "diplus_push", "byd_cloud", "voyah"}) {
            if (!ChannelIds.hasImplementation(id))
                throw new AssertionError(id + " must stay implemented");
        }
        if (ChannelIds.hasImplementation("bogus"))
            throw new AssertionError("unknown id must not be implemented");
        if (ChannelIds.hasImplementation(null))
            throw new AssertionError("null must not be implemented");
        System.out.println("All ChannelIds tests passed.");
    }
}
