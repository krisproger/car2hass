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

import com.car2hass.LogBuffer;

/**
 * System channel worker: GPS/device signals are written to the ValueStore from
 * its own thread (1 Hz device refresh + location callbacks).
 */
public final class SystemWorker implements ChannelWorker {

    private static final long TICK_INTERVAL_MS = 1000;

    private final Context ctx;
    private final ValueStore store;
    private final LocationManager lm;
    private LocationListener locationListener;
    private volatile boolean running;
    private volatile ChannelWorkerStatus.Result lastResult = ChannelWorkerStatus.Result.OK;
    private volatile String lastError = "";
    private Thread thread;
    private long lastBaselineMs;
    private volatile long cycleCount;
    private volatile long lastCycleAtMs;
    private volatile long threadStartAtMs;
    private volatile long errorCount;

    public SystemWorker(Context ctx, ValueStore store) {
        this.ctx = ctx;
        this.store = store;
        this.lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
    }

    @Override
    public String channelId() { return "system"; }

    @Override
    public boolean isRunning() { return running; }

    @Override
    public ChannelWorkerStatus status() {
        return new ChannelWorkerStatus("system", running, lastResult, lastError,
                TICK_INTERVAL_MS, TICK_INTERVAL_MS, cycleCount, lastCycleAtMs,
                threadStartAtMs, errorCount);
    }

    @Override
    public synchronized void start() {
        if (running) return;
        running = true;
        threadStartAtMs = System.currentTimeMillis();
        registerLocation();
        thread = new Thread(this::loop, "worker-system");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public synchronized void stop() {
        running = false;
        Thread t = thread;
        thread = null;
        if (t != null) t.interrupt();
        if (locationListener != null && lm != null) {
            try { lm.removeUpdates(locationListener); } catch (Exception ignored) {}
            locationListener = null;
        }
    }

    private void loop() {
        while (running) {
            try {
                tick();
                lastResult = ChannelWorkerStatus.Result.OK;
                lastError = "";
            } catch (Throwable t) {
                lastResult = ChannelWorkerStatus.Result.ERROR;
                lastError = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                errorCount++;
                LogBuffer.d("SystemWorker", "tick error: " + lastError);
            }
            cycleCount++;
            lastCycleAtMs = System.currentTimeMillis();
            try {
                Thread.sleep(TICK_INTERVAL_MS);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private void registerLocation() {
        if (lm == null) return;
        baseline();
        try {
            locationListener = new LocationListener() {
                @Override public void onLocationChanged(Location loc) { accept(loc); }
                @Override public void onStatusChanged(String p, int s, Bundle e) {}
                @Override public void onProviderEnabled(String p) {}
                @Override public void onProviderDisabled(String p) {}
            };
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, locationListener);
            try { lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000, 0, locationListener); } catch (Exception ignored) {}
        } catch (Exception ignored) {}
    }

    /** Refreshes baseline and device signals; called by the worker loop. */
    public void tick() {
        if (System.currentTimeMillis() - lastBaselineMs > 30_000L) baseline();
        readBattery();
        readVolume();
        readPressure();
    }

    private void accept(Location loc) {
        if (loc == null) return;
        float acc = loc.getAccuracy();
        if (LocationPolicy.isAbsurd(acc)) return;
        long now = System.currentTimeMillis();
        // A cached fix must never overwrite a live one.
        if (!LocationPolicy.isFresh(loc.getTime(), now)) return;
        // A coarse network/passive fix must not replace a recent accurate GPS
        // fix already in the store (PASSIVE carries the fixed bogus coordinate).
        if (LocationPolicy.suppressCoarse(loc.getProvider(), store.get("location_provider"),
                parseFloat(store.get("location_accuracy")),
                parseLong(store.get("location_t")) * 1000L, now)) {
            return;
        }
        putLocation(loc.getLatitude(), loc.getLongitude(),
                loc.hasSpeed() ? loc.getSpeed() : 0f,
                loc.hasBearing() ? loc.getBearing() : 0f,
                loc.hasAltitude() ? loc.getAltitude() : 0.0,
                acc, loc.getProvider(), loc.getTime());
    }

    private void baseline() {
        lastBaselineMs = System.currentTimeMillis();
        if (lm == null) return;
        // With a fresh fix already in the store, reading PASSIVE here would only
        // risk overwriting a live GPS position with a cached one.
        if (LocationPolicy.isFresh(parseLong(store.get("location_t")) * 1000L, lastBaselineMs)) {
            return;
        }
        Location best = null;
        for (String p : new String[]{"gps", "network", "passive"}) {
            try {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            } catch (Exception ignored) {}
        }
        if (best != null) accept(best);
    }

    private static long parseLong(String v) {
        try { return Long.parseLong(v); } catch (Exception e) { return 0L; }
    }

    private static float parseFloat(String v) {
        try { return Float.parseFloat(v); } catch (Exception e) { return 0f; }
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
                if (m > 0) store.put("system_media_volume", String.valueOf((int) (100f * v / m)), "system");
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
