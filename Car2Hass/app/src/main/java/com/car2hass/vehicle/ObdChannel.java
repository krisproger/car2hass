package com.car2hass.vehicle;

import android.content.Context;

import com.car2hass.AppConfig;
import com.car2hass.CANDataItem;
import com.car2hass.LogBuffer;
import com.car2hass.vehicle.obd.ObdTransport;
import com.car2hass.vehicle.obd.ObdTransportFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
            return ChannelResult.dead(transport.describe() + " — адаптер не ответил на ATI");
        }
        String version = Elm327Parser.extractVersion(resp);
        if (version == null) {
            return ChannelResult.dead(transport.describe() + " — это не ELM327: "
                    + firstLine(resp));
        }
        return ChannelResult.rawData("ELM327 " + version + " на " + transport.describe());
    }

    @Override
    public List<CANDataItem> read(Context ctx, List<CANDataItem> knownItems) {
        List<CANDataItem> out = new ArrayList<>();
        if (!AppConfig.isObdEnabled(ctx) || knownItems == null) return out;
        ObdTransport transport = ObdTransportFactory.create(ctx);
        for (Map.Entry<String, String> e : ObdPidCodec.PID_TO_KEY.entrySet()) {
            String pid = e.getKey();
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