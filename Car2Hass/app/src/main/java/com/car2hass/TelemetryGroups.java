package com.car2hass;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Pure helper for the telemetry list: builds the visible rows of one
 * availability group as availability -> category -> signals. A category header
 * is always shown; its signals only when the category is expanded.
 */
public final class TelemetryGroups {

    public static List<CANDataItem> buildRows(List<CANDataItem> items,
                                              Function<String, String> categoryOf,
                                              List<String> order,
                                              Set<String> expandedCategories) {
        List<CANDataItem> out = new ArrayList<>();
        if (items == null || items.isEmpty()) return out;
        Set<String> expanded = expandedCategories != null
                ? expandedCategories : Collections.<String>emptySet();
        for (String cat : order) {
            List<CANDataItem> inCat = new ArrayList<>();
            for (CANDataItem it : items) {
                if (cat.equals(categoryOf.apply(it.key))) inCat.add(it);
            }
            if (inCat.isEmpty()) continue;
            CANDataItem h = new CANDataItem(0, cat, "", 0);
            h.isHeader = true;
            h.groupKey = cat;
            h.headerText = "";
            out.add(h);
            if (expanded.contains(cat)) out.addAll(inCat);
        }
        // Defensive: items whose category is not in `order` are appended as-is.
        for (CANDataItem it : items) {
            if (!order.contains(categoryOf.apply(it.key))) out.add(it);
        }
        return out;
    }

    private TelemetryGroups() {}
}
