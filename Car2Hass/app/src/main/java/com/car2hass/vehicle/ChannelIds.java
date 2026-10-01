package com.car2hass.vehicle;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Registry channel ids that have a concrete {@link DataChannel} implementation
 * (see {@link ChannelCatalog}). Ids absent here exist only in the registry and
 * must not be probed or offered as a selectable channel.
 *
 * <p>{@link ChannelCatalog#create(String)} returns {@code null} exactly for ids
 * where {@link #hasImplementation(String)} is false. Kept Android-free so the
 * plain-Java test harness can compile it.
 */
public final class ChannelIds {

    private static final Set<String> IMPLEMENTED = new HashSet<>(Arrays.asList(
            "diplus", "adb", "native", "dumpsys", "system", "obd",
            "diplus_push", "byd_cloud", "voyah", "phone"));

    public static boolean hasImplementation(String id) {
        return id != null && IMPLEMENTED.contains(id);
    }

    private ChannelIds() {}
}
