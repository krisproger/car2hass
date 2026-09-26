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
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
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
import com.car2hass.CloudSyncClient;
import com.car2hass.CommandPoller;
import com.car2hass.DerivedAggregates;
import com.car2hass.ProbeUploader;
import com.car2hass.vehicle.BydCloudChannel;
import com.car2hass.vehicle.DataChannel;
import com.car2hass.vehicle.DiPlusChannel;
import com.car2hass.vehicle.DiPlusPushChannel;
import com.car2hass.vehicle.ExperimentalChannel;
import com.car2hass.vehicle.LocationPolicy;
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
    private static final long LAST_VALUES_SAVE_INTERVAL_MS = 30000;
    private static final int MAX_LAST_VALUES = 500;

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
    /** Shared signal store (new architecture): workers write here, tab/snapshot read. */
    public final com.car2hass.vehicle.ValueStore valueStore =
            new com.car2hass.vehicle.ValueStore();
    /** One worker instance per enabled channel; reconciled with settings on every cycle. */
    private com.car2hass.vehicle.ChannelWorkerRegistry workerRegistry;
    private final SnapshotStore snapshotStore = new SnapshotStore();
    private final LocationSource locationSource = new LocationSource(snapshotStore);

    public RuleEngine getRuleEngine() {
        return ruleEngine;
    }

    /** Status snapshot of a running channel worker, or null when the channel has none. */
    public com.car2hass.vehicle.ChannelWorkerStatus channelStatus(String channelId) {
        com.car2hass.vehicle.ChannelWorkerRegistry reg = workerRegistry;
        return reg == null ? null : reg.status(channelId);
    }

    /** Status snapshots of every running worker (worker-state page). */
    public java.util.List<com.car2hass.vehicle.ChannelWorkerStatus> channelStatuses() {
        com.car2hass.vehicle.ChannelWorkerRegistry reg = workerRegistry;
        return reg == null ? java.util.Collections.emptyList() : reg.statuses();
    }

    /** Number of live worker threads (running channels). */
    public int liveWorkerThreadCount() {
        com.car2hass.vehicle.ChannelWorkerRegistry reg = workerRegistry;
        return reg == null ? 0 : reg.runningChannels().size();
    }

    /** Number of distinct signals currently held in the shared ValueStore. */
    public int valueStoreSize() {
        return valueStore.snapshot().size();
    }

    /** Pending upload-queue entries (logs + probe reports). */
    public int uploadQueueSize() {
        try {
            return com.car2hass.UploadQueue.load(this).size();
        } catch (Exception e) {
            return 0;
        }
    }

    private double lastLat = Double.NaN;
    private double lastLon = Double.NaN;
    private float lastAccuracy = 0;
    private long lastLocTime = 0;
    private String lastProvider = "";
    private volatile long lastNetworkFlushMs = 0;
    private volatile long lastLastValuesSaveMs = 0;
    private volatile boolean lastValuesDirty = false;
    private static final long NETWORK_FLUSH_DEBOUNCE_MS = 30000;
    private static final long VEHICLE_ASLEEP_INTERVAL_MS = 30000;
    private static final long LOCATION_MIN_TIME_MS = 3000;
    private static final float LOCATION_MIN_DISTANCE_M = 10f;
    private static final long AUTO_LOG_DUMP_DELAY_MS = 5 * 60 * 1000; // 5 minutes after start

    private volatile boolean vehicleAsleep = false;
    private volatile boolean foregroundTimedOut = false;
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
        // Restore before knownItems/rules are built so the telemetry view and the
        // rules engine see the previous session's values from the first tick.
        com.car2hass.vehicle.ValueStore.setMain(valueStore);
        restoreLastValues();
        knownItems = buildKnownItems();
        preregisterGeofenceItems();
        preregisterDerivedItems();
        shutdownExecutors();
        telemetryExecutor = Executors.newSingleThreadExecutor();
        flushExecutor = Executors.newSingleThreadExecutor();
        ruleEngine = new RuleEngine(getApplicationContext(), valueStore::get, valueStore::isRestored);
        // Event-driven rules: re-evaluate affected rules as soon as a channel
        // writes a changed value, instead of waiting for the 1s engine tick.
        valueStore.addListener((key, value, source) -> {
            // Restored values are not a change: never treat them as an edge.
            if (com.car2hass.vehicle.ValueStore.SOURCE_RESTORED.equals(source)) return;
            lastValuesDirty = true;
            if (ruleEngine != null) ruleEngine.signalChanged(key);
            // Derived door/window aggregates must react to a source change at once,
            // not only on the CAN / 1 Hz snapshot cycles.
            if (isDerivedSourceKey(key)) refreshDerivedSensors();
        });
        SensorValueHistory.ensureLoaded(AppConfig.getSensorValueHistoryJson(this));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        explicitStopRequested = false;
        foregroundTimedOut = false;
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
            // Worker-per-channel: one thread per enabled channel, each writing to
            // the shared ValueStore tagged with its channel source. System is
            // always on (plain Android device has telemetry); the rest follow the
            // settings channel checkboxes.
            workerRegistry = new com.car2hass.vehicle.ChannelWorkerRegistry(
                    new com.car2hass.vehicle.ChannelWorkerFactory(this, valueStore,
                            new ArrayList<>(knownItems)));
            workerRegistry.sync(enabledChannelIds());
            // Baseline GPS: ensure telemetry carries a location even before the
            // first live fix (Car Scanner-style "always have a position").
            try {
                LocationManager lm0 = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                if (lm0 != null) updateLastKnownLocation(lm0);
            } catch (Exception ignored) {}
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
     * Android 15+ (API 35) calls this when an FGS type exceeds its time budget
     * (dataSync/mediaProcessing). The service must drop the foreground state
     * within a few seconds or the system crashes the process with
     * ForegroundServiceDidNotStopInTimeException. Stop gracefully here; the
     * restart is delayed in onDestroy so a recurring timeout cannot spin.
     */
    @Override
    public void onTimeout(int startId, int fgsType) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return;
        foregroundTimedOut = true;
        LogBuffer.w("TelemetryService", "onTimeout startId=" + startId + " fgsType=" + fgsType
                + " — stopping foreground gracefully");
        try {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } catch (Exception e) {
            LogBuffer.e("TelemetryService", "onTimeout stopForeground failed: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        stopSelf(startId);
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
                // Automatic research is scoped to the channels the user enabled;
                // other channels are only probed from the manual research button.
                List<DataChannel> channels = buildEnabledResearchChannels();
                VehicleProfile profile = AppConfig.getVehicleProfile(this);
                if (full) {
                    LogBuffer.i("TelemetryService", "Probe: full research (runs=" + runs + ")");
                    VehicleResearch.ResearchOutcome outcome = VehicleResearch.runWithRegistry(this,
                            RegistryStore.load(this), channels, profile, null, new SignalProber(),
                            (ctx, p, a, rp) -> AppConfig.saveProbeResult(ctx, p,
                                    com.car2hass.vehicle.ResearchUiModel.unionActive(
                                            AppConfig.getActiveChannels(ctx), a), rp));
                    registerDiscoveredChannels(outcome.report);
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
        // A live channel becomes visible in the settings source list so the user
        // can enable it; it is never auto-enabled.
        registerDiscoveredChannels(java.util.Collections.emptyList(), alive);
    }

    /** Channels probed automatically: the user-enabled set (system always on). */
    private List<DataChannel> buildEnabledResearchChannels() {
        List<DataChannel> channels = com.car2hass.vehicle.ChannelCatalog.createFor(enabledChannelIds());
        if (channels.isEmpty()) channels.add(new com.car2hass.vehicle.DiPlusChannel());
        return channels;
    }

    /**
     * Makes channels that yielded sensor data visible in the settings source
     * list (union with what the user already added). Never enables them.
     */
    private void registerDiscoveredChannels(org.json.JSONObject report) {
        java.util.LinkedHashSet<String> discovered = new java.util.LinkedHashSet<>();
        if (report != null) {
            try {
                org.json.JSONObject sensors = report.optJSONObject("sensors");
                if (sensors != null) {
                    java.util.Iterator<String> keys = sensors.keys();
                    while (keys.hasNext()) {
                        org.json.JSONObject per = sensors.optJSONObject(keys.next());
                        if (per == null) continue;
                        java.util.Iterator<String> chans = per.keys();
                        while (chans.hasNext()) {
                            String ch = chans.next();
                            if ("ok".equals(per.optString(ch))) discovered.add(ch);
                        }
                    }
                }
            } catch (Exception e) {
                LogBuffer.d("TelemetryService", "registerDiscoveredChannels: " + e.getMessage());
                return;
            }
        }
        registerDiscoveredChannels(discovered, null);
    }

    private void registerDiscoveredChannels(java.util.Collection<String> fromReport,
                                            java.util.Collection<String> extra) {
        java.util.LinkedHashSet<String> discovered = new java.util.LinkedHashSet<>();
        if (fromReport != null) discovered.addAll(fromReport);
        if (extra != null) discovered.addAll(extra);
        discovered.remove("system");
        if (discovered.isEmpty()) return;
        try {
            java.util.LinkedHashSet<String> added =
                    new java.util.LinkedHashSet<>(AppConfig.getAddedSources(this));
            int before = added.size();
            added.addAll(discovered);
            if (added.size() != before) {
                AppConfig.setAddedSources(this, new ArrayList<>(added));
                LogBuffer.i("TelemetryService", "Channels with data added to settings: " + discovered);
            }
        } catch (Exception e) {
            LogBuffer.d("TelemetryService", "registerDiscoveredChannels: " + e.getMessage());
        }
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
        saveLastValues();
        if (workerRegistry != null) {
            workerRegistry.stopAll();
            workerRegistry = null;
        }
        shutdownExecutors();
        releaseWakeLock();
        releaseWifiLock();
        if (!explicitStopRequested) {
            try {
                BootReceiver.scheduleRestart(this, foregroundTimedOut ? 60_000 : 500);
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
        LogBuffer.i("TelemetryService", "Auto log flush after boot");
        new Thread(() -> {
            try {
                com.car2hass.LogManager lm = com.car2hass.LogManager.get();
                if (lm != null) lm.flushNow();
            } catch (Exception e) {
                LogBuffer.e("TelemetryService", "Auto log flush error: " + e.getMessage());
            }
        }, "log-flush").start();
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
                // Reconcile the worker set with the settings checkboxes: a newly
                // enabled channel starts polling, a disabled one stops at once.
                syncWorkers();
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
                // Persist last-known values periodically (not only on exit) so a
                // kill/restart can still restore the previous session immediately.
                long now = System.currentTimeMillis();
                if (lastValuesDirty && now - lastLastValuesSaveMs >= LAST_VALUES_SAVE_INTERVAL_MS) {
                    lastLastValuesSaveMs = now;
                    lastValuesDirty = false;
                    saveLastValues();
                }
            }
        });
    }

    private void refreshData() {
        try {
            // The per-channel workers own polling and have already written their
            // latest values into the ValueStore. This pass only assembles the
            // snapshot/rules view from the store — it never waits for a channel.
            List<CANDataItem> items = assembleItemsFromStore();
            applySystemValues(items);
            collectSnapshot(items);
            long timestamp = System.currentTimeMillis();
            boolean any = false;
            for (CANDataItem it : items) {
                if (it != null && it.value != null && !"---".equals(it.value)) { any = true; break; }
            }
            final boolean hasData = any;
            mainHandler.post(() -> {
                try {
                    if (hasData) {
                        updateNotification(getString(R.string.notification_active, items.size()));
                    } else {
                        updateNotification(hasSystemValues()
                                ? getString(R.string.notification_system_only)
                                : getString(R.string.notification_error, "no channel data"));
                    }
                    if (callback != null) {
                        if (hasData) callback.onDataUpdated(items, timestamp);
                        else callback.onError("No data from enabled channels");
                    }
                } catch (Exception e) {
                    LogBuffer.e("TelemetryService", "refreshData UI post failed: "
                            + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            });
        } catch (Exception e) {
            LogBuffer.e("TelemetryService", "refreshData failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * Rebuilds the item set from the raw values the channel workers wrote, so the
     * existing translation/snapshot pipeline keeps working without polling any
     * channel on this thread.
     */
    private List<CANDataItem> assembleItemsFromStore() {
        java.util.Map<String, CANDataItem> byKey = new java.util.LinkedHashMap<>();
        synchronized (knownItems) {
            for (CANDataItem known : knownItems) {
                if (known == null || known.key == null) continue;
                CANDataItem copy = new CANDataItem(known.canId, known.name, known.unit,
                        known.route, known.diplusName);
                copy.key = known.key;
                copy.rawData = known.rawData;
                copy.sourceChannel = valueStore.sourceOf(known.key);
                byKey.put(known.key, copy);
            }
        }
        for (java.util.Map.Entry<String, String> e
                : com.car2hass.vehicle.ValueStore.rawSnapshot().entrySet()) {
            String key = e.getKey();
            String raw = e.getValue();
            if (key == null || raw == null || raw.isEmpty() || "---".equals(raw)) continue;
            CANDataItem item = byKey.get(key);
            if (item == null) {
                item = new CANDataItem(0, key, "", 0);
                item.key = key;
                item.diplusName = null;
                byKey.put(key, item);
            }
            item.value = raw;
            if (item.sourceChannel == null) item.sourceChannel = valueStore.sourceOf(key);
        }
        return new ArrayList<>(byKey.values());
    }

    /** Reconciles the running workers with the settings channel checkboxes. */
    private void syncWorkers() {
        com.car2hass.vehicle.ChannelWorkerRegistry reg = workerRegistry;
        if (reg == null) return;
        try {
            reg.sync(enabledChannelIds());
        } catch (Exception e) {
            LogBuffer.d("TelemetryService", "syncWorkers: " + e.getMessage());
        }
    }

    /**
     * Channel ids whose worker must run. System is always on; every other channel
     * follows the settings checkboxes (an unchecked channel is never polled).
     */
    private List<String> enabledChannelIds() {
        List<String> out = new ArrayList<>();
        out.add("system");
        for (String id : AppConfig.getActiveChannels(this)) {
            if (id == null || id.isEmpty() || "system".equals(id)) continue;
            if ("obd".equals(id) && !AppConfig.isObdEnabled(this)) continue;
            String normalized = com.car2hass.vehicle.ChannelWorkerFactory.normalize(id);
            if (!out.contains(normalized)) out.add(normalized);
        }
        return out;
    }

    /** True when at least one sensor value came from the system channel (GPS/device). */
    private boolean hasSystemValues() {
        for (java.util.Map.Entry<String, String> e : valueStore.entries()) {
            if ("system".equals(valueStore.sourceOf(e.getKey()))) return true;
        }
        return false;
    }

    /** Restore the last-known values so the UI shows them before the first CAN cycle. */
    private void restoreLastValues() {
        try {
            String json = AppConfig.getLastValuesJson(this);
            if (json == null || json.isEmpty()) return;
            org.json.JSONObject obj = new org.json.JSONObject(json);
            java.util.Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                String v = obj.optString(k);
                if (v == null || v.isEmpty() || "---".equals(v)) continue;
                valueStore.put(k, v, com.car2hass.vehicle.ValueStore.SOURCE_RESTORED);
            }
            LogBuffer.i("TelemetryService", "Restored last values: " + obj.length());
        } catch (Exception e) {
            LogBuffer.d("TelemetryService", "restore last values: " + e.getMessage());
        }
    }

    private void saveLastValues() {
        try {
            org.json.JSONObject obj = new org.json.JSONObject();
            int saved = 0;
            for (java.util.Map.Entry<String, String> e : valueStore.snapshot().entrySet()) {
                if (saved >= MAX_LAST_VALUES) break;
                if (e.getKey() == null || e.getValue() == null || e.getValue().isEmpty()
                        || "---".equals(e.getValue())) continue;
                obj.put(e.getKey(), e.getValue());
                saved++;
            }
            AppConfig.saveLastValuesJson(this, obj.toString());
        } catch (Exception e) {
            LogBuffer.d("TelemetryService", "save last values: " + e.getMessage());
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
            if (!k.startsWith("location_") && !"device_battery".equals(k)
                    && !"device_pressure".equals(k)
                    && !"system_media_volume".equals(k)) continue;
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

                // Locally computed sensors (derived door/window aggregates, virtual
                // geofences) are already final values — never run them through the
                // DiPlus enum translation (they have no labels -> warning spam).
                if (isDerivedKey(key) || key.startsWith("geo_")) continue;

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
                    String src = item.sourceChannel != null ? item.sourceChannel : valueStore.sourceOf(key);
                    valueStore.put(key, translated, src != null ? src : "channel");
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

            boolean fixStale = !hasValidLocation()
                    || System.currentTimeMillis() - lastLocTime > 30_000L;
            if (fixStale) {
                ensureLocationBaseline();
                if (!hasValidLocation()) {
                    LogBuffer.d("TelemetryService", "collectSnapshot: no valid GPS fix yet");
                }
            }

            // Static metadata sensors
            sig.put("app_version", AppInfo.getVersionString(this));
            addWifiInfo(sig);

            // Virtual geofence states (inside/outside) are computed locally from
            // GPS in evaluateGeofences and never pass through the DiPlus CAN
            // pipeline (their items have no diplusName and are dropped from batch
            // reads), so inject them into the snapshot directly from the cache.
            // geo_<id>_name keys carry the zone name for friendly naming in HA.
            for (Map.Entry<String, String> e : valueStore.entries()) {
                String key = e.getKey();
                String value = e.getValue();
                if (key == null || !key.startsWith("geo_")) continue;
                if (value == null || value.isEmpty() || "---".equals(value)) continue;
                if (valueStore.isRestored(key)) continue;
                sig.put(key, value);
            }

            // Derived aggregate sensors (windows_state/doors_state) are computed
            // locally from the raw signals above and injected like geo_ items.
            refreshDerivedSensors();
            injectDerivedSensors(sig);

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

    /** Fallback to last-known GPS/NETWORK/PASSIVE (throttled); logs the result. */
    private void ensureLocationBaseline() {
        long now = System.currentTimeMillis();
        if (now - lastLocationRetryMs < LOCATION_RETRY_INTERVAL_MS) return;
        lastLocationRetryMs = now;
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (lm != null) updateLastKnownLocation(lm);
    }

    /** One-shot barometer read: device_pressure has no producer otherwise. */
    private void readDevicePressure() {
        try {
            SensorManager sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            Sensor p = sm == null ? null : sm.getDefaultSensor(Sensor.TYPE_PRESSURE);
            if (p == null) return;
            sm.registerListener(new SensorEventListener() {
                @Override
                public void onSensorChanged(SensorEvent e) {
                    try {
                        locationSource.onDevicePressure(e.values[0]);
                    } finally {
                        SensorManager s = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
                        if (s != null) s.unregisterListener(this);
                    }
                }

                @Override
                public void onAccuracyChanged(Sensor sensor, int accuracy) {
                }
            }, p, SensorManager.SENSOR_DELAY_NORMAL);
        } catch (Exception ignored) {}
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
            // Baseline: even when live fixes are absent the last-known location
            // must be carried (and logged) so HA always gets a position.
            boolean fixStale = !hasValidLocation()
                    || System.currentTimeMillis() - lastLocTime > 30_000L;
            if (fixStale) ensureLocationBaseline();
            // System-first: the system worker refreshes device signals on its own
            // thread; mirror its latest store values into the snapshot pipeline.
            mirrorSystemToSnapshot();
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
            // Media volume: DiPlus often fails to report the live value, but the
            // Android media stream is authoritative on the head unit.
            try {
                AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                if (am != null) {
                    int vol = am.getStreamVolume(AudioManager.STREAM_MUSIC);
                    int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    if (max > 0) locationSource.onMediaVolume((int) (100f * vol / max));
                }
            } catch (Exception ignored) {}
            readDevicePressure();
        } catch (Exception ignored) {}

        JSONObject sig = new JSONObject();
        java.util.Map<String, Object> cloudSensors = new java.util.LinkedHashMap<>();
        try {
            // GPS minimum — always included in the integration
            for (String k : new String[]{"location_lat", "location_lon", "location_speed",
                    "location_bearing", "location_altitude", "location_accuracy", "location_provider"}) {
                String v = snapshotStore.get(k);
                if (v != null) {
                    sig.put(k, v);
                    cloudSensors.put(k, v);
                }
            }
            String batt = snapshotStore.get("device_battery");
            if (batt != null) {
                sig.put("device_battery", batt);
                cloudSensors.put("device_battery", batt);
            }
            // Derived aggregates must be refreshed before the cache loop picks
            // up the cached value for the current batch.
            refreshDerivedSensors();
            // most recent auto-sensor values
            for (java.util.Map.Entry<String, String> e : valueStore.entries()) {
                String key = e.getKey();
                if (key == null || key.startsWith("geo_") || key.endsWith("_name")) continue;
                // Disabled signals are never forwarded (both snapshot paths agree).
                if (!AppConfig.isSignalEnabled(this, key)) continue;
                String v = e.getValue();
                if (v == null || "---".equals(v)) continue;
                // Never forward non-finite numerics ("nan"/"inf"): HA rejects
                // them for measurement sensors (ValueError).
                if (isNonFiniteNumeric(v)) continue;
                cloudSensors.put(key, v);
                // Restored values are dashboard/telemetry-only: Home Assistant
                // must receive only values a live source has confirmed.
                if (valueStore.isRestored(key)) continue;
                sig.put(key, v);
            }
        } catch (org.json.JSONException e) {
            LogBuffer.e("TelemetryService", "snapshot build: " + e.getMessage());
        }
        SnapshotStore.Loc l = snapshotStore.getLocation();
        HassClient.collectSnapshot(this, l.lat, l.lon, l.accuracy,
                l.timeMs > 0 ? l.timeMs / 1000 : 0, sig.toString());
        if (AppConfig.isCloudSyncEnabled(this) && !cloudSensors.isEmpty()) {
            CloudSyncClient.sendBatch(this, cloudSensors, l.lat, l.lon);
        }
    }

    /** True when a string is a numeric literal that is NaN/Infinity. */
    private void mirrorSystemToSnapshot() {
        try {
            for (String k : new String[]{"location_lat", "location_lon", "location_speed",
                    "location_bearing", "location_altitude", "location_accuracy",
                    "location_provider", "device_battery", "device_pressure", "system_media_volume"}) {
                String v = valueStore.get(k);
                if (v == null) continue;
                // Restored values are dashboard-only; a persisted position must
                // never be re-emitted as the current one.
                if (valueStore.isRestored(k)) continue;
                locationSource.storePut(k, v);
            }
            com.car2hass.vehicle.SnapshotStore.Loc l = valueStoreLoc();
            if (l != null && !valueStore.isRestored("location_lat")
                    && LocationPolicy.isFresh(l.timeMs, System.currentTimeMillis())) {
                locationSource.storeSetLocation(l);
            }
        } catch (Exception ignored) {}
    }

    private com.car2hass.vehicle.SnapshotStore.Loc valueStoreLoc() {
        String lat = valueStore.get("location_lat");
        String lon = valueStore.get("location_lon");
        if (lat == null || lon == null) return null;
        try {
            com.car2hass.vehicle.SnapshotStore.Loc loc = new com.car2hass.vehicle.SnapshotStore.Loc();
            loc.lat = Double.parseDouble(lat);
            loc.lon = Double.parseDouble(lon);
            loc.alt = parseDoubleSafe(valueStore.get("location_altitude"));
            loc.speed = (float) parseDoubleSafe(valueStore.get("location_speed"));
            loc.bearing = (float) parseDoubleSafe(valueStore.get("location_bearing"));
            loc.accuracy = (float) parseDoubleSafe(valueStore.get("location_accuracy"));
            loc.provider = valueStore.get("location_provider") == null ? "" : valueStore.get("location_provider");
            // Real GPS fix time (epoch seconds) when available; only fall back to
            // "now" if the worker never recorded a fix time. Using the true time
            // keeps stale baseline positions in history instead of jumping "now".
            String t = valueStore.get("location_t");
            loc.timeMs = t == null ? System.currentTimeMillis() : Long.parseLong(t) * 1000L;
            return loc;
        } catch (Exception e) {
            return null;
        }
    }

    private static double parseDoubleSafe(String v) {
        try { return Double.parseDouble(v); } catch (Exception e) { return 0.0; }
    }

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

                    long now = System.currentTimeMillis();
                    long time = loc.getTime();
                    long ageMs = now - time;
                    double lat = loc.getLatitude();
                    double lon = loc.getLongitude();
                    float accuracy = loc.getAccuracy();
                    String provider = loc.getProvider();

                    // Ignore absurd fixes; anything else becomes the baseline
                    // (accuracy is recorded and forwarded, HA decides).
                    if (LocationPolicy.isAbsurd(accuracy)) {
                        LogBuffer.d("TelemetryService", "Ignoring absurd location (±" + accuracy + "m) from " + provider);
                        return;
                    }

                    // A cached/last-known fix must never overwrite a live one.
                    if (!LocationPolicy.isFresh(time, now)) {
                        LogBuffer.d("TelemetryService", "Ignoring stale location (age " + ageMs
                                + "ms) from " + provider);
                        return;
                    }

                    // De-duplicate identical locations delivered twice.
                    if (Math.abs(time - lastLocTime) < 100
                            && Math.abs(lat - lastLat) < 1e-6
                            && Math.abs(lon - lastLon) < 1e-6) {
                        LogBuffer.d("TelemetryService", "Ignoring duplicate location from " + provider);
                        return;
                    }

                    // If we have a recent accurate GPS fix, ignore coarse
                    // network/passive updates (PASSIVE needs the same guard).
                    if (LocationPolicy.suppressCoarse(provider, lastProvider,
                            lastAccuracy, lastLocTime, now)) {
                        LogBuffer.d("TelemetryService", "Ignoring " + provider
                                + " location, recent accurate GPS fix");
                        return;
                    }

                    lastLat = lat;
                    lastLon = lon;
                    lastAccuracy = accuracy;
                    lastLocTime = time;
                    lastProvider = provider == null ? "" : provider;
                    locationSource.onLocation(lat, lon,
                            loc.hasSpeed() ? loc.getSpeed() : 0f,
                            loc.hasBearing() ? loc.getBearing() : 0f,
                            loc.hasAltitude() ? loc.getAltitude() : 0.0,
                            accuracy, provider, time);
                    LogBuffer.i("TelemetryService", "GPS update: " + lastLat + ", " + lastLon
                            + " (±" + lastAccuracy + "m, age " + ageMs + "ms) from " + provider);
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
            // Pick the newest last-known fix across providers; a GPS-first pick
            // would otherwise throw away a fresher NETWORK/PASSIVE fix.
            Location loc = null;
            for (String provider : new String[]{
                    LocationManager.GPS_PROVIDER,
                    LocationManager.NETWORK_PROVIDER,
                    LocationManager.PASSIVE_PROVIDER}) {
                try {
                    Location l = lm.getLastKnownLocation(provider);
                    if (l != null && (loc == null || l.getTime() > loc.getTime())) {
                        loc = l;
                    }
                } catch (Exception ignored) {}
            }
            if (loc == null) {
                LogBuffer.w("TelemetryService", "No last known GPS location available");
                return;
            }
            long now = System.currentTimeMillis();
            long ageMs = now - loc.getTime();
            // A cached fix is history once it is older than the guard: seeding it
            // would stamp an old position with the current snapshot time.
            if (!LocationPolicy.isFresh(loc.getTime(), now)) {
                LogBuffer.d("TelemetryService", "Skipping stale last-known location (age "
                        + ageMs + "ms) from " + loc.getProvider());
                return;
            }
            if (LocationPolicy.isAbsurd(loc.getAccuracy())) {
                LogBuffer.d("TelemetryService", "Skipping inaccurate last-known location (±"
                        + loc.getAccuracy() + "m) from " + loc.getProvider());
                return;
            }
            // Only adopt the last-known fix if it is newer than what we already
            // have. A stale PASSIVE/NETWORK last-known location would otherwise
            // overwrite a fresher GPS fix and make the HA track jump back to an
            // old position with an old fix time.
            if (lastLocTime != 0 && loc.getTime() <= lastLocTime) {
                LogBuffer.d("TelemetryService", "Skipping last-known location older than current ("
                        + loc.getTime() + " <= " + lastLocTime + ")");
                return;
            }
            lastLat = loc.getLatitude();
            lastLon = loc.getLongitude();
            lastAccuracy = loc.getAccuracy();
            lastLocTime = loc.getTime();
            lastProvider = loc.getProvider() == null ? "" : loc.getProvider();
            // Keep the location_* signals in sync so telemetry always carries
            // a baseline even when live GPS fixes are absent.
            locationSource.onLocation(loc.getLatitude(), loc.getLongitude(),
                    loc.hasSpeed() ? loc.getSpeed() : 0f,
                    loc.hasBearing() ? loc.getBearing() : 0f,
                    loc.hasAltitude() ? loc.getAltitude() : 0.0,
                    loc.getAccuracy(), loc.getProvider(), loc.getTime());
            LogBuffer.i("TelemetryService", "Last known GPS: " + lastLat + ", " + lastLon
                    + " (±" + lastAccuracy + "m, age " + ageMs + "ms) from " + loc.getProvider());
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
                String prevState = valueStore.get(key);
                if (!state.equals(prevState)) {
                    LogBuffer.i("TelemetryService", "Geofence '" + z.name + "': "
                        + (prevState != null ? prevState : "unknown") + "→" + state);
                }
                if ("inside".equals(state) && "outside".equals(prevState)) {
                    // outside → inside transition: the car just entered the zone.
                    z.lastVisitedAtMs = now;
                    visitedChanged = true;
                }
                valueStore.put(key, state, "channel");
                // Zone name travels alongside the state so Home Assistant can
                // build a friendly entity name for the dynamic geo_<id> key.
                valueStore.put(key + "_name", z.name, "channel");
                ensureGeofenceItem(key);
            }
            // Remove stale geo_ keys for zones that no longer exist.
            for (Iterator<Map.Entry<String, String>> it = valueStore.entries().iterator(); it.hasNext(); ) {
                Map.Entry<String, String> entry = it.next();
                String k = entry.getKey();
                if (k.startsWith("geo_") && !k.endsWith("_name")) {
                    String zoneId = k.substring("geo_".length());
                    if (!activeZoneIds.contains(zoneId)) {
                        it.remove();
                        valueStore.remove(k + "_name");
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
        item.value = (valueStore.get(key) != null ? valueStore.get(key) : "---");
        knownItems.add(item);
    }

    private static final String[] DERIVED_WINDOW_KEYS =
            {"window_fl", "window_fr", "window_rl", "window_rr", "sunroof", "sunshade"};
    private static final String[] DERIVED_DOOR_KEYS =
            {"driver_door", "passenger_door", "rear_left_door", "rear_right_door", "trunk"};
    private static final String[] DERIVED_COUNT_KEYS =
            {"windows_state", "doors_state"};
    private static final String[] DERIVED_SENSOR_KEYS =
            {"windows_state", "windows_all_state", "doors_state", "doors_all_state", "doors_all_lock"};

    // Pre-register one telemetry item per derived aggregate sensor so the
    // knownItems list stays stable while CANDataReader workers iterate it.
    // Their values are computed locally (never read from a vehicle channel),
    // which is why diplusName stays null — the same contract as geo_ items.
    private void preregisterDerivedItems() {
        try {
            for (String key : DERIVED_SENSOR_KEYS) {
                ensureDerivedItem(key);
            }
        } catch (Exception e) {
            LogBuffer.w("TelemetryService", "preregisterDerivedItems failed: " + e.getMessage());
        }
    }

    private void ensureDerivedItem(String key) {
        for (CANDataItem item : knownItems) {
            if (key.equals(item.key)) return;
        }
        CANDataItem item = new CANDataItem(0, key, "", 0);
        item.key = key;
        item.diplusName = null;
        item.value = (valueStore.get(key) != null ? valueStore.get(key) : "---");
        knownItems.add(item);
    }

    /**
     * Computes aggregate door/window sensors from the raw values cached above
     * (windows_state/doors_state open counts, their *_all_state enums and the
     * doors_all_lock lock aggregate) and stores them in the cache so both
     * snapshot paths and rules see them.
     * Derived sensors are computed locally — never read from a vehicle channel.
     */
    private void refreshDerivedSensors() {
        try {
            int openWindows = 0, totalWindows = 0;
            for (String k : DERIVED_WINDOW_KEYS) {
                if (valueStore.isRestored(k)) continue;
                String v = valueStore.get(k);
                if (v == null || v.isEmpty() || "---".equals(v)) continue;
                totalWindows++;
                if (parseDoubleSafe(v) > 0 || isOpenState(v)) openWindows++;
            }
            if (totalWindows > 0) {
                valueStore.put("windows_state", String.valueOf(openWindows), "channel");
                valueStore.put("windows_all_state", openWindows == 0 ? "closed" : "open", "channel");
                SensorValueHistory.recordValue("windows_state", String.valueOf(openWindows));
                SensorValueHistory.recordValue("windows_all_state", openWindows == 0 ? "closed" : "open");
            }

            int openDoors = 0, totalDoors = 0;
            for (String k : DERIVED_DOOR_KEYS) {
                if (valueStore.isRestored(k)) continue;
                String v = valueStore.get(k);
                if (v == null || v.isEmpty() || "---".equals(v)) continue;
                totalDoors++;
                if (isOpenState(v)) openDoors++;
            }
            if (totalDoors > 0) {
                valueStore.put("doors_state", String.valueOf(openDoors), "channel");
                valueStore.put("doors_all_state", openDoors == 0 ? "closed" : "open", "channel");
                SensorValueHistory.recordValue("doors_state", String.valueOf(openDoors));
                SensorValueHistory.recordValue("doors_all_state", openDoors == 0 ? "closed" : "open");
            }

            String lockValue = DerivedAggregates.doorsAllLocked(valueStore) ? "locked" : "unlocked";
            if (hasFreshLockValue()) {
                valueStore.put("doors_all_lock", lockValue, "channel");
                SensorValueHistory.recordValue("doors_all_lock", lockValue);
            }

            updateDerivedItems();
        } catch (Exception ignored) {}
    }

    /** True when at least one door lock has a live (non-restored) value. */
    private boolean hasFreshLockValue() {
        for (String k : DerivedAggregates.DOOR_LOCK_KEYS) {
            if (valueStore.isRestored(k)) continue;
            String v = valueStore.get(k);
            if (v != null && !v.isEmpty() && !"---".equals(v)) return true;
        }
        return false;
    }

    /** True for a raw source key of a derived aggregate (window/door/sunroof/trunk). */
    private static boolean isDerivedSourceKey(String key) {
        if (key == null) return false;
        for (String k : DERIVED_WINDOW_KEYS) if (k.equals(key)) return true;
        for (String k : DERIVED_DOOR_KEYS) if (k.equals(key)) return true;
        for (String k : DerivedAggregates.DOOR_LOCK_KEYS) if (k.equals(key)) return true;
        return false;
    }

    private static boolean isDerivedKey(String key) {
        for (String k : DERIVED_SENSOR_KEYS) if (k.equals(key)) return true;
        return false;
    }

    /** Treats numeric >0 and textual open/on/true/1 as "open". */
    private static boolean isOpenState(String v) {
        if (v == null) return false;
        String s = v.trim().toLowerCase(java.util.Locale.ROOT);
        return s.startsWith("open") || "1".equals(s) || "true".equals(s) || "on".equals(s);
    }

    /** Mirrors the computed aggregates onto knownItems so the UI/telemetry see them. */
    private void updateDerivedItems() {
        synchronized (knownItems) {
            long now = System.currentTimeMillis();
            for (CANDataItem item : knownItems) {
                if (item == null || item.key == null) continue;
                if (!isDerivedKey(item.key)) continue;
                String v = valueStore.get(item.key);
                if (v != null && !v.isEmpty() && !"---".equals(v)) {
                    item.value = v;
                    item.lastUpdate = now;
                }
            }
        }
    }

    /** Puts derived aggregate sensors into the snapshot (numeric counts as numbers). */
    private void injectDerivedSensors(JSONObject sig) {
        try {
            java.util.Set<String> countKeys = new java.util.HashSet<>(
                    java.util.Arrays.asList(DERIVED_COUNT_KEYS));
            for (String key : DERIVED_SENSOR_KEYS) {
                if (valueStore.isRestored(key)) continue;
                String v = valueStore.get(key);
                if (v == null || v.isEmpty() || "---".equals(v)) continue;
                if (countKeys.contains(key)) {
                    sig.put(key, Integer.parseInt(v));
                } else {
                    sig.put(key, v);
                }
            }
        } catch (Exception ignored) {}
    }
}
