package com.car2hass;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Pure helper: inserts non-selectable category header rows before each run of
 * signals sharing a category, so the expandable telemetry list shows a second
 * level (availability group -> category -> signals). Items keep their incoming
 * (key) order; categories follow the taxonomy order.
 */
public final class TelemetryGroups {

    public static List<CANDataItem> withCategoryHeaders(List<CANDataItem> items,
                                                        Function<String, String> categoryOf,
                                                        List<String> order) {
        List<CANDataItem> out = new ArrayList<>();
        if (items == null || items.isEmpty()) return out;
        for (String cat : order) {
            boolean headerAdded = false;
            for (CANDataItem it : items) {
                String c = categoryOf.apply(it.key);
                if (!cat.equals(c)) continue;
                if (!headerAdded) {
                    CANDataItem h = new CANDataItem(0, cat, "", 0);
                    h.isHeader = true;
                    h.groupKey = cat;
                    h.headerText = "";
                    out.add(h);
                    headerAdded = true;
                }
                out.add(it);
            }
        }
        // Defensive: items whose category is not in `order` are appended as-is.
        for (CANDataItem it : items) {
            if (!order.contains(categoryOf.apply(it.key))) out.add(it);
        }
        return out;
    }

    private TelemetryGroups() {}
}
