package com.diplustohass;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * One tile on the dashboard screen.
 */
public class DashboardTile {

    public enum Type {
        SENSOR,
        COMMAND,
        PRESET
    }

    public final Type type;
    public final String key;
    public String icon;
    /**
     * Raw preset icon name (e.g. "air-conditioner") used to look up a PNG via
     * PresetIconResolver; null/empty means the emoji in {@link #icon} is used.
     */
    public String iconName;
    public String label;
    public String value;
    public String unit;
    public String sub;
    public boolean alert;

    /**
     * Optional command parameter persisted with command tiles.
     * For commands that require a value (e.g. temperature, window position).
     */
    public String commandValue;

    // ─── Preset fields ───

    /** Reference to a preset definition in DashboardPresetRegistry. */
    public String presetId;

    /**
     * Cached preset behaviour at tile creation time so we do not have to
     * re-look it up on every UI frame.
     */
    public String behavior;
    public List<String> associatedSensorKeys;
    public boolean hasAlertToggle;

    // Dual-action zone tracking
    /** When true, the next tap dispatches the right zone action. */
    public boolean tapRightZone;

    /** True for placeholder empty cells shown while editing the dashboard. */
    public boolean isEmptyCell = false;

    public DashboardTile(String key, String icon, String label) {
        this(Type.SENSOR, key, icon, label);
    }

    public DashboardTile(Type type, String key, String icon, String label) {
        this.type = type;
        this.key = key;
        this.icon = icon;
        this.label = label;
        this.value = "—";
        this.unit = "";
        this.sub = "";
        this.alert = false;
        this.associatedSensorKeys = new ArrayList<>();
    }

    public DashboardTile(Type type, String key) {
        this.type = type;
        this.key = key;
        this.icon = "";
        this.label = key;
        this.value = "—";
        this.unit = "";
        this.sub = "";
        this.alert = false;
        this.associatedSensorKeys = new ArrayList<>();
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            if (type == Type.PRESET) {
                o.put("type", "preset");
                o.put("key", presetId != null ? presetId : key);
            } else {
                o.put("type", type == Type.SENSOR ? "sensor" : "command");
                o.put("key", key);
                if (type == Type.COMMAND && commandValue != null && !commandValue.isEmpty()) {
                    o.put("value", commandValue);
                }
            }
        } catch (Exception e) {
            // JSONObject rarely throws; ignore.
        }
        return o;
    }

    public void setValue(String value) {
        setValue(value, "");
    }

    public void setValue(String value, String unit) {
        this.value = value != null ? value : "—";
        this.unit = unit != null ? unit : "";
    }

    public void setSub(String sub) {
        this.sub = sub != null ? sub : "";
    }

    public void setAlert(boolean alert) {
        this.alert = alert;
    }

    public boolean isSensor() {
        return type == Type.SENSOR;
    }

    public boolean isCommand() {
        return type == Type.COMMAND;
    }
}
