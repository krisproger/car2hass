package com.car2hass.vehicle;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Picks the brand family (profiles sharing a live key channel). */
public final class BrandSelector {

    private BrandSelector() {}

    /**
     * Profiles of the brand detected via a live key channel. Empty when the
     * brand is undetermined (no key channel alive, or several different ones).
     */
    public static List<String> detect(RegistryStore reg,
                                      Map<String, Boolean> channelAvailability) {
        List<String> out = new ArrayList<>();
        try {
            List<String> profileIds = reg.profileIds();
            String brandChannel = null;
            for (String pid : profileIds) {
                String kc = reg.profileKeyChannel(pid);
                if (kc == null) continue;
                Boolean alive = channelAvailability.get(kc);
                if (alive != null && alive) {
                    if (brandChannel == null) {
                        brandChannel = kc;
                    } else if (!brandChannel.equals(kc)) {
                        return out; // ambiguous — fall back to general scoring
                    }
                }
            }
            if (brandChannel == null) return out;
            for (String pid : profileIds) {
                if (brandChannel.equals(reg.profileKeyChannel(pid))) out.add(pid);
            }
        } catch (Exception e) {
            return out;
        }
        return out;
    }
}