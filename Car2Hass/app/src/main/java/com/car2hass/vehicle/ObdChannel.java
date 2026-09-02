package com.car2hass.vehicle;

import android.content.Context;

import com.car2hass.AppConfig;
import com.car2hass.CANDataItem;
import com.car2hass.LogBuffer;
import com.car2hass.vehicle.obd.ObdTransport;
import com.car2hass.vehicle.obd.ObdTransportFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * OBD-II channel. Primary transport is Bluetooth SPP (default), TCP/WiFi
 * remains as a fallback; the active one comes from ObdTransportFactory.
 */
public class ObdChannel implements DataChannel {

    @Override
    public String id() { return "obd"; }

    @Override
    public String displayName() { return "OBD-II (ELM327)"; }

    @Override
    public boolean supportsCommands() { return false; }

    @Override
    public ChannelResult probe(Context ctx) {
        if (!AppConfig.isObdEnabled(ctx)) {
            return ChannelResult.dead("отключён пользователем");
        }
        ObdTransport transport = ObdTransportFactory.create(ctx);
        String resp = transport.transact("ATI", 1);
        if (resp == null) {
            AppConfig.setObdStatus(ctx, "disconnected");
            AppConfig.setObdLastError(ctx, "адаптер не ответил на ATI");
            return ChannelResult.dead(transport.describe() + " — адаптер не ответил на ATI");
        }
        String version = Elm327Parser.extractVersion(resp);
        if (version == null) {
            AppConfig.setObdStatus(ctx, "disconnected");
            AppConfig.setObdLastError(ctx, "не ELM327");
            return ChannelResult.dead(transport.describe() + " — это не ELM327: "
                    + firstLine(resp));
        }
        // Auto-connect + protocol analysis (ELM327 init → ATDP → supported-PID bitmap).
        analyzeProtocol(ctx, transport);
        AppConfig.setObdStatus(ctx, "connected");
        AppConfig.setObdLastError(ctx, "");
        return ChannelResult.rawData("ELM327 " + version + " на " + transport.describe());
    }

    /**
     * ELM327 initialization + protocol detection + supported-PID bitmap.
     * Mirrors Car Scanner's approach but runs automatically at connect (no button).
     */
    private static void analyzeProtocol(Context ctx, ObdTransport transport) {
        try {
            // Init sequence; per-transaction sockets so a short pause keeps the
            // adapter stable between AT commands.
            transport.transact("ATZ", 1);
            Thread.sleep(300);
            transport.transact("ATE0", 0);
            transport.transact("ATL0", 0);
            transport.transact("ATH0", 0);
            transport.transact("ATSP0", 0);
            String dp = transport.transact("ATDP", 1);
            AppConfig.setObdProtocol(ctx, dp != null ? dp.trim() : "");
        } catch (Exception e) {
            LogBuffer.d("ObdChannel", "init: " + e.getMessage());
        }
        try {
            Set<String> supported = readSupportedPids(transport);
            if (supported != null) {
                AppConfig.setObdSupportedPids(ctx,
                        new org.json.JSONArray(new ArrayList<>(supported)).toString());
            }
        } catch (Exception e) {
            LogBuffer.d("ObdChannel", "pid bitmap: " + e.getMessage());
        }
    }

    /** Queries 0100/0120/0140 and returns the mode-01 PIDs the car reports supported. */
    private static Set<String> readSupportedPids(ObdTransport transport) {
        Set<String> supported = new LinkedHashSet<>();
        for (Map.Entry<String, String> e : ObdPidCodec.PID_TO_KEY.entrySet()) {
            String pid = e.getKey();
            int index = Integer.parseInt(pid.substring(2), 16) - 1; // 0-based PID index
            int rangeBase = (index / 32) * 32;
            String rangePid = String.format("01%02X", rangeBase);
            String bitmapResp = transport.transact(rangePid, 1);
            int[] bitmap = ObdPidCodec.responseData(bitmapResp);
            if (bitmap.length == 0) return null; // can't determine → read all
            if (ObdPidCodec.bitmapSupports(bitmap, index % 32)) supported.add(pid);
        }
        return supported;
    }

    /** Supported mode-01 PIDs from prefs, or null (read everything). */
    private static Set<String> supportedPids(Context ctx) {
        String json = AppConfig.getObdSupportedPids(ctx);
        if (json == null || json.isEmpty()) return null;
        try {
            org.json.JSONArray arr = new org.json.JSONArray(json);
            Set<String> out = new LinkedHashSet<>();
            for (int i = 0; i < arr.length(); i++) out.add(arr.getString(i));
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public List<CANDataItem> read(Context ctx, List<CANDataItem> knownItems) {
        List<CANDataItem> out = new ArrayList<>();
        if (!AppConfig.isObdEnabled(ctx) || knownItems == null) return out;
        ObdTransport transport = ObdTransportFactory.create(ctx);
        Set<String> supported = supportedPids(ctx);
        for (Map.Entry<String, String> e : ObdPidCodec.PID_TO_KEY.entrySet()) {
            String pid = e.getKey();
            if (supported != null && !supported.contains(pid)) continue;
            String key = e.getValue();
            CANDataItem item = findByKey(knownItems, key);
            if (item == null) continue;
            try {
                String resp = transport.transact(ObdPidCodec.command(pid).trim(), 1);
                Integer value = resp != null ? ObdPidCodec.parse(pid, resp) : null;
                if (value != null) {
                    item.value = String.valueOf(value);
                    out.add(item);
                }
            } catch (Exception ex) {
                LogBuffer.d("ObdChannel", "read " + pid + ": " + ex.getMessage());
            }
        }
        // Link state reflects the last exchange on this cycle.
        AppConfig.setObdStatus(ctx, out.isEmpty() && !knownItems.isEmpty()
                ? "disconnected" : "connected");
        return out;
    }

    /** Single-PID probe for the research engine; ok/error based on ELM327 answer. */
    public static ProbeResult readSinglePid(Context ctx, String pid) {
        if (!AppConfig.isObdEnabled(ctx)) return ProbeResult.unsupported();
        ObdTransport transport = ObdTransportFactory.create(ctx);
        String resp = transport.transact(ObdPidCodec.command(pid).trim(), 1);
        Integer value = resp != null ? ObdPidCodec.parse(pid, resp) : null;
        if (value != null) return ProbeResult.fromRaw(String.valueOf(value), false);
        return ProbeResult.error("ELM327 не ответил на " + pid);
    }

    private static String firstLine(String raw) {
        List<String> lines = Elm327Parser.splitLines(raw);
        return lines.isEmpty() ? "?" : lines.get(0);
    }

    private static CANDataItem findByKey(List<CANDataItem> items, String key) {
        for (CANDataItem it : items) {
            if (it != null && key.equals(it.key)) return it;
        }
        return null;
    }
}