package com.car2hass.vehicle;

public class DeviceAnonTest {
    public static void main(String[] args) throws Exception {
        // Pre-computed vector: printf 'TestModel4a5bsalt' | sha256sum
        String d = DeviceAnon.digest("TestModel", "4a5b", "salt");
        if (!"5645987f88e9558f86aa3afd6eaf644cf36c89e9dbce5d00120129dbd8d88c31".equals(d)) {
            throw new AssertionError("digest=" + d);
        }
        // null inputs are tolerated
        String d2 = DeviceAnon.digest(null, null, null);
        if (d2 == null || d2.length() != 64) throw new AssertionError("null digest=" + d2);
        // stable and different per input
        if (!d.equals(DeviceAnon.digest("TestModel", "4a5b", "salt"))) throw new AssertionError("not stable");
        if (d.equals(DeviceAnon.digest("OtherModel", "4a5b", "salt"))) throw new AssertionError("collision");
        System.out.println("All DeviceAnon tests passed.");
    }
}
