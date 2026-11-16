package com.car2hass;

public final class IntegrationInfoTest {
    public static void main(String[] args) throws Exception {
        IntegrationInfo loaded = IntegrationInfo.fromJson(
                "{\"integration_version\":\"3.5.6-beta.9\",\"api_version\":4,\"loaded\":true}");
        check(loaded != null && loaded.loaded && loaded.isCompatible());

        IntegrationInfo notLoaded = IntegrationInfo.fromJson(
                "{\"integration_version\":\"3.5.6-beta.9\",\"api_version\":4,\"loaded\":false}");
        check(notLoaded != null && !notLoaded.loaded);

        IntegrationInfo legacy = IntegrationInfo.fromJson(
                "{\"integration_version\":\"3.5.5\",\"api_version\":4}");
        check(legacy != null && legacy.loaded);

        System.out.println("All IntegrationInfo tests passed.");
    }
    private static void check(boolean ok) {
        if (!ok) throw new AssertionError();
    }
}
