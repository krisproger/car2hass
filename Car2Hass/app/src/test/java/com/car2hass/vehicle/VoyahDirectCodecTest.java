package com.car2hass.vehicle;

public final class VoyahDirectCodecTest {
    public static void main(String[] args) {
        VoyahDirectCodec.Doors six = VoyahDirectCodec.doors(new int[]{0, 0, 1, 0, 0, 0});
        check(six.valid && six.frontRight, "6-field FR");
        check(!six.frontLeft && !six.rearLeft && !six.rearRight, "6-field others");

        VoyahDirectCodec.Doors trunk = VoyahDirectCodec.doors(new int[]{0, 0, 0, 1, 0, 0});
        check(trunk.valid && trunk.trunk && trunk.trunkValid, "6-field trunk");

        VoyahDirectCodec.Doors h97Driver = VoyahDirectCodec.doors(
                new int[]{0, 1, 0, -1, 0, 0, 1, -1, -1, -1});
        check(h97Driver.valid && h97Driver.frontLeft, "H97X driver open");
        check(!h97Driver.frontRight && !h97Driver.rearLeft && !h97Driver.rearRight,
                "H97X other doors closed");

        VoyahDirectCodec.Doors h97Closed = VoyahDirectCodec.doors(
                new int[]{0, 0, 0, -1, 0, 0, 1, -1, -1, -1});
        check(h97Closed.valid && !h97Closed.frontLeft && !h97Closed.frontRight,
                "H97X all closed");

        check(!VoyahDirectCodec.doors(new int[]{0, 0}).valid, "bad door length");

        VoyahDirectCodec.Fuel fuel = VoyahDirectCodec.fuel(52, 0, 43.0f, 0, 0f, 0f, 0f);
        check(fuel.valid && Math.abs(fuel.percent - 43.0f) < 0.01f, "fuel pct");
        check(!VoyahDirectCodec.fuel(52, 0, Float.NaN, 0, 0f, 0f, 0f).valid, "fuel NaN");
        check(!VoyahDirectCodec.fuel(52, 0, 101f, 0, 0f, 0f, 0f).valid, "fuel >100");

        VoyahDirectCodec.Battery battery = VoyahDirectCodec.battery(0, 126);
        check(battery.valid && Math.abs(battery.volts - 12.6f) < 0.01f, "battery 12.6");
        check(!VoyahDirectCodec.battery(0, -1).valid, "battery sentinel");

        VoyahDirectCodec.Odometer odo = VoyahDirectCodec.odometer(
                new int[]{632, 34672, 50, 65535, 1, 0});
        check(odo.valid && odo.total == 34672, "odometer total");
        check(odo.canTravel == 632, "odometer range");
        check(odo.evRange == 50, "EV range");

        System.out.println("All VoyahDirectCodec tests passed.");
    }

    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
    }
}
