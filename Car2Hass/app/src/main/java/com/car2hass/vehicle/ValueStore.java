package com.car2hass.vehicle;

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
        values.put(key, value);
        updatedAt.put(key, System.currentTimeMillis());
        source.put(key, channel);
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
