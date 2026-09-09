package com.car2hass.vehicle;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.os.BatteryManager;
import android.os.Bundle;

/** System channel worker: GPS/device signals always written to the ValueStore. */
public final class SystemWorker {
    private final Context ctx;
    private final ValueStore store;
    private LocationManager lm;
    private long lastBaselineMs;

    public SystemWorker(Context ctx, ValueStore store) {
        this.ctx = ctx;
        this.store = store;
        this.lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
    }

    public void start() {
        if (lm == null) return;
        baseline();
        try {
            LocationListener l = new LocationListener() {
                @Override public void onLocationChanged(Location loc) { accept(loc); }
                @Override public void onStatusChanged(String p, int s, Bundle e) {}
                @Override public void onProviderEnabled(String p) {}
                @Override public void onProviderDisabled(String p) {}
            };
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, l);
            try { lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000, 0, l); } catch (Exception ignored) {}
        } catch (Exception ignored) {}
    }

    /** Called by the 1 Hz aggregator; refreshes baseline and device signals. */
    public void tick() {
        if (System.currentTimeMillis() - lastBaselineMs > 30_000L) baseline();
        readBattery();
        readVolume();
        readPressure();
    }

    private void accept(Location loc) {
        if (loc == null) return;
        float acc = loc.getAccuracy();
        if (acc > 1000) return; // absurd fix
        putLocation(loc.getLatitude(), loc.getLongitude(),
                loc.hasSpeed() ? loc.getSpeed() : 0f,
                loc.hasBearing() ? loc.getBearing() : 0f,
                loc.hasAltitude() ? loc.getAltitude() : 0.0,
                acc, loc.getProvider(), loc.getTime());
    }

    private void baseline() {
        lastBaselineMs = System.currentTimeMillis();
        if (lm == null) return;
        Location best = null;
        for (String p : new String[]{"gps", "network", "passive"}) {
            try {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            } catch (Exception ignored) {}
        }
        if (best != null) accept(best);
    }

    private void putLocation(double lat, double lon, float speed, float bearing,
                             double alt, float acc, String provider, long timeMs) {
        store.put("location_lat", String.valueOf(lat), "system");
        store.put("location_lon", String.valueOf(lon), "system");
        store.put("location_speed", String.valueOf(speed), "system");
        store.put("location_bearing", String.valueOf(bearing), "system");
        store.put("location_altitude", String.valueOf(alt), "system");
        store.put("location_accuracy", String.valueOf(acc), "system");
        store.put("location_provider", provider == null ? "" : provider, "system");
        // Real GPS fix time (epoch seconds). Baseline/last-known positions must
        // keep their true timestamp so HA never treats a stale fix as "now".
        store.put("location_t", String.valueOf(timeMs / 1000), "system");
    }

    private void readBattery() {
        try {
            Intent bat = ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (bat != null) {
                int level = bat.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = bat.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (level >= 0 && scale > 0) {
                    store.put("device_battery", String.valueOf((int) (100f * level / scale)), "system");
                }
            }
        } catch (Exception ignored) {}
    }

    private void readVolume() {
        try {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                int v = am.getStreamVolume(AudioManager.STREAM_MUSIC);
                int m = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                if (m > 0) store.put("media_volume", String.valueOf((int) (100f * v / m)), "system");
            }
        } catch (Exception ignored) {}
    }

    private void readPressure() {
        try {
            SensorManager sm = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
            Sensor p = sm == null ? null : sm.getDefaultSensor(Sensor.TYPE_PRESSURE);
            if (p == null) return;
            sm.registerListener(new SensorEventListener() {
                @Override public void onSensorChanged(SensorEvent e) {
                    store.put("device_pressure", String.valueOf(e.values[0]), "system");
                    SensorManager s = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
                    if (s != null) s.unregisterListener(this);
                }
                @Override public void onAccuracyChanged(Sensor s, int a) {}
            }, p, SensorManager.SENSOR_DELAY_NORMAL);
        } catch (Exception ignored) {}
    }
}