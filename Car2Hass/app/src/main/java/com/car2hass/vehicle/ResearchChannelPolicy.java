package com.car2hass.vehicle;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Channel selection rules for automatic and manual research. */
public final class ResearchChannelPolicy {
    private ResearchChannelPolicy() {}

    /**
     * Automatic research: never probe Voyah on a non-Voyah profile, and skip
     * channels known to be dead from the last report (fail-open when unknown).
     */
    public static boolean allowAuto(String id, String selectedProfile,
                                    Map<String, Boolean> availability) {
        if (id == null) return false;
        if ("voyah".equals(id)
                && selectedProfile != null
                && !"voyah_generic".equals(selectedProfile)) return false;
        if (availability != null && Boolean.FALSE.equals(availability.get(id))) return false;
        return true;
    }

    public static List<DataChannel> filterAuto(List<DataChannel> all, String selectedProfile,
                                               Map<String, Boolean> availability) {
        List<DataChannel> out = new ArrayList<>();
        if (all == null) return out;
        for (DataChannel ch : all) {
            if (ch != null && allowAuto(ch.id(), selectedProfile, availability)) out.add(ch);
        }
        return out;
    }

    /** Manual research always probes every channel (user explicitly asked for it). */
    public static List<DataChannel> manual(List<DataChannel> all) {
        return all == null ? new ArrayList<>() : new ArrayList<>(all);
    }
}
