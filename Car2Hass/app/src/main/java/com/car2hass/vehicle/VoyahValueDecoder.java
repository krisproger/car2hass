package com.car2hass.vehicle;

/**
 * Brand-specific decoding of raw Voyah VehicleState values.
 *
 * <p>The shared {@code SignalTranslator} enum labels are also used by the BYD
 * paths, so values whose Voyah semantics differ from the BYD convention are
 * decoded here before leaving the Voyah channel.
 */
public final class VoyahValueDecoder {

    private VoyahValueDecoder() {}

    /** Decodes a raw Voyah value for a registry key; returns the raw value when no rule applies. */
    public static String decode(String key, String raw) {
        if (key == null || raw == null) return raw;
        switch (key) {
            case "drive_mode":
                return driveMode(raw);
            case "rear_left_door":
            case "rear_right_door":
                return doorPosition(raw);
            case "fuel_charge_flap":
                return fuelPortCap(raw);
            case "charge_port_flap":
                return chargePortCap(raw);
            case "outside_temp":
            case "cabin_temp":
            case "ambient_temp":
                return temperature(raw);
            default:
                return raw;
        }
    }

    /**
     * BCM_DRIVEMODE_CHANGE (DFVehicleState {@code dirve_mode_*}).
     * 1 eco, 2 comfort, 3 sport, 4 offroad, 5 snow, 6 custom.
     */
    static String driveMode(String raw) {
        Integer v = parseInt(raw);
        if (v == null) return raw;
        switch (v) {
            case 1: return "eco";
            case 2: return "comfort";
            case 3: return "sport";
            case 4: return "offroad";
            case 5: return "snow";
            case 6: return "custom";
            default: return raw;
        }
    }

    /**
     * DOOR_POSITION_STATUS_RL/RR (DFVehicleState {@code DOOR_POSITION_*}):
     * 0 full latch (closed), 1 half latch, 2 latch open, 3 full open.
     *
     * <p>Only the four values defined by DFVehicleState are mapped. Anything
     * else (a percent/position or a bitfield from a different firmware build)
     * is passed through untouched instead of being reported as "open" — the
     * field report was that closed doors showed as open, so a raw value outside
     * the enum must not be guessed.
     */
    static String doorPosition(String raw) {
        Integer v = parseInt(raw);
        if (v == null) return raw;
        switch (v) {
            case 0: return "closed";
            case 1:
            case 2:
            case 3: return "open";
            default: return raw;
        }
    }

    /**
     * FUEL_PORT_CAP_STS. Field feedback from a Voyah owner: the flap is reported
     * as 1 while physically closed, i.e. 0 = open and 1 = closed (inverse of the
     * shared fuel_charge_flap label). DFVehicleState carries no FUEL_PORT_CAP_*
     * enum, so the owner report is the only evidence.
     */
    static String fuelPortCap(String raw) {
        return portCap(raw, true);
    }

    /** CHRG_PORT_CAP_STS uses the regular 0 = closed / 1 = open convention. */
    static String chargePortCap(String raw) {
        return portCap(raw, false);
    }

    private static String portCap(String raw, boolean inverted) {
        Integer v = parseInt(raw);
        if (v == null) return raw;
        if (v == 0) return inverted ? "open" : "closed";
        if (v == 1) return inverted ? "closed" : "open";
        return raw;
    }

    /**
     * getAmbientTemperature()/AirCondition temperature values. The demo against
     * firmware f0506h displays the int getter directly as °C, but the firmware
     * report warns about ×10 scales. Values outside a physically possible °C
     * range (-80..80) are therefore treated as tenths of a degree.
     */
    static String temperature(String raw) {
        Double v = parseDouble(raw);
        if (v == null) return raw;
        if (Math.abs(v) > 80.0) v = v / 10.0;
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return String.valueOf(v.longValue());
        return trimZeros(String.valueOf(Math.round(v * 10.0) / 10.0));
    }

    private static String trimZeros(String s) {
        if (s.indexOf('.') < 0) return s;
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '0') end--;
        if (end > 0 && s.charAt(end - 1) == '.') end--;
        return s.substring(0, end);
    }

    private static Integer parseInt(String raw) {
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double parseDouble(String raw) {
        try {
            return Double.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
