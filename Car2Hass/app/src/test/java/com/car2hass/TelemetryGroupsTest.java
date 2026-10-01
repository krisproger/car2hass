package com.car2hass;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TelemetryGroupsTest {
    public static void main(String[] args) {
        Map<String, String> cat = new HashMap<>();
        cat.put("speed", "drive");
        cat.put("gear", "drive");
        cat.put("soc", "battery");
        cat.put("cabin_temp", "climate");

        List<CANDataItem> items = new ArrayList<>();
        for (String k : new String[]{"soc", "speed", "gear", "cabin_temp"}) {
            CANDataItem it = new CANDataItem(0, k, k, 0);
            it.key = k;
            items.add(it);
        }
        List<String> order = Arrays.asList("drive", "battery", "climate", "other");
        List<CANDataItem> out = TelemetryGroups.withCategoryHeaders(
                items, key -> cat.getOrDefault(key, "other"), order);

        String[] expectGroups = {"drive", "battery", "climate"};
        int gi = 0;
        int headers = 0;
        String prev = null;
        for (CANDataItem it : out) {
            if (it.isHeader) {
                headers++;
                if (gi >= expectGroups.length || !expectGroups[gi].equals(it.groupKey))
                    throw new AssertionError("header order: " + it.groupKey);
                gi++;
                prev = it.groupKey;
            } else {
                String c = cat.get(it.key);
                if (!c.equals(prev)) throw new AssertionError("item " + it.key + " not under " + c);
            }
        }
        if (headers != 3) throw new AssertionError("expected 3 headers, got " + headers);
        if (out.size() != items.size() + 3) throw new AssertionError("size " + out.size());
        System.out.println("All TelemetryGroups tests passed.");
    }
}
