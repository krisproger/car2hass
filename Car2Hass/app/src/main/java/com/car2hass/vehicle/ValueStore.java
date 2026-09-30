package com.car2hass.vehicle;

import com.car2hass.LogBuffer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe latest-value store for every signal (system + channels).
 * This is the single source of truth for translated sensor values; raw values
 * (pre-scale, for command verification) are kept in a static raw map so the
 * static CANDataReader/CommandExecutor paths can reach them without a service
 * instance.
 */
public final class ValueStore {

    /** Source tag for values loaded from the previous-session cache. */
    public static final String SOURCE_RESTORED = "restored";

    /** Raw (untranslated) value per key, written by the CAN readers. */
    private static final ConcurrentHashMap<String, String> RAW_VALUES = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, String> values = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> updatedAt = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> source = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    /** Last logged priority-drop tag per key, to avoid repeating the same message. */
    private final ConcurrentHashMap<String, String> lastIgnored = new ConcurrentHashMap<>();

    /** The live store instance, set by TelemetryService so static code (command
     *  verification) can reach the translated values + listeners. */
    private static volatile ValueStore main;

    public static void setMain(ValueStore store) { main = store; }
    public static ValueStore main() { return main; }

    /** A consumer that reacts to a value change (rules, UI, snapshot, commands). */
    public interface Listener {
        void onValueChanged(String key, String value, String source);
    }

    public void put(String key, String value, String channel) {
        long now = System.currentTimeMillis();
        Long currentAt = updatedAt.get(key);
        if (currentAt != null && !ChannelPriority.shouldAccept(
                ChannelPriority.rank(source.get(key)), currentAt,
                ChannelPriority.rank(channel), now)) {
            // Log a drop only when the incoming/held combination changes, not on
            // every rejected cycle (an intermittent lower-priority writer would
            // otherwise flood the log).
            String held = source.get(key);
            String tag = channel + "<-" + held;
            if (!tag.equals(lastIgnored.put(key, tag))) {
                LogBuffer.d("ValueStore", "ignored " + key + " from " + channel
                        + " (held by " + held + ")");
            }
            return;
        }
        lastIgnored.remove(key);
        String prevValue = values.get(key);
        String prevSource = source.get(key);
        values.put(key, value);
        updatedAt.put(key, now);
        source.put(key, channel);
        // Cross-channel value flip (same key, different channel/value) — the HA
        // flicker signal; kept at debug so it is visible in field logs.
        if (prevValue != null && prevSource != null && !prevSource.equals(channel)
                && !prevValue.equals(value)) {
            LogBuffer.d("ValueStore", "flip " + key + ": " + prevValue + "(" + prevSource
                    + ") -> " + value + "(" + channel + ")");
        }
        if (!listeners.isEmpty()) {
            for (Listener l : listeners) l.onValueChanged(key, value, channel);
        }
    }

    public void addListener(Listener l) { listeners.addIfAbsent(l); }
    public void removeListener(Listener l) { listeners.remove(l); }

    public String get(String key) {
        return values.get(key);
    }

    public boolean has(String key) {
        return values.containsKey(key);
    }

    public void remove(String key) {
        values.remove(key);
        updatedAt.remove(key);
        source.remove(key);
    }

    public long ageMs(String key) {
        Long t = updatedAt.get(key);
        return t == null ? -1 : System.currentTimeMillis() - t;
    }

    /** Absolute timestamp of the last accepted write for {@code key}, 0 if none. */
    public long updatedAt(String key) {
        Long t = updatedAt.get(key);
        return t == null ? 0L : t;
    }

    public String sourceOf(String key) {
        return source.get(key);
    }

    /** True when the key's current value came only from the last-session restore. */
    public boolean isRestored(String key) {
        return SOURCE_RESTORED.equals(source.get(key));
    }

    public Map<String, String> snapshot() {
        return new HashMap<>(values);
    }

    /** Iterable view of the entries (for snapshot assembly / derived sensors). */
    public Iterable<Map.Entry<String, String>> entries() {
        return new ArrayList<>(values.entrySet());
    }

    // ── Raw values (static, for command verification) ────────────────────────

    public static void putRaw(String key, String rawValue) {
        RAW_VALUES.put(key, rawValue);
    }

    public static String getRaw(String key) {
        return RAW_VALUES.get(key);
    }

    /** Snapshot of the raw values written by the channel workers. */
    public static Map<String, String> rawSnapshot() {
        return new HashMap<>(RAW_VALUES);
    }
}
