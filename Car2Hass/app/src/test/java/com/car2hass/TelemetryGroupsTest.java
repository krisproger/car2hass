package com.car2hass;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TelemetryGroupsTest {
    private static List<CANDataItem> items(Map<String, String> cat) {
        List<CANDataItem> items = new ArrayList<>();
        for (String k : new String[]{"soc", "speed", "gear", "cabin_temp"}) {
            CANDataItem it = new CANDataItem(0, k, k, 0);
            it.key = k;
            items.add(it);
        }
        return items;
    }

    public static void main(String[] args) {
        Map<String, String> cat = new HashMap<>();
        cat.put("speed", "drive");
        cat.put("gear", "drive");
        cat.put("soc", "battery");
        cat.put("cabin_temp", "climate");
        List<String> order = Arrays.asList("drive", "battery", "climate", "other");
        List<CANDataItem> items = items(cat);

        // All collapsed: only headers (one per non-empty category), no signals.
        List<CANDataItem> collapsed = TelemetryGroups.buildRows(
                items, key -> cat.getOrDefault(key, "other"), order, new HashSet<>());
        int headers = 0, signals = 0;
        String[] expect = {"drive", "battery", "climate"};
        int gi = 0;
        for (CANDataItem it : collapsed) {
            if (it.isHeader) {
                if (!expect[gi].equals(it.groupKey)) throw new AssertionError("header order " + it.groupKey);
                gi++; headers++;
            } else signals++;
        }
        if (headers != 3) throw new AssertionError("collapsed headers=" + headers);
        if (signals != 0) throw new AssertionError("collapsed must hide signals, got " + signals);

        // One category expanded: its signals appear under its header only.
        Set<String> expanded = new HashSet<>(Arrays.asList("drive"));
        List<CANDataItem> rows = TelemetryGroups.buildRows(
                items, key -> cat.getOrDefault(key, "other"), order, expanded);
        signals = 0;
        String prev = null;
        for (CANDataItem it : rows) {
            if (it.isHeader) prev = it.groupKey;
            else {
                signals++;
                if (!"drive".equals(cat.get(it.key)))
                    throw new AssertionError("only drive signals expected, got " + it.key);
                if (!"drive".equals(prev)) throw new AssertionError(it.key + " not under drive");
            }
        }
        if (signals != 2) throw new AssertionError("expanded drive signals=" + signals);

        System.out.println("All TelemetryGroups tests passed.");
    }
}
