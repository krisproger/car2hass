package com.car2hass.vehicle;

import android.content.Context;
import com.car2hass.AdbShellExecutor;
import com.car2hass.AppConfig;
import com.car2hass.CANDataItem;
import com.car2hass.LogBuffer;

import java.util.List;

/** Экспериментальный канал: зондирует общедоступные ADB-команды и локальные HTTP-порты. */
public class ExperimentalChannel implements DataChannel {

    @Override
    public String id() { return "experimental"; }

    @Override
    public String displayName() { return "Экспериментальные каналы"; }

    @Override
    public boolean supportsCommands() { return false; }

    @Override
    public ChannelResult probe(Context ctx) {
        String host = AppConfig.getAdbHost(ctx);
        int port = AppConfig.getAdbPort(ctx);
        try {
            AdbShellExecutor.init(ctx);
            String out = AdbShellExecutor.executeSync(host, port, "echo probe; service list | head -5");
            if (out != null && !out.trim().isEmpty()) {
                return ChannelResult.rawData("ADB отвечает, службы: " + out.trim().replace('\n', ' '));
            }
        } catch (Exception e) {
            LogBuffer.d("VehicleResearch", "experimental ADB probe: " + e.getMessage());
            return ChannelResult.dead("ADB: " + e.getMessage());
        }
        return ChannelResult.dead("экспериментальные зонды не дали данных");
    }

    @Override
    public List<CANDataItem> read(Context ctx, List<CANDataItem> knownItems) {
        return null; // зондирование без полного сбора
    }
}