package com.car2hass;

import android.content.Context;

/**
 * One telemetry item. When data comes from the DiPlus HTTP API, {@link #diplusName}
 * holds the Chinese signal name used for {@code /api/getVal?name=<diplusName>}.
 * {@link #key} is the stable snake_case identifier sent to Home Assistant.
 */
public class CANDataItem {
    public final int canId;
    public String diplusName;
    public final String name;
    public String key;
    public final String unit;
    public final int route;
    public String value;
    public String rawData;
    public long lastUpdate;
    public boolean enabled = true;
    /** True when DiPlus reports this signal is not supported on the current firmware. */
    public boolean unsupported = false;

    public CANDataItem(int canId, String name, String unit, int route) {
        this.canId = canId;
        this.diplusName = String.format("0x%03X", canId);
        this.name = name;
        this.key = "";
        this.unit = unit;
        this.route = route;
        this.value = "---";
        this.rawData = "";
        this.lastUpdate = 0;
    }

    public CANDataItem(int canId, String name, String unit, int route, String diplusName) {
        this.canId = canId;
        this.diplusName = diplusName;
        this.name = name;
        this.key = "";
        this.unit = unit;
        this.route = route;
        this.value = "---";
        this.rawData = "";
        this.lastUpdate = 0;
    }

    public String getDisplayName(Context ctx) {
        if (key != null && !key.isEmpty()) {
            int resId = ctx.getResources().getIdentifier("sensor_" + key, "string", ctx.getPackageName());
            if (resId != 0) {
                try {
                    return ctx.getString(resId);
                } catch (Exception ignored) {}
            }
        }
        return name + " (" + key + ")";
    }
}
