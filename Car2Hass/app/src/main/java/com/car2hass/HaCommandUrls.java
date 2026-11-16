package com.car2hass;

public final class HaCommandUrls {
    private HaCommandUrls() {}

    public static String pollUrl(HaEndpointConfig endpoint) {
        try {
            return endpoint.haAddress().url("/api/cartelemetry/commands?car_name="
                    + java.net.URLEncoder.encode(endpoint.carName, "UTF-8"));
        } catch (Exception e) {
            return endpoint.haAddress().url("/api/cartelemetry/commands");
        }
    }

    public static String ackUrl(HaEndpointConfig endpoint) {
        return endpoint.haAddress().url("/api/cartelemetry/commands");
    }
}
