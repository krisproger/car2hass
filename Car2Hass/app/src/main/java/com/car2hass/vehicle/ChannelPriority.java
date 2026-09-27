package com.car2hass.vehicle;

import java.util.Arrays;
import java.util.List;

/**
 * Merge priority for one value per sensor when several channel workers write the
 * same key. Highest first; anything not listed (including {@code restored} and
 * the generic {@code channel}) ranks below every listed channel.
 *
 * <p>The registry's {@code channels_priority} block (see {@link RegistryStore})
 * encodes a different thing — the order in which a read cycle polls channels —
 * and does not match the merge order required here, so the merge order is kept
 * as this explicit constant.
 */
public final class ChannelPriority {

    /** Highest first. */
    public static final List<String> ORDER = Arrays.asList(
            "adb", "system", "dumpsys", "diplus", "voyah", "obd", "byd_cloud");

    /** Rank returned for channels absent from {@link #ORDER}. */
    public static final int UNKNOWN_RANK = ORDER.size();

    public static int rank(String channel) {
        int i = ORDER.indexOf(channel);
        return i >= 0 ? i : UNKNOWN_RANK;
    }

    /** Lower rank wins; on equal rank the newer timestamp wins. */
    public static boolean shouldAccept(int currentRank, long currentAtMs,
                                       int incomingRank, long incomingAtMs) {
        if (incomingRank < currentRank) return true;
        if (incomingRank > currentRank) return false;
        return incomingAtMs >= currentAtMs;
    }

    private ChannelPriority() {
    }
}
