package com.car2hass.vehicle;

public class LocationSourceTest {
    public static void main(String[] args) throws Exception {
        SnapshotStore store = new SnapshotStore();
        LocationSource src = new LocationSource(store);
        src.onLocation(55.7, 37.6, 12.5f, 90.0f, 150.0, 5.0f, "gps", 999L);
        if (!"55.7".equals(store.get("location_lat"))) throw new AssertionError("lat=" + store.get("location_lat"));
        if (!"37.6".equals(store.get("location_lon"))) throw new AssertionError("lon");
        if (!"12.5".equals(store.get("location_speed"))) throw new AssertionError("speed");
        if (!"90.0".equals(store.get("location_bearing"))) throw new AssertionError("bearing");
        if (!"150.0".equals(store.get("location_altitude"))) throw new AssertionError("alt");
        if (!"gps".equals(store.get("location_provider"))) throw new AssertionError("provider");
        src.onDeviceBattery(82);
        if (!"82".equals(store.get("device_battery"))) throw new AssertionError("battery");
        System.out.println("All LocationSource tests passed.");
    }
}
