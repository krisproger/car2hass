package com.car2hass;

import java.util.ArrayList;
import java.util.List;

public final class EndpointRouter {
    private EndpointRouter() {}

    public static List<String> enabledIds(List<HaEndpointConfig> endpoints) {
        List<String> ids = new ArrayList<>();
        for (HaEndpointConfig endpoint : endpoints) {
            if (endpoint.enabled) ids.add(endpoint.id);
        }
        return ids;
    }
}
