package com.diplustohass;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Orchestrates a native (DiPlus-free) telemetry read: builds one ADB shell
 * command for all mapped signals, runs it, parses the output, decodes every
 * value and applies the post-processing rules.
 *
 * <p>Pure Java apart from {@link CANDataItem} and {@link LogBuffer}: the shell
 * call is delegated to a {@link ShellRunner} so unit tests can stub the ADB
 * transport (the production runner wires up {@link AdbShellExecutor#executeSync}).
 */
public class NativeReader {

    /** Executes one shell command on the ADB daemon, returns full output or null. */
    public interface ShellRunner {
        String run(String host, int port, String command);
    }

    private static final String TAG = "CANReader";
    private static final String GEN3_SUFFIX = "_gen3";

    private final ShellRunner runner;
    private final String host;
    private final int port;
    private final boolean debug;

    private int lastOk = 0;

    public NativeReader(ShellRunner runner, String host, int port, boolean debug) {
        this.runner = runner;
        this.host = host;
        this.port = port;
        this.debug = debug;
    }

    /** Number of signals successfully decoded in the last {@link #readAll} call. */
    public int getLastOk() {
        return lastOk;
    }

    /**
     * Reads every native-mapped signal in one ADB session and updates the
     * matching {@code knownItems} in place.
     *
     * @param knownItems the current signal items (keyed by {@link CANDataItem#key})
     * @return the updated items; signals without a native fid are left untouched
     */
    public List<CANDataItem> readAll(List<CANDataItem> knownItems) {
        lastOk = 0;
        List<NativeSignalMap.FidEntry> batch = buildBatch();
        if (batch.isEmpty()) {
            LogBuffer.w(TAG, "Native: no signals in the map");
            return knownItems;
        }

        String command = NativeCommandBuilder.build(batch);
        long t0 = System.currentTimeMillis();
        String output = runner.run(host, port, command);
        if (output == null) {
            LogBuffer.w(TAG, "Native: ADB exec failed or timed out");
            return knownItems;
        }
        LogBuffer.d(TAG, "Native: exec " + batch.size() + " fids in "
                + (System.currentTimeMillis() - t0) + " ms, "
                + output.length() + " chars of output");

        Map<String, Long> raw = NativeOutputParser.parse(output);
        int sentinel = 0;
        int ok = 0;

        Map<String, CANDataItem> byKey = new LinkedHashMap<>();
        for (CANDataItem item : knownItems) {
            if (item.key != null) {
                byKey.put(item.key, item);
            }
        }

        for (NativeSignalMap.FidEntry e : batch) {
            String key = e.key;
            Long value = raw.get(key);
            if (value == null) {
                LogBuffer.d(TAG, "Native: no value for " + key);
                continue;
            }
            Object decoded = decode(e, value.intValue());
            if (decoded == null) {
                sentinel++;
                LogBuffer.d(TAG, "Native: sentinel for " + key + " raw=0x"
                        + String.format(Locale.US, "%08x", value));
                continue;
            }
            ok++;
            applyTo(byKey, key, decoded);
        }

        postProcessWindowRR(raw, byKey);
        postProcessTurnSignal(raw, byKey);

        LogBuffer.i(TAG, "Native: " + batch.size() + " signals via ADB, parsed "
                + raw.size() + ", sentinel " + sentinel + ", ok " + ok);
        lastOk = ok;
        return knownItems;
    }

    /** Batch = every primary entry plus the windowRR Gen3 fallback entry. */
    private List<NativeSignalMap.FidEntry> buildBatch() {
        List<NativeSignalMap.FidEntry> batch = new ArrayList<>(NativeSignalMap.allEntries());
        for (NativeSignalMap.FidEntry primary : NativeSignalMap.allEntries()) {
            NativeSignalMap.FidEntry fallback = NativeSignalMap.getFallbackFid(primary.key);
            if (fallback == null) {
                continue;
            }
            batch.add(new NativeSignalMap.FidEntry(primary.key + GEN3_SUFFIX,
                    fallback.device, fallback.fid, fallback.transact, fallback.decoder, fallback.scale));
        }
        return batch;
    }

    /** Decodes a raw 32-bit value with the entry's decoder; null on sentinel. */
    private Object decode(NativeSignalMap.FidEntry e, int rawValue) {
        if (e.transact == 7 || e.decoder == ParamDecoder.FLOAT_VOLT
                || e.decoder == ParamDecoder.FLOAT_PERCENT
                || e.decoder == ParamDecoder.FLOAT_KW
                || e.decoder == ParamDecoder.FLOAT_KWH) {
            Double d = ParamDecoder.decodeFloat(rawValue, e.decoder);
            return d == null ? null : (Object) d;
        }
        if (e.decoder == ParamDecoder.INT_SCALED) {
            return ParamDecoder.decodeScaled(rawValue, e.scale);
        }
        return ParamDecoder.decodeInt(rawValue, e.decoder);
    }

    private void applyTo(Map<String, CANDataItem> byKey, String key, Object decoded) {
        CANDataItem item = byKey.get(key);
        if (item == null) {
            return;
        }
        item.value = format(decoded);
        item.lastUpdate = System.currentTimeMillis();
        if (debug) {
            LogBuffer.d(TAG, "Native: " + key + " -> " + item.value);
        }
    }

    private void postProcessWindowRR(Map<String, Long> raw, Map<String, CANDataItem> byKey) {
        CANDataItem primary = byKey.get("window_rr");
        if (primary == null) {
            return;
        }
        // Primary fid returned a link error (sentinel → value left untouched):
        // fall back to the Gen3 fid from the same batch.
        if (!"---".equals(primary.value)) {
            return;
        }
        Long gen3 = raw.get("window_rr" + GEN3_SUFFIX);
        if (gen3 == null) {
            return;
        }
        Object decoded = decode(new NativeSignalMap.FidEntry("window_rr",
                1001, 1267728408, 5, ParamDecoder.INT_PERCENT, 1.0), gen3.intValue());
        if (decoded == null) {
            return;
        }
        applyTo(byKey, "window_rr", decoded);
        LogBuffer.d(TAG, "Native: window_rr fallback gen3 -> " + primary.value);
    }

    private void postProcessTurnSignal(Map<String, Long> raw, Map<String, CANDataItem> byKey) {
        Long mask = raw.get("turn_signal");
        if (mask == null) {
            return;
        }
        Map<String, Integer> derived = NativeSignalMap.deriveTurnValues(mask.intValue());
        for (Map.Entry<String, Integer> entry : derived.entrySet()) {
            CANDataItem item = byKey.get(entry.getKey());
            if (item != null) {
                item.value = String.valueOf(entry.getValue());
                item.lastUpdate = System.currentTimeMillis();
            }
        }
    }

    /** Formats a decoded number: integer values without ".0", else 1 decimal. */
    private static String format(Object decoded) {
        if (decoded instanceof Double) {
            double d = (Double) decoded;
            if (d == Math.rint(d) && Math.abs(d) < 1e15) {
                return String.valueOf((long) d);
            }
            return String.format(Locale.US, "%.1f", d);
        }
        return String.valueOf(decoded);
    }
}
