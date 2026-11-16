package com.car2hass;

public final class EndpointMigrationTest {
    public static void main(String[] args) {
        HaEndpointConfig local = EndpointMigration.fromLegacy(
                "192.168.1.10", 8123, false, true, false, "Car");
        check(local != null && "192.168.1.10:8123".equals(local.address));
        check("primary".equals(local.id));
        check(local.enabled && !local.commandsEnabled);

        HaEndpointConfig nabu = EndpointMigration.fromLegacy(
                "abc.ui.nabu.casa", 443, true, true, true, "Car");
        check(nabu != null && "abc.ui.nabu.casa".equals(nabu.address));
        check(nabu.commandsEnabled);

        check(EndpointMigration.fromLegacy("", 8123, false, true, true, "Car") == null);
        System.out.println("All EndpointMigration tests passed.");
    }
    private static void check(boolean ok) {
        if (!ok) throw new AssertionError();
    }
}
