package com.car2hass.vehicle;

import android.content.Context;

import com.car2hass.AppConfig;
import com.car2hass.CANDataItem;
import com.car2hass.LogBuffer;
import com.car2hass.vehicle.obd.ObdSession;
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
        autoDetectAdapter(ctx);
        ObdTransport transport = ObdTransportFactory.create(ctx);
        try (ObdSession s = transport.open()) {
            String version = s.initWarmUp();
            if (version == null) {
                AppConfig.setObdStatus(ctx, "disconnected");
                AppConfig.setObdLastError(ctx, "нет ответа на init (ATZ/ATI)");
                return ChannelResult.dead(transport.describe() + " — нет ответа на init (ATZ/ATI)");
            }
            String dp = s.transact("ATDP", 1);
            AppConfig.setObdProtocol(ctx, dp != null ? dp.trim() : "");
            Set<String> supported = readSupportedPids(s);
            if (supported != null) {
                AppConfig.setObdSupportedPids(ctx,
                        new org.json.JSONArray(new ArrayList<>(supported)).toString());
            }
            AppConfig.setObdStatus(ctx, "connected");
            AppConfig.setObdLastError(ctx, "");
            return ChannelResult.rawData("ELM327 " + version + " на " + transport.describe());
        } catch (Exception e) {
            AppConfig.setObdStatus(ctx, "disconnected");
            AppConfig.setObdLastError(ctx, "bt connect failed: " + e.getMessage());
            return ChannelResult.dead(transport.describe() + " — connect: " + e.getMessage());
        }
    }

    /** Queries 0100/0120/0140 and returns the mode-01 PIDs the car reports supported. */
    private static Set<String> readSupportedPids(ObdSession s) {
        Set<String> supported = new LinkedHashSet<>();
        for (Map.Entry<String, String> e : ObdPidCodec.PID_TO_KEY.entrySet()) {
            String pid = e.getKey();
            int index = Integer.parseInt(pid.substring(2), 16) - 1; // 0-based PID index
            int rangeBase = (index / 32) * 32;
            String rangePid = String.format("01%02X", rangeBase);
            String bitmapResp = s.transact(rangePid, 1);
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

    /** Phone case: no adapter configured yet — pick a bonded ELM327-looking device. */
    private static void autoDetectAdapter(Context ctx) {
        if (!"bt".equals(AppConfig.getObdMode(ctx))) return;
        if (!AppConfig.getObdBtAddress(ctx).isEmpty()) return;
        android.bluetooth.BluetoothDevice dev =
                com.car2hass.vehicle.obd.BtSppTransport.findBondedObdAdapter();
        if (dev == null) return;
        AppConfig.setObdMode(ctx, "bt");
        AppConfig.setObdBtAddress(ctx, dev.getAddress());
        AppConfig.setObdBtName(ctx, dev.getName() != null ? dev.getName() : dev.getAddress());
        LogBuffer.i("ObdChannel", "auto-detected OBD adapter: " + dev.getName());
    }

    @Override
    public List<CANDataItem> read(Context ctx, List<CANDataItem> knownItems) {
        List<CANDataItem> out = new ArrayList<>();
        if (!AppConfig.isObdEnabled(ctx) || knownItems == null) return out;
        ObdTransport transport = ObdTransportFactory.create(ctx);
        Set<String> supported = supportedPids(ctx);
        try (ObdSession s = transport.open()) {
            s.initWarmUp(); // ensure the adapter is responsive before batch reads
            for (Map.Entry<String, String> e : ObdPidCodec.PID_TO_KEY.entrySet()) {
                String pid = e.getKey();
                if (supported != null && !supported.contains(pid)) continue;
                String key = e.getValue();
                CANDataItem item = findByKey(knownItems, key);
                if (item == null) continue;
                try {
                    String resp = s.transact(ObdPidCodec.command(pid).trim(), 1);
                    Integer value = resp != null ? ObdPidCodec.parse(pid, resp) : null;
                    if (value != null) {
                        item.value = String.valueOf(value);
                        out.add(item);
                    }
                } catch (Exception ex) {
                    LogBuffer.d("ObdChannel", "read " + pid + ": " + ex.getMessage());
                }
            }
        } catch (Exception ex) {
            LogBuffer.d("ObdChannel", "read: " + ex.getMessage());
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
        try (ObdSession s = transport.open()) {
            String resp = s.transact(ObdPidCodec.command(pid).trim(), 1);
            Integer value = resp != null ? ObdPidCodec.parse(pid, resp) : null;
            if (value != null) return ProbeResult.fromRaw(String.valueOf(value), false);
        } catch (Exception e) {
            LogBuffer.d("ObdChannel", "readSinglePid " + pid + ": " + e.getMessage());
        }
        return ProbeResult.error("ELM327 не ответил на " + pid);
    }

    private static CANDataItem findByKey(List<CANDataItem> items, String key) {
        for (CANDataItem it : items) {
            if (it != null && key.equals(it.key)) return it;
        }
        return null;
    }
}