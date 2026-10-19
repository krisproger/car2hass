package com.car2hass.vehicle;

import java.util.List;
import java.util.Map;

/**
 * Whether the car-control UI should be shown: true only when at least one
 * command-capable channel is known alive in the last probe. Unknown
 * availability hides the UI (e.g. on a phone with no car bus).
 */
public final class CommandVisibility {
    private CommandVisibility() {}

    public static boolean shouldShow(Map<String, Boolean> availability, List<String> commandChannels) {
        if (availability == null || commandChannels == null) return false;
        for (String ch : commandChannels) {
            if (Boolean.TRUE.equals(availability.get(ch))) return true;
        }
        return false;
    }

    /**
     * Like {@link #shouldShow(Map, List)} but also requires that commands are
     * allowed for this vehicle (e.g. the producer is not UNIVERSAL).
     */
    public static boolean shouldShow(Map<String, Boolean> availability,
                                     List<String> commandChannels,
                                     boolean commandsAllowed) {
        return commandsAllowed && shouldShow(availability, commandChannels);
    }
}
