package com.car2hass;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds delta snapshots for the Home Assistant channel (issue #91).
 *
 * <p>Given the current full signal map, the previous sent map and the channel
 * each key belongs to, it returns either a full snapshot (first send / resync /
 * integration older than API 4) or a delta containing only changed keys plus a
 * channel→keys map so the integration can refresh per-channel last_seen.</p>
 *
 * <p>Pure logic (no Android) so it is unit-testable on the JVM.</p>
 */
public final class DeltaSnapshotBuilder {

    public static final class Result {
        /** True when {@link #signals} holds only changed keys. */
        public final boolean delta;
        /** Signals to send (all for a full snapshot, changed only for a delta). */
        public final Map<String, String> signals;
        /** channel → changed keys (empty for a full snapshot). */
        public final Map<String, List<String>> channels;
        /** New reference map to remember as lastSent. */
        public final Map<String, String> lastSent;

        Result(boolean delta, Map<String, String> signals,
               Map<String, List<String>> channels, Map<String, String> lastSent) {
            this.delta = delta;
            this.signals = signals;
            this.channels = channels;
            this.lastSent = lastSent;
        }
    }

    private DeltaSnapshotBuilder() {
    }

    /**
     * @param full       current full signal state (key → value)
     * @param lastSent   previously sent reference (may be null/empty → full)
     * @param keyChannel key → channel name (may be null → "unknown")
     * @param forceFull  force a full snapshot (resync)
     */
    public static Result build(Map<String, String> full,
                               Map<String, String> lastSent,
                               Map<String, String> keyChannel,
                               boolean forceFull) {
        Map<String, String> cur = full == null ? Collections.<String, String>emptyMap() : full;
        Map<String, String> prev = lastSent == null ? Collections.<String, String>emptyMap() : lastSent;
        boolean doFull = forceFull || prev.isEmpty();

        Map<String, String> out = new LinkedHashMap<>();
        if (doFull) {
            out.putAll(cur);
        } else {
            for (Map.Entry<String, String> e : cur.entrySet()) {
                String old = prev.get(e.getKey());
                if (!Objects.equals(old, e.getValue())) {
                    out.put(e.getKey(), e.getValue());
                }
            }
        }

        // New reference: the current full state (drops keys that disappeared).
        Map<String, String> newLastSent = new LinkedHashMap<>(cur);

        if (doFull) {
            return new Result(false, out, Collections.<String, List<String>>emptyMap(), newLastSent);
        }

        Map<String, List<String>> ch = new LinkedHashMap<>();
        for (String key : out.keySet()) {
            String channel = keyChannel == null ? null : keyChannel.get(key);
            if (channel == null || channel.isEmpty()) {
                channel = "unknown";
            }
            List<String> keys = ch.get(channel);
            if (keys == null) {
                keys = new ArrayList<>();
                ch.put(channel, keys);
            }
            keys.add(key);
        }
        return new Result(true, out, ch, newLastSent);
    }
}
