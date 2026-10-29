package com.car2hass.vehicle;

public final class VoyahDirectCodecTest {
    public static void main(String[] args) {
        VoyahDirectCodec.Doors six = VoyahDirectCodec.doors(new int[]{0, 0, 1, 0, 0, 0});
        check(six.valid && six.frontRight, "6-field FR");
        check(!six.frontLeft && !six.rearLeft && !six.rearRight, "6-field others");

        VoyahDirectCodec.Doors trunk = VoyahDirectCodec.doors(new int[]{0, 0, 0, 1, 0, 0});
        check(trunk.valid && trunk.trunk && trunk.trunkValid, "6-field trunk");

        VoyahDirectCodec.Doors ten = VoyahDirectCodec.doors(
                new int[]{0, 0, 0, 1, 0, 0, 0, 0, 0, 0});
        check(ten.valid && ten.frontRight, "10-field FR");

        VoyahDirectCodec.Doors tenTrunk = VoyahDirectCodec.doors(
                new int[]{0, 0, 0, 0, 0, 1, 0, 0, 0, 0});
        check(tenTrunk.valid && tenTrunk.trunk, "10-field trunk");

        check(!VoyahDirectCodec.doors(new int[]{0, 0}).valid, "bad door length");

        VoyahDirectCodec.Fuel fuel = VoyahDirectCodec.fuel(52, 0, 43.0f, 0, 0f, 0f, 0f);
        check(fuel.valid && Math.abs(fuel.percent - 43.0f) < 0.01f, "fuel pct");
        check(!VoyahDirectCodec.fuel(52, 0, Float.NaN, 0, 0f, 0f, 0f).valid, "fuel NaN");
        check(!VoyahDirectCodec.fuel(52, 0, 101f, 0, 0f, 0f, 0f).valid, "fuel >100");

        VoyahDirectCodec.Battery battery = VoyahDirectCodec.battery(0, 126);
        check(battery.valid && Math.abs(battery.volts - 12.6f) < 0.01f, "battery 12.6");
        check(!VoyahDirectCodec.battery(0, -1).valid, "battery sentinel");

        VoyahDirectCodec.Odometer odo = VoyahDirectCodec.odometer(
                new float[]{880f, 55f, 0f, 0f, 0f, 34662f});
        check(odo.valid && Math.abs(odo.total - 34662f) < 0.1f, "odometer total");
        check(Math.abs(odo.canTravel - 880f) < 0.1f, "odometer range");
        check(Math.abs(odo.evRange - 55f) < 0.1f, "EV range");

        System.out.println("All VoyahDirectCodec tests passed.");
    }

    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
    }
}
