package com.car2hass.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.BatteryManager;
import android.net.wifi.WifiManager;

import com.car2hass.AppConfig;
import com.car2hass.AppInfo;
import com.car2hass.BackgroundModeManager;
import com.car2hass.BuildConfig;
import com.car2hass.CANDataItem;
import com.car2hass.CANDataReader;
import com.car2hass.CommandPoller;
import com.car2hass.ProbeUploader;
import com.car2hass.vehicle.BydCloudChannel;
import com.car2hass.vehicle.DataChannel;
import com.car2hass.vehicle.DiPlusChannel;
import com.car2hass.vehicle.DiPlusPushChannel;
import com.car2hass.vehicle.ExperimentalChannel;
import com.car2hass.vehicle.LocationSource;
import com.car2hass.vehicle.NativeChannel;
import com.car2hass.vehicle.ObdChannel;
import com.car2hass.vehicle.RegistryStore;
import com.car2hass.vehicle.SignalProber;
import com.car2hass.vehicle.SnapshotStore;
import com.car2hass.vehicle.SourceManager;
import com.car2hass.vehicle.SysPropsChannel;
import com.car2hass.vehicle.VehicleProfile;
import com.car2hass.vehicle.VehicleResearch;
import com.car2hass.vehicle.VoyahChannel;
import com.car2hass.GeofenceZone;
import com.car2hass.HassClient;
import com.car2hass.LogBuffer;
import com.car2hass.LogExportHelper;
import com.car2hass.MainActivity;
import com.car2hass.R;
import com.car2hass.SensorValueHistory;
import com.car2hass.SignalTranslator;
import com.car2hass.rules.RuleEngine;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class TelemetryService extends Service {

    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "diplus_telemetry_channel";
    // Native refresh cycles took 4500–5500 ms at a 5000 ms interval, so the
    // async refresh almost never finished before the next tick and the cycle
    // was regularly skipped (up to ~120/session, log analysis 2026-08-17 §2.5).
    // Raised to 7000 ms to leave a comfortable margin once write-phase
    // fallbacks add extra latency to a cycle.
    private static final long REFRESH_INTERVAL_MS = 7000;
    private static final long FLUSH_INTERVAL_MS = 4000; // batch window 3–5 s (spec Section 3)
    private static final long SNAPSHOT_INTERVAL_MS = 1000; // 1 Hz snapshot ticker

    private final IBinder binder = new LocalBinder();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService telemetryExecutor;
    private ExecutorService flushExecutor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private List<CANDataItem> knownItems;
    private LocationListener locationListener;
    private ConnectivityManager.NetworkCallback networkCallback;
    private TelemetryCallback callback;
    private CommandPoller commandPoller;
    private RuleEngine ruleEngine;
    private final ConcurrentHashMap<String, String> cachedSignalValues = new ConcurrentHashMap<>();
    private final SnapshotStore snapshotStore = new SnapshotStore();
    private final LocationSource locationSource = new LocationSource(snapshotStore);

    public RuleEngine getRuleEngine() {
        return ruleEngine;
    }

    private double lastLat = Double.NaN;
    private double lastLon = Double.NaN;
    private float lastAccuracy = 0;
    private long lastLocTime = 0;
    private volatile long lastNetworkFlushMs = 0;
    private static final long NETWORK_FLUSH_DEBOUNCE_MS = 30000;
    private static final long VEHICLE_ASLEEP_INTERVAL_MS = 30000;
    private static final long LOCATION_MIN_TIME_MS = 3000;
    private static final float LOCATION_MIN_DISTANCE_M = 10f;
    private static final long AUTO_LOG_DUMP_DELAY_MS = 5 * 60 * 1000; // 5 minutes after start

    private volatile boolean vehicleAsleep = false;
    private static volatile boolean explicitStopRequested = false;
    private final Runnable autoLogDumpRunnable = this::dumpLogAfterBoot;
    private PendingIntent cachedNotificationIntent;
    private long lastLocationRetryMs = 0;
    private static final long LOCATION_RETRY_INTERVAL_MS = 30000;
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;

    public interface TelemetryCallback {
        void onDataUpdated(List<CANDataItem> items, long timestamp);
        void onError(String message);
    }

    public class LocalBinder extends Binder {
        public TelemetryService getService() {
            return TelemetryService.this;
        }
    }

    public void setCallback(TelemetryCallback callback) {
        this.callback = callback;
    }

    public double getLastLatitude() {
        return lastLat;
    }

    public double getLastLongitude() {
        return lastLon;
    }

    public float getLastAccuracy() {
        return lastAccuracy;
    }

    public boolean hasValidLocation() {
        return !Double.isNaN(lastLat) && !Double.isNaN(lastLon);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        LogBuffer.init(this);
        LogBuffer.i("TelemetryService", "Car2Hass " + AppInfo.getVersionString(this)
                + " starting, pid=" + android.os.Process.myPid()
                + " backgroundMode=" + BackgroundModeManager.isEnabled(this));
        createNotificationChannel();
        // CRITICAL: startForeground must be the very first call and never be skipped.
        try {
            startForeground(NOTIFICATION_ID, buildNotification());
            LogBuffer.i("TelemetryService", "startForeground called");
        } catch (Exception e) {
            LogBuffer.e("TelemetryService", "startForeground failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        acquireWakeLock();
        acquireWifiLock();
        knownItems = buildKnownItems();
        preregisterGeofenceItems();
        shutdownExecutors();
        telemetryExecutor = Executors.newSingleThreadExecutor();
        flushExecutor = Executors.newSingleThreadExecutor();
        ruleEngine = new RuleEngine(getApplicationContext(), cachedSignalValues::get);
        SensorValueHistory.ensureLoaded(AppConfig.getSensorValueHistoryJson(this));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        explicitStopRequested = false;
        LogBuffer.i("TelemetryService", "onStartCommand startId=" + startId
                + " flags=" + flags
                + " intent=" + (intent != null ? intent.toString() : "null"));
        // Re-promote to foreground on every restart to satisfy Android 12+ requirements.
        try {
            startForeground(NOTIFICATION_ID, buildNotification());
        } catch (Exception e) {
            LogBuffer.e("TelemetryService", "startForeground retry failed: " + e.getMessage());
        }
        acquireWakeLock();
        acquireWifiLock();
        CANDataReader.resetRefreshState();
        if (!running.get()) {
            running.set(true);
            startTelemetryLoop();
            startFlushLoop();
            startLocationUpdates();
            startSnapshotTicker();
            registerNetworkCallback();
            startCommandPoller();
            if (ruleEngine != null) ruleEngine.start();
            mainHandler.postDelayed(autoLogDumpRunnable, AUTO_LOG_DUMP_DELAY_MS);
            LogBuffer.i("TelemetryService", "Scheduled auto log dump in " + AUTO_LOG_DUMP_DELAY_MS + " ms");
            maybeRunProbe();
        } else {
            LogBuffer.d("TelemetryService", "Service already running, skipped loop restart");
        }
        return START_STICKY;
    }

    /**
     * Auto-triggers the vehicle probe: full research on first launch (no saved
     * report) or every 3rd launch; a light channel-availability check otherwise.
     * Runs on a background thread so it never blocks the telemetry cycle.
     */
    private void maybeRunProbe() {
        new Thread(() -> {
            try {
                int runs = AppConfig.incRunsCount(this);
                boolean hasReport = AppConfig.getProbeReportPath(this) != null;
                boolean full = !hasReport || (runs % 3 == 0);
                List<DataChannel> channels = buildResearchChannels();
                VehicleProfile profile = AppConfig.getVehicleProfile(this);
                if (full) {
                    LogBuffer.i("TelemetryService", "Probe: full research (runs=" + runs + ")");
                    VehicleResearch.ResearchOutcome outcome = VehicleResearch.runWithRegistry(this,
                            RegistryStore.load(this), channels, profile, null, new SignalProber(),
                            (ctx, p, a, rp) -> AppConfig.saveProbeResult(ctx, p,
                                    com.car2hass.vehicle.ResearchUiModel.unionActive(
                                            AppConfig.getActiveChannels(ctx), a), rp));
                    maybeUploadReport(outcome.reportPath);
                } else {
                    LogBuffer.i("TelemetryService", "Probe: light channel check (runs=" + runs + ")");
                    runLightChannelCheck(channels);
                }
            } catch (Exception e) {
                LogBuffer.e("TelemetryService", "maybeRunProbe: " + e.getMessage());
            }
        }, "probe-auto").start();
    }

    /** Sends the report to the site when the user opted in (spec Section 5). */
    private void maybeUploadReport(String path) {
        if (path == null || !AppConfig.isProbeUploadEnabled(this)) return;
        new Thread(() -> {
            boolean ok = ProbeUploader.upload(this, path);
            LogBuffer.i("TelemetryService", "probe upload: " + ok);
            if (!ok) {
                // Offline/server down: keep the report in the persistent queue
                // for later retries (user decides about stale entries in-app).
                try {
                    String anon = com.car2hass.vehicle.DeviceAnon.fromContext(this);
                    com.car2hass.UploadQueue.enqueue(this, com.car2hass.UploadQueue.KIND_PROBE,
                            AppInfo.getVersionString(this), anon, "", path);
                } catch (Exception e) {
                    LogBuffer.e("TelemetryService", "queue probe: " + e.getMessage());
                }
            }
        }, "probe-upload").start();
    }

    private void runLightChannelCheck(List<DataChannel> channels) {
        java.util.List<String> alive = new java.util.ArrayList<>();
        for (DataChannel ch : channels) {
            com.car2hass.vehicle.ChannelResult r = VehicleResearch.run(this, java.util.Collections.singletonList(ch), null).get(0);
            if (r.isAlive()) alive.add(ch.id());
        }
        // Merge, never drop: a failed check must not uncheck a user-enabled channel.
        AppConfig.updateActiveChannels(this,
                com.car2hass.vehicle.ResearchUiModel.unionActive(
                        AppConfig.getActiveChannels(this), alive));
    }

    private List<DataChannel> buildResearchChannels() {
        List<DataChannel> channels;
        try {
            channels = com.car2hass.vehicle.ChannelCatalog.createAll(
                    com.car2hass.vehicle.RegistryStore.load(this));
        } catch (Exception e) {
            LogBuffer.e("TelemetryService", "ChannelCatalog: " + e.getMessage());
            channels = new ArrayList<>();
            channels.add(new com.car2hass.vehicle.DiPlusChannel());
            channels.add(new com.car2hass.vehicle.NativeChannel());
        }
        return channels;
    }

    private void acquireWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                LogBuffer.d("TelemetryService", "WakeLock already held");
                return;
            }
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "diplus2hass:telemetry");
                wakeLock.acquire(24 * 60 * 60 * 1000L); // 24h, re-acquired on restart
                LogBuffer.i("TelemetryService", "WakeLock acquired");
            } else {
                LogBuffer.w("TelemetryService", "PowerManager is null, cannot acquire wake lock");
            }
        } catch (Exception e) {
            LogBuffer.e("TelemetryService", "acquireWakeLock failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
                LogBuffer.i("TelemetryService", "WakeLock released");
            } else {
                LogBuffer.d("TelemetryService", "WakeLock not held, nothing to release");
            }
        } catch (Exception e) {
            LogBuffer.e("TelemetryService", "releaseWakeLock failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void acquireWifiLock() {
        try {
            if (wifiLock != null && wifiLock.isHeld()) {
                LogBuffer.d("TelemetryService", "WifiLock already held");
                return;
            }
            WifiManager wm = (WifiManager) getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "diplus2hass:wifi");
                wifiLock.acquire();
                LogBuffer.i("TelemetryService", "WifiLock acquired");
            } else {
                LogBuffer.w("TelemetryService", "WifiManager is null, cannot acquire wifi lock");
            }
        } catch (Exception e) {
            LogBuffer.e("TelemetryService", "acquireWifiLock failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void releaseWifiLock() {
        try {
            if (wifiLock != null && wifiLock.isHeld()) {
                wifiLock.release();
                LogBuffer.i("TelemetryService", "WifiLock released");
            } else {
                LogBuffer.d("TelemetryService", "WifiLock not held, nothing to release");
            }
        } catch (Exception e) {
            LogBuffer.e("TelemetryService", "releaseWifiLock failed: " + e.getMessage());
        }
    }

    private void shutdownExecutors() {
        try {
            if (telemetryExecutor != null) {
                telemetryExecutor.shutdownNow();
                telemetryExecutor = null;
            }
        } catch (Exception e) {
            LogBuffer.e("TelemetryService", "shutdown telemetryExecutor failed: " + e.getMessage());
        }
        try {
            if (flushExecutor != null) {
                flushExecutor.shutdownNow();
                flushExecutor = null;
            }
        } catch (Exception e) {
            LogBuffer.e("TelemetryService", "shutdown flushExecutor failed: " + e.getMessage());
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        callback = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        LogBuffer.i("TelemetryService", "onDestroy — explicitStop=" + explicitStopRequested);
        running.set(false);
        LogBuffer.flush();
        mainHandler.removeCallbacks(autoLogDumpRunnable);
        stopLocationUpdates();
        unregisterNetworkCallback();
        stopCommandPoller();
        if (ruleEngine != null) ruleEngine.stop();
        // Persist any unsaved sensor-value history before shutdown.
        if (SensorValueHistory.isDirty()) {
            try {
                AppConfig.saveSensorValueHistoryJson(this, SensorValueHistory.toJson());
                SensorValueHistory.markFlushed();
            } catch (Exception e) {
                LogBuffer.w("TelemetryService", "value history final flush failed: " + e.getMessage());
            }
        }
        shutdownExecutors();
        releaseWakeLock();
        releaseWifiLock();
        if (!explicitStopRequested) {
            try {
                BootReceiver.scheduleRestart(this, 500);
            } catch (Exception e) {
                LogBuffer.e("TelemetryService", "scheduleRestart in onDestroy failed: " + e.getMessage());
            }
        } else {
            LogBuffer.i("TelemetryService", "Explicit stop requested — not scheduling restart");
            explicitStopRequested = false;
        }
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        try {
            LogBuffer.i("TelemetryService", "onTaskRemoved — user removed task, keeping service alive");
            BootReceiver.scheduleRestart(this, 1000);
        } catch (Exception e) {
            LogBuffer.e("TelemetryService", "onTaskRemoved failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        super.onTaskRemoved(rootIntent);
    }

    private void dumpLogAfterBoot() {
        if (!running.get()) return;
        LogBuffer.i("TelemetryService", "Auto log dump started");
        new Thread(() -> {
            try {
                byte[] logBytes = LogExportHelper.buildLogBytes(TelemetryService.this);
                Uri uri = LogExportHelper.saveLogToDownloads(TelemetryService.this, logBytes);
                if (uri != null) {
                    LogBuffer.i("TelemetryService", "Auto log dump saved: " + uri);
                } else {
                    LogBuffer.w("TelemetryService", "Auto log dump failed to save");
                }
            } catch (Exception e) {
                LogBuffer.e("TelemetryService", "Auto log dump error: " + e.getMessage());
            }
        }).start();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_title),
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription(getString(R.string.notification_text));
            nm.createNotificationChannel(channel);
        }
    }

    private PendingIntent getNotificationIntent() {
        if (cachedNotificationIntent != null) return cachedNotificationIntent;
        Intent intent = new Intent(this, MainActivity.class);
        cachedNotificationIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        return cachedNotificationIntent;
    }

    private Notification buildNotification() {
        try {
            return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentIntent(getNotificationIntent())
                .setOngoing(true)
                .build();
        } catch (Exception e) {
            LogBuffer.e("TelemetryService", "buildNotification failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return new Notification.Builder(this).setSmallIcon(android.R.drawable.ic_menu_mylocation).build();
        }
    }

    private void startCommandPoller() {
        try {
            if (!AppConfig.isCarControlEnabled(this)) {
                LogBuffer.i("TelemetryService", "Car control from HA is disabled — not starting command poller");
                return;
            }
            if (commandPoller == null) {
                commandPoller = new CommandPoller(this);
                LogBuffer.i("TelemetryService", "CommandPoller instance created");
            }
            commandPoller.start();
            LogBuffer.i("TelemetryService", "Command poller started");
        } catch (Exception e) {
            LogBuffer.e("TelemetryService", "startCommandPoller failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void stopCommandPoller() {
        if (commandPoller != null) {
            try {
                commandPoller.stop();
                LogBuffer.i("TelemetryService", "Command poller stopped");
            } catch (Exception e) {
                LogBuffer.e("TelemetryService", "stopCommandPoller failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
            commandPoller = null;
        } else {
            LogBuffer.d("TelemetryService", "stopCommandPoller: no poller running");
        }
    }

    private void startTelemetryLoop() {
        telemetryExecutor.execute(() -> {
            while (running.get()) {
                if (!vehicleAsleep || BackgroundModeManager.isEnabled(this)) {
                    refreshData();
                } else {
                    LogBuffer.d("TelemetryService", "Vehicle asleep, checking less frequently");
                }
                try {
                    long interval = REFRESH_INTERVAL_MS;
                    if (vehicleAsleep && !BackgroundModeManager.isEnabled(this)) {
                        interval = VEHICLE_ASLEEP_INTERVAL_MS;
                    }
                    Thread.sleep(interval);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
    }

    private void startFlushLoop() {
        flushExecutor.execute(() -> {
            while (running.get()) {
                try {
                    Thread.sleep(FLUSH_INTERVAL_MS);
                } catch (InterruptedException e) {
                    break;
                }
                if (!running.get()) break;
                if (AppConfig.isHassEnabled(this)) {
                    HassClient.flush(this, (success, msg) -> {
                        if (!success) {
                            LogBuffer.w("TelemetryService", "HA flush failed: " + msg);
                        }
                    });
                }
                // Persist the dynamic sensor-value dictionary at most once
                // per minute when it changed (see SensorValueHistory).
                if (SensorValueHistory.needsFlush(System.currentTimeMillis())) {
                    try {
                        AppConfig.saveSensorValueHistoryJson(this, SensorValueHistory.toJson());
                        SensorValueHistory.markFlushed();
                    } catch (Exception e) {
                        LogBuffer.w("TelemetryService", "value history flush failed: " + e.getMessage());
                    }
                }
            }
        });
    }

    private void refreshData() {
        try {
            // Defensive copy: CANDataReader creates subList() views that executor
            // workers iterate, while the main thread may add geofence items via
            // ensureGeofenceItem — sharing the live list caused CME (build 156+).
            CANDataReader.refreshData(this, new ArrayList<>(knownItems), CANDataReader.SOURCE_ALL,
                new CANDataReader.Callback() {
                    @Override
                    public void onData(List<CANDataItem> items, long timestamp, int source) {
                        try {
                            applySystemValues(items);
                            collectSnapshot(items);
                            mainHandler.post(() -> {
                                try {
                                    updateNotification(getString(R.string.notification_active, items.size()));
                                    if (callback != null) {
                                        callback.onDataUpdated(items, timestamp);
                                    }
                                } catch (Exception e) {
                                    LogBuffer.e("TelemetryService", "onData UI post failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                                }
                            });
                        } catch (Exception e) {
                            LogBuffer.e("TelemetryService", "onData failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                        }
                    }

                    @Override
                    public void onError(String message, int source) {
                        try {
                            mainHandler.post(() -> {
                                try {
                                    updateNotification(getString(R.string.notification_error, message));
                                    if (callback != null) {
                                        callback.onError(message);
                                    }
                                } catch (Exception e) {
                                    LogBuffer.e("TelemetryService", "onError UI post failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                                }
                            });
                        } catch (Exception e) {
                            LogBuffer.e("TelemetryService", "onError dispatch failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                        }
                    }
                });
        } catch (Exception e) {
            LogBuffer.e("TelemetryService", "refreshData failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * System-channel values (GPS/device) never pass through the CAN cycles —
     * LocationSource writes them into the SnapshotStore. Overlay them onto
     * the item list so the telemetry UI, rules and history see them too.
     */
    private void applySystemValues(List<CANDataItem> items) {
        if (items == null) return;
        long now = System.currentTimeMillis();
        for (CANDataItem item : items) {
            if (item == null || item.key == null) continue;
            String k = item.key;
            if (!k.startsWith("location_") && !"device_battery".equals(k)) continue;
            String v = snapshotStore.get(k);
            if (v != null && !v.isEmpty()) {
                item.value = v;
                item.lastUpdate = now;
            }
        }
    }

    private void collectSnapshot(List<CANDataItem> items) {
        try {
            JSONObject sig = new JSONObject();
            boolean foundPowerState = false;
            for (CANDataItem item : items) {
                String key = item.key;
                if (key == null || key.isEmpty()) continue;
                if (item.value == null || "---".equals(item.value)) continue;

                // Signals that DiPlus currently reports as unsupported are still
                // polled (they may become available after a firmware update) but
                // must not be forwarded to Home Assistant until they return valid data.
                boolean isPowerState = "power_state".equals(key);
                if (!isPowerState && CANDataReader.isUnsupportedSignal(this, item.diplusName)) {
                    continue;
                }

                // Respect user-defined attribute filter. power_state is always
                // needed internally for sleep logic, but still respects the filter
                // when sending to HA.
                if (!isPowerState && !AppConfig.isSignalEnabled(this, key)) {
                    continue;
                }

                // Normalize decimal separator: some DiLink locales use comma as the
                // decimal point. HA and JSON expect a dot.
                String rawValue = item.value.replace(',', '.');

                // Check power state for sleep logic
                if (isPowerState) {
                    String translated = SignalTranslator.translateEnumValue(key, rawValue);
                    boolean isOff = SignalTranslator.isOffState(translated);
                    if (isOff && !vehicleAsleep) {
                        LogBuffer.i("TelemetryService", "Vehicle off, pausing telemetry");
                        vehicleAsleep = true;
                    } else if (!isOff && vehicleAsleep) {
                        LogBuffer.i("TelemetryService", "Vehicle on, resuming telemetry");
                        vehicleAsleep = false;
                    }
                    foundPowerState = true;
                }

                try {
                    boolean isEnum = "enum".equals(item.rawData);
                    String translated = SignalTranslator.translateEnumValue(key, rawValue);
                    if (isEnum) {
                        sig.put(key, translated);
                    } else {
                        Object num = parseNumeric(rawValue, translated);
                        if (num != null) {
                            sig.put(key, num);
                        }
                    }
                    cachedSignalValues.put(key, translated);
                    SensorValueHistory.recordValue(key, translated);
                } catch (Exception e) {
                    LogBuffer.d("TelemetryService", "Skipping signal " + key + " with value '" + rawValue + "': " + e.getMessage());
                }
            }

            // If no power_state in this batch, assume vehicle is active
            if (!foundPowerState && vehicleAsleep) {
                vehicleAsleep = false;
                LogBuffer.i("TelemetryService", "No power_state signal, assuming vehicle active");
            }

            if (!hasValidLocation()) {
                long now = System.currentTimeMillis();
                if (now - lastLocationRetryMs > LOCATION_RETRY_INTERVAL_MS) {
                    lastLocationRetryMs = now;
                    LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                    if (lm != null) {
                        updateLastKnownLocation(lm);
                    }
                }
                LogBuffer.d("TelemetryService", "collectSnapshot: no valid GPS fix yet");
            }

            // Static metadata sensors
            sig.put("app_version", AppInfo.getVersionString(this));
            addWifiInfo(sig);

            // Virtual geofence states (inside/outside) are computed locally from
            // GPS in evaluateGeofences and never pass through the DiPlus CAN
            // pipeline (their items have no diplusName and are dropped from batch
            // reads), so inject them into the snapshot directly from the cache.
            // geo_<id>_name keys carry the zone name for friendly naming in HA.
            for (Map.Entry<String, String> e : cachedSignalValues.entrySet()) {
                String key = e.getKey();
                String value = e.getValue();
                if (key == null || !key.startsWith("geo_")) continue;
                if (value == null || value.isEmpty() || "---".equals(value)) continue;
                sig.put(key, value);
            }

            // Fix time (ms) is only meaningful when we have a fix; pass it in
            // epoch seconds so HA can attribute the location to the real
            // measurement moment instead of the snapshot collection time.
            long fixTimeSec = hasValidLocation() ? lastLocTime / 1000 : 0;
            HassClient.collectSnapshot(this, lastLat, lastLon, lastAccuracy, fixTimeSec, sig.toString());
        } catch (Exception e) {
            LogBuffer.e("TelemetryService", "collectSnapshot failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * Builds the signal item set. Once a vehicle profile is known the set is
     * taken from the registry (via SourceManager), otherwise the legacy
     * SIGNAL_REGISTRY list is used.
     */
    private List<CANDataItem> buildKnownItems() {
        if (AppConfig.getSelectedProfile(this) != null) {
            try {
                SourceManager sm = new SourceManager(RegistryStore.load(this),
                        AppConfig.getActiveChannels(this),
                        AppConfig.getSelectedProfile(this), null);
                return sm.buildSignalItems();
            } catch (Exception e) {
                LogBuffer.e("TelemetryService", "registry items: " + e.getMessage());
            }
        }
        return CANDataReader.createSignalItems();
    }

    /** 1 Hz snapshot ticker: keeps GPS/device at 1 Hz, batched via HassClient queue. */
    private void startSnapshotTicker() {
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    buildAndEnqueueSnapshot();
                } catch (Exception e) {
                    LogBuffer.e("TelemetryService", "snapshot ticker: " + e.getMessage());
                }
                mainHandler.postDelayed(this, SNAPSHOT_INTERVAL_MS);
            }
        }, SNAPSHOT_INTERVAL_MS);
    }

    /**
     * Assembles a snapshot from the latest location/device values and the most
     * recent auto-sensor cache, always including the GPS minimum, and enqueues
     * it for batched transmission.
     */
    private void buildAndEnqueueSnapshot() {
        try {
            int battery = -1;
            try {
                Intent bat = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                if (bat != null) {
                    int level = bat.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale = bat.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                    if (level >= 0 && scale > 0) battery = (int) (100f * level / scale);
                }
            } catch (Exception ignored) {}
            if (battery >= 0) locationSource.onDeviceBattery(battery);
        } catch (Exception ignored) {}

        JSONObject sig = new JSONObject();
        try {
            // GPS minimum — always included in the integration
            for (String k : new String[]{"location_lat", "location_lon", "location_speed",
                    "location_bearing", "location_altitude", "location_accuracy", "location_provider"}) {
                String v = snapshotStore.get(k);
                if (v != null) sig.put(k, v);
            }
            String batt = snapshotStore.get("device_battery");
            if (batt != null) sig.put("device_battery", batt);
            // most recent auto-sensor values
            for (java.util.Map.Entry<String, String> e : cachedSignalValues.entrySet()) {
                String key = e.getKey();
                if (key == null || key.startsWith("geo_") || key.endsWith("_name")) continue;
                String v = e.getValue();
                if (v == null || "---".equals(v)) continue;
                // Never forward non-finite numerics ("nan"/"inf"): HA rejects
                // them for measurement sensors (ValueError).
                if (isNonFiniteNumeric(v)) continue;
                sig.put(key, v);
            }
        } catch (org.json.JSONException e) {
            LogBuffer.e("TelemetryService", "snapshot build: " + e.getMessage());
        }
        SnapshotStore.Loc l = snapshotStore.getLocation();
        HassClient.collectSnapshot(this, l.lat, l.lon, l.accuracy,
                l.timeMs > 0 ? l.timeMs / 1000 : 0, sig.toString());
    }

    /** True when a string is a numeric literal that is NaN/Infinity. */
    private static boolean isNonFiniteNumeric(String v) {
        if (v == null) return false;
        String s = v.trim().toLowerCase(java.util.Locale.US);
        return s.equals("nan") || s.equals("inf") || s.equals("+inf") || s.equals("-inf")
                || s.equals("infinity") || s.equals("∞");
    }

    private Object parseNumeric(String value, String fallback) {
        if (value == null || value.isEmpty()) return fallback;
        try {
            // Preserve integers without a fractional part to avoid "5.0" in HA.
            if (value.matches("-?\\d+")) {
                return Integer.parseInt(value);
            }
            double d = Double.parseDouble(value);
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                return null;
            }
            return d;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
            || checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Called by the UI after the user grants location permission: the service
     * may already be running (permission dialog appears over it), in which
     * case GPS updates must be (re)started without a service restart.
     */
    public static void onLocationPermissionMaybeGranted() {
        TelemetryService svc = MainActivity.getTelemetryService();
        if (svc == null) return;
        svc.mainHandler.post(() -> {
            if (svc.hasLocationPermission()) {
                LogBuffer.i("TelemetryService", "Location permission granted, starting GPS updates");
                svc.startLocationUpdates();
            }
        });
    }

    private void startLocationUpdates() {
        try {
            if (!hasLocationPermission()) {
                LogBuffer.w("TelemetryService", "Location permission not granted, skipping GPS updates");
                return;
            }
            // Idempotent: drop a previous registration before re-requesting.
            if (locationListener != null) {
                LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                if (lm != null) lm.removeUpdates(locationListener);
                locationListener = null;
            }

            // Make sure we never register multiple listeners after service restart.
            stopLocationUpdates();

            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) {
                LogBuffer.w("TelemetryService", "LocationManager is null");
                return;
            }

            locationListener = new LocationListener() {
                @Override
                public void onLocationChanged(Location loc) {
                    if (loc == null) return;

                    long time = loc.getTime();
                    double lat = loc.getLatitude();
                    double lon = loc.getLongitude();
                    float accuracy = loc.getAccuracy();
                    String provider = loc.getProvider();

                    // Ignore very inaccurate fixes.
                    if (accuracy > 50) {
                        LogBuffer.d("TelemetryService", "Ignoring low-accuracy location (±" + accuracy + "m) from " + provider);
                        return;
                    }

                    // De-duplicate identical locations delivered twice.
                    if (Math.abs(time - lastLocTime) < 100
                            && Math.abs(lat - lastLat) < 1e-6
                            && Math.abs(lon - lastLon) < 1e-6) {
                        LogBuffer.d("TelemetryService", "Ignoring duplicate location from " + provider);
                        return;
                    }

                    // If we have a recent accurate GPS fix, ignore coarse network updates.
                    if (LocationManager.NETWORK_PROVIDER.equals(provider)
                            && lastAccuracy > 0 && lastAccuracy <= 15
                            && (time - lastLocTime) < 30000) {
                        LogBuffer.d("TelemetryService", "Ignoring network location, recent GPS is more accurate");
                        return;
                    }

                    lastLat = lat;
                    lastLon = lon;
                    lastAccuracy = accuracy;
                    lastLocTime = time;
                    locationSource.onLocation(lat, lon,
                            loc.hasSpeed() ? loc.getSpeed() : 0f,
                            loc.hasBearing() ? loc.getBearing() : 0f,
                            loc.hasAltitude() ? loc.getAltitude() : 0.0,
                            accuracy, provider, time);
                    LogBuffer.i("TelemetryService", "GPS update: " + lastLat + ", " + lastLon
                            + " (±" + lastAccuracy + "m) from " + provider);
                    evaluateGeofences(lat, lon);
                }

                @Override
                public void onProviderEnabled(String provider) {
                    LogBuffer.i("TelemetryService", "GPS provider enabled: " + provider);
                }

                @Override
                public void onProviderDisabled(String provider) {
                    LogBuffer.w("TelemetryService", "GPS provider disabled: " + provider);
                }
            };

            for (String provider : new String[]{
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            }) {
                try {
                    if (!lm.isProviderEnabled(provider)) continue;
                    lm.requestLocationUpdates(provider, LOCATION_MIN_TIME_MS, LOCATION_MIN_DISTANCE_M,
                            locationListener, Looper.getMainLooper());
                } catch (Exception ignored) {}
            }

            updateLastKnownLocation(lm);
        } catch (Exception e) {
            LogBuffer.e("TelemetryService", "Location start error: " + e.getMessage());
        }
    }

    private void updateLastKnownLocation(LocationManager lm) {
        try {
            Location loc = null;
            try {
                loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            } catch (Exception ignored) {}
            if (loc == null) {
                try {
                    loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                } catch (Exception ignored) {}
            }
            if (loc == null) {
                try {
                    loc = lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
                } catch (Exception ignored) {}
            }
            if (loc != null) {
                lastLat = loc.getLatitude();
                lastLon = loc.getLongitude();
                lastAccuracy = loc.getAccuracy();
                lastLocTime = loc.getTime();
                LogBuffer.i("TelemetryService", "Last known GPS: " + lastLat + ", " + lastLon
                        + " (±" + lastAccuracy + "m) from " + loc.getProvider());
            } else {
                LogBuffer.w("TelemetryService", "No last known GPS location available");
            }
        } catch (Exception ignored) {}
    }

    private void stopLocationUpdates() {
        if (locationListener != null) {
            try {
                LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                if (lm != null) {
                    lm.removeUpdates(locationListener);
                }
            } catch (Exception e) {
                LogBuffer.e("TelemetryService", "Location stop error: " + e.getMessage());
            }
            locationListener = null;
        }
    }

    private void registerNetworkCallback() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return;
            NetworkRequest req = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    if (!AppConfig.isHassEnabled(TelemetryService.this)) return;
                    long now = System.currentTimeMillis();
                    if (now - lastNetworkFlushMs < NETWORK_FLUSH_DEBOUNCE_MS) {
                        LogBuffer.d("TelemetryService", "Network available, flush debounced");
                        return;
                    }
                    lastNetworkFlushMs = now;
                    HassClient.onNetworkAvailable(TelemetryService.this);
                }
            };
            cm.registerNetworkCallback(req, networkCallback);
        } catch (Exception e) {
            LogBuffer.e("TelemetryService", "Network callback registration failed: " + e.getMessage());
        }
    }

    private void unregisterNetworkCallback() {
        if (networkCallback != null) {
            try {
                ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                if (cm != null) {
                    cm.unregisterNetworkCallback(networkCallback);
                }
            } catch (Exception e) {
                LogBuffer.e("TelemetryService", "Network callback unregister failed: " + e.getMessage());
            }
            networkCallback = null;
        }
    }

    private void updateNotification(String text) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) {
                LogBuffer.w("TelemetryService", "NotificationManager is null");
                return;
            }
            Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Car2Hass")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentIntent(getNotificationIntent())
                .setOngoing(true);

            nm.notify(NOTIFICATION_ID, builder.build());
            LogBuffer.d("TelemetryService", "Notification updated: " + text);
        } catch (Exception e) {
            LogBuffer.e("TelemetryService", "Notification update failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void addWifiInfo(JSONObject sig) {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm == null) return;
            android.net.wifi.WifiInfo info = wm.getConnectionInfo();
            if (info == null) return;
            String ssid = info.getSSID();
            if (ssid != null && !"<unknown ssid>".equals(ssid) && !"0x".equals(ssid)) {
                sig.put("wifi_ssid", ssid.replace("\"", ""));
            }
            String bssid = info.getBSSID();
            if (bssid != null) {
                sig.put("wifi_bssid", bssid);
            }
            int rssi = info.getRssi();
            if (rssi != -127 && rssi != Integer.MAX_VALUE) {
                sig.put("wifi_rssi", rssi);
            }
        } catch (Exception e) {
            LogBuffer.d("TelemetryService", "addWifiInfo failed: " + e.getMessage());
        }
    }

    public static void start(Context context) {
        Intent intent = new Intent(context, TelemetryService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stop(Context context) {
        explicitStopRequested = true;
        Intent intent = new Intent(context, TelemetryService.class);
        context.stopService(intent);
    }

    private void evaluateGeofences(double lat, double lon) {
        try {
            List<GeofenceZone> zones = AppConfig.loadGeofences(this);
            boolean visitedChanged = false;
            long now = System.currentTimeMillis();
            // Collect all active zone ids to clean up stale keys.
            Set<String> activeZoneIds = new HashSet<>();
            for (GeofenceZone z : zones) {
                activeZoneIds.add(z.id);
                float[] results = new float[1];
                android.location.Location.distanceBetween(lat, lon, z.latitude, z.longitude, results);
                float dist = results[0];
                String state = dist <= z.radius ? "inside" : "outside";
                String key = "geo_" + z.id;
                String prevState = cachedSignalValues.get(key);
                if (!state.equals(prevState)) {
                    LogBuffer.i("TelemetryService", "Geofence '" + z.name + "': "
                        + (prevState != null ? prevState : "unknown") + "→" + state);
                }
                if ("inside".equals(state) && "outside".equals(prevState)) {
                    // outside → inside transition: the car just entered the zone.
                    z.lastVisitedAtMs = now;
                    visitedChanged = true;
                }
                cachedSignalValues.put(key, state);
                // Zone name travels alongside the state so Home Assistant can
                // build a friendly entity name for the dynamic geo_<id> key.
                cachedSignalValues.put(key + "_name", z.name);
                ensureGeofenceItem(key);
            }
            // Remove stale geo_ keys for zones that no longer exist.
            for (Iterator<Map.Entry<String, String>> it = cachedSignalValues.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, String> entry = it.next();
                String k = entry.getKey();
                if (k.startsWith("geo_") && !k.endsWith("_name")) {
                    String zoneId = k.substring("geo_".length());
                    if (!activeZoneIds.contains(zoneId)) {
                        it.remove();
                        cachedSignalValues.remove(k + "_name");
                        LogBuffer.i("TelemetryService", "Removed stale geo_ keys for deleted zone: " + zoneId);
                    }
                }
            }
            if (visitedChanged) {
                AppConfig.saveGeofences(this, zones);
            }
        } catch (Exception e) {
            LogBuffer.w("TelemetryService", "evaluateGeofences error: " + e.getMessage());
        }
    }

    // Pre-register one telemetry item per configured geofence zone so the
    // knownItems list stays stable while CANDataReader workers iterate it.
    // ensureGeofenceItem remains as a fallback for zones added later.
    private void preregisterGeofenceItems() {
        try {
            for (GeofenceZone z : AppConfig.loadGeofences(this)) {
                ensureGeofenceItem("geo_" + z.id);
            }
        } catch (Exception e) {
            LogBuffer.w("TelemetryService", "preregisterGeofenceItems failed: " + e.getMessage());
        }
    }

    private void ensureGeofenceItem(String key) {
        for (CANDataItem item : knownItems) {
            if (key.equals(item.key)) return;
        }
        CANDataItem item = new CANDataItem(0, key, "", -1);
        item.key = key;
        // Geofence states are virtual (computed locally from GPS) and never read
        // via the DiPlus getDiPars/getVal pipeline. Leave diplusName null so the
        // item is skipped when building batch request templates; otherwise the
        // "0x000" fallback name is sent to DiPlus, which answers
        // {"success":false} and bloats the logs with group errors.
        item.diplusName = null;
        item.value = cachedSignalValues.getOrDefault(key, "---");
        knownItems.add(item);
    }
}
