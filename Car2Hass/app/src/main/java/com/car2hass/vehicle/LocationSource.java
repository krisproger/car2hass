package com.car2hass.vehicle;

/** Writes navigation/device signals into the SnapshotStore (testable without android.location.Location). */
public final class LocationSource {
    private final SnapshotStore store;
    public LocationSource(SnapshotStore store) { this.store = store; }

    public void onLocation(double lat, double lon, float speed, float bearing,
                           double alt, float accuracy, String provider, long timeMs) {
        store.setLocation(lat, lon, speed, bearing, alt, accuracy, provider, timeMs);
        store.put("location_lat", String.valueOf(lat));
        store.put("location_lon", String.valueOf(lon));
        store.put("location_speed", String.valueOf(speed));
        store.put("location_bearing", String.valueOf(bearing));
        store.put("location_altitude", String.valueOf(alt));
        store.put("location_accuracy", String.valueOf(accuracy));
        store.put("location_provider", provider == null ? "" : provider);
    }

    public void onDeviceBattery(int levelPercent) {
        store.put("device_battery", String.valueOf(levelPercent));
    }
}
