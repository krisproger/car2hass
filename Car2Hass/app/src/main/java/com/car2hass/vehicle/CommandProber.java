package com.car2hass.vehicle;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Determines which channels a command can be issued on. This is a formation
 * check only (the descriptor exists in the registry) — no command is actually
 * written to the vehicle.
 */
public final class CommandProber {

    private static final List<String> CHANNELS = Arrays.asList(
            "diplus", "adb", "dumpsys", "system", "obd", "diplus_push", "byd_cloud");

    private CommandProber() {}

    public static List<String> callableBy(RegistryStore reg, String commandId) throws JSONException {
        List<String> out = new ArrayList<>();
        for (String ch : CHANNELS) {
            if (reg.commandChannel(commandId, ch) != null) out.add(ch);
        }
        return out;
    }
}
