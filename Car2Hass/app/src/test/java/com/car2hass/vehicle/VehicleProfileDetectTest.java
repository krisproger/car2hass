package com.car2hass.vehicle;

import java.util.HashMap;
import java.util.Map;

public class VehicleProfileDetectTest {
    public static void main(String[] args) {
        Map<String, String> props = new HashMap<>();
        props.put("ro.product.manufacturer", "Voyah");
        props.put("ro.vehicle.type", "VF12");
        props.put("ro.build.date", "2023-06-01");
        VehicleProfileDetect.Result r = VehicleProfileDetect.detect(props);
        check(r.getMakeHint().contains("Voyah"), "make hint from manufacturer");
        check(r.getProducerHint().length() > 0, "producer hint present");

        Map<String, String> empty = new HashMap<>();
        VehicleProfileDetect.Result e = VehicleProfileDetect.detect(empty);
        check(e.isEmpty(), "empty props yield empty result");
        System.out.println("All VehicleProfileDetect tests passed.");
    }

    private static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }
}