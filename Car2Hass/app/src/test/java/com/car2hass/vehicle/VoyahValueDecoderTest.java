package com.car2hass.vehicle;

public class VoyahValueDecoderTest {
    private static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }

    private static void eq(String actual, String expected, String msg) {
        check(expected.equals(actual), msg + ": expected '" + expected + "' got '" + actual + "'");
    }

    public static void main(String[] args) {
        // BCM_DRIVEMODE_CHANGE (dirve_mode_*).
        eq(VoyahValueDecoder.decode("drive_mode", "1"), "eco", "drive 1");
        eq(VoyahValueDecoder.decode("drive_mode", "2"), "comfort", "drive 2");
        eq(VoyahValueDecoder.decode("drive_mode", "3"), "sport", "drive 3");
        eq(VoyahValueDecoder.decode("drive_mode", "4"), "offroad", "drive 4");
        eq(VoyahValueDecoder.decode("drive_mode", "5"), "snow", "drive 5");
        eq(VoyahValueDecoder.decode("drive_mode", "6"), "custom", "drive 6");
        eq(VoyahValueDecoder.decode("drive_mode", "0"), "0", "drive unknown passthrough");
        eq(VoyahValueDecoder.decode("drive_mode", "-"), "-", "drive non-numeric passthrough");

        // DOOR_POSITION_*: only the four enum values are mapped; out-of-range
        // raw values (percent/bitfield from other firmware) pass through so a
        // closed door is never reported as open.
        eq(VoyahValueDecoder.decode("rear_left_door", "0"), "closed", "door RL closed");
        eq(VoyahValueDecoder.decode("rear_left_door", "1"), "open", "door RL half latch");
        eq(VoyahValueDecoder.decode("rear_left_door", "2"), "open", "door RL latch open");
        eq(VoyahValueDecoder.decode("rear_left_door", "3"), "open", "door RL full open");
        eq(VoyahValueDecoder.decode("rear_right_door", "3"), "open", "door RR full open");
        eq(VoyahValueDecoder.decode("rear_left_door", "4"), "4", "door RL unknown enum passthrough");
        eq(VoyahValueDecoder.decode("rear_right_door", "100"), "100", "door RR percent passthrough");
        eq(VoyahValueDecoder.decode("rear_right_door", "255"), "255", "door RR bitfield passthrough");

        // Temperature getters: °C passthrough, tenths normalised above 80.
        eq(VoyahValueDecoder.decode("outside_temp", "22"), "22", "outside temp whole degrees");
        eq(VoyahValueDecoder.decode("outside_temp", "-5"), "-5", "outside temp negative");
        eq(VoyahValueDecoder.decode("outside_temp", "225"), "22.5", "outside temp tenths");
        eq(VoyahValueDecoder.decode("cabin_temp", "-105"), "-10.5", "cabin temp tenths negative");
        eq(VoyahValueDecoder.decode("cabin_temp", "80"), "80", "cabin temp boundary stays");
        eq(VoyahValueDecoder.decode("ambient_temp", "221"), "22.1", "ambient temp tenths");
        eq(VoyahValueDecoder.decode("outside_temp", "---"), "---", "temp non-numeric passthrough");

        // Fuel flap: Voyah reports 1 while (physically) closed -> inverted.
        eq(VoyahValueDecoder.decode("fuel_charge_flap", "0"), "open", "fuel flap open");
        eq(VoyahValueDecoder.decode("fuel_charge_flap", "1"), "closed", "fuel flap closed");
        // Charge port: regular convention, matching the enum labels.
        eq(VoyahValueDecoder.decode("charge_port_flap", "0"), "closed", "charge flap closed");
        eq(VoyahValueDecoder.decode("charge_port_flap", "1"), "open", "charge flap open");

        // Unknown keys and null/blank inputs are untouched.
        eq(VoyahValueDecoder.decode("speed", "42"), "42", "unrelated key passthrough");
        eq(VoyahValueDecoder.decode("drive_mode", " 2 "), "comfort", "whitespace tolerated");
        eq(VoyahValueDecoder.decode("drive_mode", ""), "", "empty passthrough");
        check(VoyahValueDecoder.decode("drive_mode", null) == null, "null value passthrough");
        eq(VoyahValueDecoder.decode(null, "1"), "1", "null key passthrough");

        System.out.println("All VoyahValueDecoder tests passed.");
    }
}
