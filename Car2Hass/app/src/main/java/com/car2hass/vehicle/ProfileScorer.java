package com.car2hass.vehicle;

import org.json.JSONException;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scores car profiles by how many of their non-generic expected sensors
 * returned a real value. Selection uses ok²/(ok+fail): expected-but-failed
 * sensors directly lower the score, and generic sensors (readable on any car)
 * are ignored. Brand is chosen by channel elsewhere (BrandSelector).
 */
public final class ProfileScorer {

    private final RegistryStore reg;
    private final Map<String, Set<String>> sensorOkByChannel;

    public ProfileScorer(RegistryStore reg, Map<String, Set<String>> sensorOkByChannel) {
        this.reg = reg;
        this.sensorOkByChannel = sensorOkByChannel;
    }

    /** Ok count over the profile's non-generic expected sensors. */
    public int score(String profileId) throws JSONException {
        int s = 0;
        for (String key : reg.profileNonGenericSensors(profileId)) {
            Set<String> okCh = sensorOkByChannel.get(key);
            if (okCh != null && !okCh.isEmpty()) s++;
        }
        return s;
    }

    /** Expected non-generic sensors that failed to read in any channel. */
    public int failCount(String profileId) throws JSONException {
        int f = 0;
        for (String key : reg.profileNonGenericSensors(profileId)) {
            Set<String> okCh = sensorOkByChannel.get(key);
            if (okCh == null || okCh.isEmpty()) f++;
        }
        return f;
    }

    /** ok²/(ok+fail): rewards completeness, penalizes expected-but-failed. */
    private double composite(String profileId) throws JSONException {
        int ok = score(profileId);
        int total = ok + failCount(profileId);
        return total > 0 ? (ok * ok) / (double) total : 0;
    }

    /** Best profile within a brand family (channel-detected list). */
    public String selectBestForBrand(List<String> profileIds) throws JSONException {
        String best = null;
        double bestScore = -1;
        int bestSize = Integer.MAX_VALUE;
        for (String pid : profileIds) {
            double m = composite(pid);
            int size = reg.profileNonGenericSensors(pid).size();
            if (m > bestScore || (m == bestScore && size < bestSize)) {
                best = pid;
                bestScore = m;
                bestSize = size;
            }
        }
        return best;
    }

    /** Best over all profiles; fallback when no brand channel is alive. */
    public String selectBest() throws JSONException {
        return selectBestForBrand(reg.profileIds());
    }
}