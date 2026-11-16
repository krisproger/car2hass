package com.car2hass;

public final class EndpointMigration {
    private EndpointMigration() {}

    public static HaEndpointConfig fromLegacy(String host, int port, boolean https,
                                              boolean enabled, boolean commands,
                                              String carName) {
        if (host == null || host.trim().isEmpty()) return null;
        String address = host.trim();
        HaAddress parsed = HaAddress.parse(address, https);
        if (parsed.port == (https ? 443 : 80) && port > 0) {
            address = address + ":" + port;
        }
        HaAddress canonical = HaAddress.parse(address, https);
        return new HaEndpointConfig(
                "primary", "Home Assistant", canonical.canonicalAddress(),
                canonical.https, carName, enabled, commands);
    }
}
