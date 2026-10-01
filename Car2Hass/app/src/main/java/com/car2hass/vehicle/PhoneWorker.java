package com.car2hass.vehicle;

import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.TrafficStats;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.StatFs;
import android.os.SystemClock;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.view.Display;
import android.view.WindowManager;

import com.car2hass.AppConfig;
import com.car2hass.LogBuffer;
import com.car2hass.PhoneSensors;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Phone channel worker: event-driven phone sensors plus a 60 s periodic refresh,
 * written to the shared {@link ValueStore} with source {@code "phone"} (the
 * phone device in HA). Enablement follows the channel state (master) plus the
 * global attribute filter and per-sensitive opt-ins; see
 * {@link PhoneSensors#effectiveKeys}.
 *
 * <p>Crash-safe: every read is wrapped so a failing subsystem never takes the
 * telemetry service down.
 */
public final class PhoneWorker implements ChannelWorker {

    private static final String TAG = "PhoneWorker";
    private static final String SOURCE = "phone";
    static final long CADENCE_MS = 60_000L;

    private final Context ctx;
    private final ValueStore store;
    private final Set<String> enabled = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private volatile boolean running;
    private volatile ChannelWorkerStatus.Result lastResult = ChannelWorkerStatus.Result.OK;
    private volatile String lastError = "";
    private volatile long cycleCount;
    private volatile long lastCycleAtMs;
    private volatile long threadStartAtMs;
    private volatile long errorCount;
    private ScheduledExecutorService executor;

    private BroadcastReceiver batteryReceiver;
    private BroadcastReceiver stateReceiver;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private SensorManager sensorManager;
    private final Map<String, SensorEventListener> activeSensors = new ConcurrentHashMap<>();

    public PhoneWorker(Context ctx, ValueStore store) {
        this.ctx = ctx.getApplicationContext();
        this.store = store;
    }

    // ── ChannelWorker ────────────────────────────────────────────────────────

    @Override public String channelId() { return SOURCE; }
    @Override public boolean isRunning() { return running; }

    @Override public ChannelWorkerStatus status() {
        return new ChannelWorkerStatus(SOURCE, running, lastResult, lastError,
                CADENCE_MS, CADENCE_MS, cycleCount, lastCycleAtMs, threadStartAtMs, errorCount);
    }

    @Override
    public synchronized void start() {
        if (running) return;
        running = true;
        threadStartAtMs = System.currentTimeMillis();
        refreshEnabled();
        try { registerBatteryReceiver(); } catch (Exception e) { LogBuffer.d(TAG, "battery: " + e.getMessage()); }
        try { registerStateReceiver(); } catch (Exception e) { LogBuffer.d(TAG, "state: " + e.getMessage()); }
        try { registerConnectivity(); } catch (Exception e) { LogBuffer.d(TAG, "connectivity: " + e.getMessage()); }
        try { registerSensors(); } catch (Exception e) { LogBuffer.d(TAG, "sensors: " + e.getMessage()); }
        try { readPower(); } catch (Exception e) { LogBuffer.d(TAG, "power: " + e.getMessage()); }
        try { readAudio(); } catch (Exception e) { LogBuffer.d(TAG, "audio: " + e.getMessage()); }
        try { readScreenGeometry(); } catch (Exception e) { LogBuffer.d(TAG, "screen: " + e.getMessage()); }
        try { readConnectivity(); } catch (Exception e) { LogBuffer.d(TAG, "conn read: " + e.getMessage()); }
        try { readWifi(); } catch (Exception e) { LogBuffer.d(TAG, "wifi: " + e.getMessage()); }
        try { readBattery(null); } catch (Exception e) { LogBuffer.d(TAG, "battery read: " + e.getMessage()); }
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "phone-worker");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleWithFixedDelay(this::safePeriodic, CADENCE_MS, CADENCE_MS,
                TimeUnit.MILLISECONDS);
        LogBuffer.i(TAG, "started, enabled=" + enabled.size());
    }

    @Override
    public synchronized void stop() {
        if (!running) return;
        running = false;
        unregisterReceiver(batteryReceiver);
        unregisterReceiver(stateReceiver);
        batteryReceiver = null;
        stateReceiver = null;
        if (networkCallback != null && connectivityManager != null) {
            try { connectivityManager.unregisterNetworkCallback(networkCallback); } catch (Exception ignored) {}
        }
        networkCallback = null;
        connectivityManager = null;
        unregisterSensors();
        ScheduledExecutorService e = executor;
        executor = null;
        if (e != null) e.shutdownNow();
        enabled.clear();
        LogBuffer.i(TAG, "stopped");
    }

    /** Re-reads the channel state + opt-ins + global filter; reconciles sensors. */
    public synchronized void refreshEnabled() {
        Set<String> effective = new HashSet<>(PhoneSensors.effectiveKeys(
                AppConfig.isPhoneChannelEnabled(ctx),
                AppConfig.getPhoneEnabledKeys(ctx),
                AppConfig.getDisabledSignals(ctx)));
        enabled.clear();
        enabled.addAll(effective);
        if (running) {
            try { registerSensors(); } catch (Exception e) { LogBuffer.d(TAG, "sync sensors: " + e.getMessage()); }
        }
    }

    // ── Write guard ──────────────────────────────────────────────────────────

    private void put(String key, String value) {
        if (value == null || value.isEmpty()) return;
        if (!enabled.contains(key)) return;
        ValueStore s = store;
        if (s == null) return;
        try { s.put(key, value, SOURCE); } catch (Exception ignored) {}
    }

    private void put(String key, long value) { put(key, String.valueOf(value)); }
    private void put(String key, int value) { put(key, String.valueOf(value)); }
    private void put(String key, double value) { put(key, trimDouble(value)); }
    private void putBool(String key, boolean value) { put(key, value ? "true" : "false"); }

    private static String trimDouble(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return null;
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return String.valueOf((long) v);
        return String.valueOf(v).replace(',', '.');
    }

    // ── Event-driven: battery / charger ──────────────────────────────────────

    private void registerBatteryReceiver() {
        batteryReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                try { readBattery(intent); } catch (Exception ignored) {}
            }
        };
        ctx.registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    private void readBattery(Intent bat) {
        if (bat == null) {
            bat = ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (bat == null) return;
        }
        int level = bat.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = bat.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if (level >= 0 && scale > 0) put("phone_battery_level", Math.round(100f * level / scale));
        int status = bat.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        put("phone_battery_state", batteryState(status));
        putBool("phone_is_charging", status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL);
        put("phone_charger_type", chargerType(bat.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)));
        put("phone_battery_health", batteryHealth(
                bat.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)));
        int temp = bat.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
        if (temp != Integer.MIN_VALUE) put("phone_battery_temperature", temp / 10.0);
    }

    private static String batteryState(int status) {
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING: return "charging";
            case BatteryManager.BATTERY_STATUS_DISCHARGING: return "discharging";
            case BatteryManager.BATTERY_STATUS_FULL: return "full";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING: return "not_charging";
            default: return "unknown";
        }
    }

    private static String chargerType(int plugged) {
        switch (plugged) {
            case BatteryManager.BATTERY_PLUGGED_AC: return "ac";
            case BatteryManager.BATTERY_PLUGGED_USB: return "usb";
            case BatteryManager.BATTERY_PLUGGED_WIRELESS: return "wireless";
            case BatteryManager.BATTERY_PLUGGED_DOCK: return "dock";
            default: return "none";
        }
    }

    private static String batteryHealth(int health) {
        switch (health) {
            case BatteryManager.BATTERY_HEALTH_GOOD: return "good";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT: return "overheat";
            case BatteryManager.BATTERY_HEALTH_DEAD: return "dead";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: return "over_voltage";
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE: return "unspecified_failure";
            case BatteryManager.BATTERY_HEALTH_COLD: return "cold";
            default: return "unknown";
        }
    }

    private void readBatteryProperties() {
        BatteryManager bm = (BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
        if (bm == null) return;
        long charge = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
        long current = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        // µAh / µA -> hours; only meaningful while current flows into the battery.
        if (charge > 0 && current > 0) {
            long seconds = (long) ((double) charge / current * 3600.0);
            if (seconds > 0 && seconds < 7L * 24 * 3600) put("phone_remaining_charge_time", seconds);
        }
        // Power (W) from the live current and the last-known battery voltage.
        Intent bat = null;
        try { bat = ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED)); }
        catch (Exception ignored) {}
        int voltage = bat == null ? Integer.MIN_VALUE
                : bat.getIntExtra(BatteryManager.EXTRA_VOLTAGE, Integer.MIN_VALUE);
        if (voltage > 0 && current != 0 && current != Long.MIN_VALUE && current != Long.MAX_VALUE) {
            double watts = (voltage / 1000.0) * (current / 1_000_000.0);
            put("phone_battery_power", Math.abs(watts));
        }
    }

    // ── Event-driven: screen / WiFi state ────────────────────────────────────

    private void registerStateReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        stateReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                String action = intent == null ? null : intent.getAction();
                try {
                    if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)) {
                        readWifi();
                    } else {
                        readPower();
                        readScreenGeometry();
                    }
                } catch (Exception ignored) {}
            }
        };
        ctx.registerReceiver(stateReceiver, filter);
    }

    private void readPower() {
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        if (pm == null) return;
        putBool("phone_interactive", pm.isInteractive());
        putBool("phone_power_save", pm.isPowerSaveMode());
        putBool("phone_doze", pm.isDeviceIdleMode());
    }

    private void readScreenGeometry() {
        Configuration cfg = ctx.getResources().getConfiguration();
        String orientation = cfg.orientation == Configuration.ORIENTATION_LANDSCAPE ? "landscape"
                : cfg.orientation == Configuration.ORIENTATION_PORTRAIT ? "portrait" : "undefined";
        put("phone_screen_orientation", orientation);
        WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        if (wm != null) {
            Display display = wm.getDefaultDisplay();
            if (display != null) put("phone_screen_rotation", display.getRotation() * 90);
        }
        try {
            int brightness = Settings.System.getInt(ctx.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS);
            put("phone_screen_brightness", brightness);
        } catch (Exception ignored) {}
        try {
            int timeout = Settings.System.getInt(ctx.getContentResolver(),
                    Settings.System.SCREEN_OFF_TIMEOUT);
            put("phone_screen_off_timeout", timeout);
        } catch (Exception ignored) {}
    }

    // ── Event-driven: connectivity / WiFi ────────────────────────────────────

    private void registerConnectivity() {
        connectivityManager = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return;
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network network) { safeConnectivityReads(); }
            @Override public void onLost(Network network) { safeConnectivityReads(); }
            @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                safeConnectivityReads();
            }
        };
        final ConnectivityManager cm = connectivityManager;
        final ConnectivityManager.NetworkCallback cb = networkCallback;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                cm.registerDefaultNetworkCallback(cb, new Handler(Looper.getMainLooper()));
            } else {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (cb == null) return;
                    try { cm.registerDefaultNetworkCallback(cb); }
                    catch (Exception e) { LogBuffer.d(TAG, "connectivity register: " + e.getMessage()); }
                });
            }
        } catch (Exception e) {
            LogBuffer.d(TAG, "connectivity register: " + e.getMessage());
        }
    }

    private void safeConnectivityReads() {
        try { readConnectivity(); } catch (Exception ignored) {}
        try { readWifi(); } catch (Exception ignored) {}
    }

    private void readConnectivity() {
        ConnectivityManager cm = connectivityManager != null ? connectivityManager
                : (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return;
        String transport = "none";
        Network active = cm.getActiveNetwork();
        NetworkCapabilities caps = active == null ? null : cm.getNetworkCapabilities(active);
        if (caps != null) {
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) transport = "wifi";
            else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) transport = "cellular";
            else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) transport = "ethernet";
            else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) transport = "bluetooth";
            else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) transport = "vpn";
            else transport = "other";
        }
        put("phone_transport_type", transport);
        put("phone_connection_type", transport);
    }

    private void readWifi() {
        WifiManager wm = (WifiManager) ctx.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        if (wm == null) return;
        boolean wifiOn = false;
        try { wifiOn = wm.isWifiEnabled(); } catch (Exception ignored) {}
        put("phone_wifi_state", wifiOn ? "enabled" : "disabled");
        WifiInfo info = null;
        try { info = wm.getConnectionInfo(); } catch (Exception ignored) {}
        if (info != null) {
            String ssid = info.getSSID();
            if (ssid != null && !"<unknown ssid>".equals(ssid) && !"0x".equals(ssid)) {
                put("phone_wifi_ssid", ssid.replace("\"", ""));
            }
            String bssid = info.getBSSID();
            if (bssid != null && !"02:00:00:00:00:00".equals(bssid)) put("phone_wifi_bssid", bssid);
            int freq = info.getFrequency();
            if (freq > 0) put("phone_wifi_frequency", freq);
            int link = info.getLinkSpeed();
            if (link > 0) put("phone_wifi_link_speed", link);
            int rssi = info.getRssi();
            if (rssi != -127 && rssi != Integer.MAX_VALUE) put("phone_wifi_signal", rssi);
            int ip = info.getIpAddress();
            if (ip != 0) {
                put("phone_wifi_ip", (ip & 0xff) + "." + ((ip >> 8) & 0xff) + "."
                        + ((ip >> 16) & 0xff) + "." + ((ip >> 24) & 0xff));
            }
        }
        try {
            Method m = wm.getClass().getMethod("isWifiApEnabled");
            Object ap = m.invoke(wm);
            if (ap instanceof Boolean) putBool("phone_hotspot_state", (Boolean) ap);
        } catch (Exception ignored) {}
    }

    // ── Event-driven: audio ──────────────────────────────────────────────────

    private void readAudio() {
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        put("phone_ringer_mode", ringerMode(am.getRingerMode()));
        put("phone_audio_mode", audioMode(am.getMode()));
        int vol = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        if (max > 0) put("phone_volume_music", Math.round(100f * vol / max));
        putBool("phone_is_headphones", am.isWiredHeadsetOn() || am.isBluetoothA2dpOn());
        putBool("phone_is_mic_muted", am.isMicrophoneMute());
        putBool("phone_is_speakerphone_on", am.isSpeakerphoneOn());
        putBool("phone_is_music_active", am.isMusicActive());
    }

    private static String ringerMode(int mode) {
        switch (mode) {
            case AudioManager.RINGER_MODE_SILENT: return "silent";
            case AudioManager.RINGER_MODE_VIBRATE: return "vibrate";
            case AudioManager.RINGER_MODE_NORMAL: return "normal";
            default: return "unknown";
        }
    }

    private static String audioMode(int mode) {
        switch (mode) {
            case AudioManager.MODE_NORMAL: return "normal";
            case AudioManager.MODE_RINGTONE: return "ringtone";
            case AudioManager.MODE_IN_CALL: return "in_call";
            case AudioManager.MODE_IN_COMMUNICATION: return "in_communication";
            default: return "unknown";
        }
    }

    // ── Sensors (opt-in only) ────────────────────────────────────────────────

    private final SensorEventListener lightListener = new SensorEventListener() {
        @Override public void onSensorChanged(SensorEvent e) { put("phone_light", e.values[0]); }
        @Override public void onAccuracyChanged(Sensor s, int a) {}
    };

    private final SensorEventListener pressureListener = new SensorEventListener() {
        @Override public void onSensorChanged(SensorEvent e) { put("phone_pressure", e.values[0]); }
        @Override public void onAccuracyChanged(Sensor s, int a) {}
    };

    private final SensorEventListener proximityListener = new SensorEventListener() {
        @Override public void onSensorChanged(SensorEvent e) { put("phone_proximity", e.values[0]); }
        @Override public void onAccuracyChanged(Sensor s, int a) {}
    };

    private final SensorEventListener stepsListener = new SensorEventListener() {
        @Override public void onSensorChanged(SensorEvent e) {
            if (e.values.length > 0) put("phone_steps", (long) e.values[0]);
        }
        @Override public void onAccuracyChanged(Sensor s, int a) {}
    };

    private void registerSensors() {
        if (sensorManager == null) {
            sensorManager = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        }
        if (sensorManager == null) return;
        syncSensor("phone_light", Sensor.TYPE_LIGHT, lightListener);
        syncSensor("phone_pressure", Sensor.TYPE_PRESSURE, pressureListener);
        syncSensor("phone_proximity", Sensor.TYPE_PROXIMITY, proximityListener);
        syncSensor("phone_steps", Sensor.TYPE_STEP_COUNTER, stepsListener);
    }

    private void syncSensor(String key, int type, SensorEventListener listener) {
        boolean want = enabled.contains(key);
        boolean has = activeSensors.containsKey(key);
        if (want && !has) {
            if ("phone_steps".equals(key) && !hasActivityRecognitionPermission()) return;
            Sensor sensor = sensorManager.getDefaultSensor(type);
            if (sensor != null) {
                try {
                    if (sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)) {
                        activeSensors.put(key, listener);
                    }
                } catch (Exception ignored) {}
            }
        } else if (!want && has) {
            SensorEventListener l = activeSensors.remove(key);
            try { sensorManager.unregisterListener(l); } catch (Exception ignored) {}
        }
    }

    private boolean hasActivityRecognitionPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true;
        try {
            return ctx.checkSelfPermission("android.permission.ACTIVITY_RECOGNITION")
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            return false;
        }
    }

    private void unregisterSensors() {
        if (sensorManager != null) {
            for (SensorEventListener l : activeSensors.values()) {
                try { sensorManager.unregisterListener(l); } catch (Exception ignored) {}
            }
        }
        activeSensors.clear();
    }

    // ── Periodic (60 s) ──────────────────────────────────────────────────────

    private void safePeriodic() {
        if (!running) return;
        refreshEnabled();
        try { readBattery(null); } catch (Exception ignored) {}
        try { readPower(); } catch (Exception ignored) {}
        try { readAudio(); } catch (Exception ignored) {}
        try { readConnectivity(); } catch (Exception ignored) {}
        try { readWifi(); } catch (Exception ignored) {}
        try { readScreenGeometry(); } catch (Exception ignored) {}
        try { readMemory(); } catch (Exception ignored) {}
        try { readStorage(); } catch (Exception ignored) {}
        try { readTraffic(); } catch (Exception ignored) {}
        try { readBatteryProperties(); } catch (Exception ignored) {}
        try { readSim(); } catch (Exception ignored) {}
        try { readBuildAndTime(); } catch (Exception ignored) {}
        lastResult = ChannelWorkerStatus.Result.OK;
        lastError = "";
        cycleCount++;
        lastCycleAtMs = System.currentTimeMillis();
    }

    private void readMemory() {
        Runtime rt = Runtime.getRuntime();
        put("phone_memory_used", rt.totalMemory() - rt.freeMemory());
        put("phone_memory_free", rt.freeMemory());
    }

    private void readStorage() {
        StatFs internal = new StatFs(Environment.getDataDirectory().getAbsolutePath());
        put("phone_storage_internal_free", internal.getAvailableBytes());
        put("phone_storage_internal_total", internal.getTotalBytes());
        try {
            if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
                StatFs external = new StatFs(Environment.getExternalStorageDirectory().getAbsolutePath());
                put("phone_storage_external_free", external.getAvailableBytes());
                put("phone_storage_external_total", external.getTotalBytes());
            }
        } catch (Exception ignored) {}
    }

    private void readTraffic() {
        long tx = TrafficStats.getTotalTxBytes();
        if (tx >= 0) put("phone_data_tx", tx);
        long rx = TrafficStats.getTotalRxBytes();
        if (rx >= 0) put("phone_data_rx", rx);
    }

    private void readSim() {
        try {
            SubscriptionManager sm = (SubscriptionManager) ctx
                    .getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
            if (sm != null) {
                List<SubscriptionInfo> subs = sm.getActiveSubscriptionInfoList();
                if (subs != null && !subs.isEmpty()) {
                    SubscriptionInfo info = subs.get(0);
                    CharSequence carrier = info.getCarrierName();
                    if (carrier != null) put("phone_sim_carrier", carrier.toString());
                    String country = info.getCountryIso();
                    if (country != null && !country.isEmpty()) put("phone_sim_country", country);
                    return;
                }
            }
        } catch (Exception ignored) {}
        TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
        if (tm == null) return;
        String operator = tm.getSimOperatorName();
        if (operator != null && !operator.isEmpty()) put("phone_sim_carrier", operator);
    }

    private void readBuildAndTime() {
        put("phone_android_os_version", Build.VERSION.RELEASE);
        put("phone_android_os_security_patch", Build.VERSION.SECURITY_PATCH);
        put("phone_time_zone", TimeZone.getDefault().getID());
        long rebootSeconds = (System.currentTimeMillis() - SystemClock.elapsedRealtime()) / 1000L;
        if (rebootSeconds > 0) put("phone_last_reboot", rebootSeconds);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void unregisterReceiver(BroadcastReceiver receiver) {
        if (receiver == null || ctx == null) return;
        try { ctx.unregisterReceiver(receiver); } catch (Exception ignored) {}
    }
}
