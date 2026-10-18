package com.car2hass.vehicle;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChannelGateTest {
    public static void main(String[] args) {
        // Unknown channel (no availability entry) is kept (fail-open).
        List<String> a = ChannelGate.resolve(Arrays.asList("voyah", "diplus"), Collections.emptyMap(), true);
        if (!a.equals(Arrays.asList("system", "voyah", "diplus"))) throw new AssertionError("fail-open: " + a);

        // Known-dead channel is dropped; known-alive kept.
        Map<String, Boolean> av = new HashMap<>();
        av.put("voyah", false);
        av.put("diplus", true);
        List<String> b = ChannelGate.resolve(Arrays.asList("voyah", "diplus"), av, true);
        if (!b.equals(Arrays.asList("system", "diplus"))) throw new AssertionError("gate: " + b);

        // system deduped; obd dropped when disabled.
        List<String> c = ChannelGate.resolve(Arrays.asList("system", "obd"), Collections.emptyMap(), false);
        if (!c.equals(Arrays.asList("system"))) throw new AssertionError("system/obd: " + c);

        // null list -> just system.
        if (!ChannelGate.resolve(null, null, true).equals(Collections.singletonList("system")))
            throw new AssertionError("null list");

        System.out.println("All ChannelGate tests passed.");
    }
}
