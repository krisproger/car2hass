package com.diplustohass;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class CANDataReader {
    private static final AtomicBoolean refreshing = new AtomicBoolean(false);
    private static final int GETDIPARS_CONCURRENCY = 3;
    private static ExecutorService executor;
    private static final Object executorLock = new Object();
    private static final int GETVAL_CONCURRENCY = 8;
    private static ExecutorService getValExecutor;
    private static final AtomicInteger requestCounter = new AtomicInteger(0);

    public static final int SOURCE_HTTP = 0;
    public static final int SOURCE_DUMPSYS = 4;
    public static final int SOURCE_ALL = 99;

    private static final String DIPLUS_BASE = "http://127.0.0.1:8988";
    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 8000;
    private static final int GETDIPARS_GROUP_SIZE = 20;
    private static final long PING_CACHE_TTL_MS = 30000;
    private static final long LAUNCH_COOLDOWN_MS = 60000;

    // Unit Separator is used to delimit values in getDiPars template. It is far less
    // likely to appear in signal values than a comma.
    private static final String BATCH_DELIMITER = "\u001F";

    // Some signals are returned in firmware-specific units and need scaling before
    // display or sending to Home Assistant.
    private static final HashMap<String, Double> SCALE_FACTORS = new HashMap<>();
    static {
        SCALE_FACTORS.put("range", 0.1);
        SCALE_FACTORS.put("tyre_pressure_fl", 0.01);
        SCALE_FACTORS.put("tyre_pressure_fr", 0.01);
        SCALE_FACTORS.put("tyre_pressure_rl", 0.01);
        SCALE_FACTORS.put("tyre_pressure_rr", 0.01);
    }

    /**
     * Apply firmware-specific scaling to numeric raw values.
     * Returns the scaled value as a string, or the original value if it cannot be
     * parsed as a number or the signal has no scaling factor.
     */
    private static String applyScale(String key, String rawValue) {
        Double factor = SCALE_FACTORS.get(key);
        if (factor == null || rawValue == null || rawValue.isEmpty()) {
            return rawValue;
        }
        try {
            double scaled = Double.parseDouble(rawValue) * factor;
            // Avoid direct float equality; derive precision from the factor magnitude.
            double absFactor = Math.abs(factor);
            if (absFactor < 0.011) {
                return String.format(Locale.US, "%.2f", scaled);
            }
            if (absFactor < 0.11) {
                return String.format(Locale.US, "%.1f", scaled);
            }
            return String.valueOf(scaled);
        } catch (NumberFormatException e) {
            return rawValue;
        }
    }

    // Cache of Chinese names that diplus actually supports (thread-safe)
    private static final List<String> supportedNames = new CopyOnWriteArrayList<>();
    // Cache of Chinese names that diplus explicitly does not support (thread-safe)
    private static final List<String> unsupportedNames = new CopyOnWriteArrayList<>();

    // Latest raw values received from DiPlus, keyed by stable HA signal key.
    // Used for command verification without blocking on another telemetry cycle.
    private static final ConcurrentHashMap<String, String> lastKnownRawValues = new ConcurrentHashMap<>();

    private static final String SIGNAL_CACHE_PREFS = "signal_cache";
    private static final String KEY_UNSUPPORTED_NAMES = "unsupported_names";
    private static final String KEY_CACHE_APP_VERSION = "cache_app_version";
    private static boolean signalCacheLoaded = false;

    private static synchronized void loadSignalCache(Context ctx) {
        if (signalCacheLoaded) return;
        signalCacheLoaded = true;
        try {
            SharedPreferences prefs = ctx.getSharedPreferences(SIGNAL_CACHE_PREFS, Context.MODE_PRIVATE);
            String cachedVersion = prefs.getString(KEY_CACHE_APP_VERSION, "");
            String currentVersion = BuildConfig.VERSION_NAME;
            if (currentVersion != null && !currentVersion.equals(cachedVersion)) {
                prefs.edit().remove(KEY_UNSUPPORTED_NAMES).remove(KEY_CACHE_APP_VERSION).apply();
                LogBuffer.i("CANReader", "Cleared unsupported signal cache (app version changed: "
                        + cachedVersion + " -> " + currentVersion + ")");
                return;
            }
            Set<String> cached = prefs.getStringSet(KEY_UNSUPPORTED_NAMES, null);
            if (cached != null) {
                unsupportedNames.addAll(cached);
                LogBuffer.i("CANReader", "Loaded " + cached.size() + " unsupported signals from cache");
            }
        } catch (Exception e) {
            LogBuffer.d("CANReader", "loadSignalCache failed: " + e.getMessage());
        }
    }

    private static synchronized void saveSignalCache(Context ctx) {
        try {
            SharedPreferences prefs = ctx.getSharedPreferences(SIGNAL_CACHE_PREFS, Context.MODE_PRIVATE);
            prefs.edit()
                    .putStringSet(KEY_UNSUPPORTED_NAMES, new HashSet<>(unsupportedNames))
                    .putString(KEY_CACHE_APP_VERSION, BuildConfig.VERSION_NAME)
                    .apply();
        } catch (Exception e) {
            LogBuffer.d("CANReader", "saveSignalCache failed: " + e.getMessage());
        }
    }

    private static void cacheUnsupported(Context ctx, String name) {
        if (name == null || name.isEmpty() || unsupportedNames.contains(name)) return;
        unsupportedNames.add(name);
        saveSignalCache(ctx);
    }

    private static void cacheSupported(Context ctx, String name) {
        if (name == null || name.isEmpty()) return;
        boolean changed = false;
        if (unsupportedNames.remove(name)) changed = true;
        if (!supportedNames.contains(name)) {
            supportedNames.add(name);
            changed = true;
        }
        if (changed) saveSignalCache(ctx);
    }

    /** True if this Chinese name is currently known to be unsupported by diplus. */
    public static boolean isUnsupportedSignal(Context ctx, String diplusName) {
        loadSignalCache(ctx);
        return diplusName != null && unsupportedNames.contains(diplusName);
    }

    // Ping cache to avoid hammering diplus when it's unavailable
    private static volatile long lastPingSuccess = 0;
    private static volatile long lastPingFailure = 0;
    private static volatile long lastLaunchAttempt = 0;

    public static String sVin = "---";
    public static String sFirmware = "---";

    public interface Callback {
        void onData(List<CANDataItem> items, long timestamp, int source);
        void onError(String message, int source);
    }

    public static void refreshData(final Context context, final List<CANDataItem> knownItems,
                                    final int source, final Callback callback) {
        if (!refreshing.compareAndSet(false, true)) {
            LogBuffer.d("CANReader", "refresh skipped — previous still in progress");
            return;
        }
        loadSignalCache(context);
        final int src = source;
        final long cycleStartMs = System.currentTimeMillis();
        requestCounter.set(0);
        ensureExecutors();
        executor.submit(() -> {
            try {
                List<CANDataItem> result = null;
                switch (src) {
                    case SOURCE_HTTP:
                        result = tryHttpApi(context, knownItems);
                        break;
                    case SOURCE_DUMPSYS:
                        result = tryDumpsys(knownItems);
                        break;
                    case SOURCE_ALL:
                        result = tryHttpApi(context, knownItems);
                        if (result == null) result = new ArrayList<>();
                        // Also run dumpsys to get VVIN/FW/props, merge with signal list
                        List<CANDataItem> sys = tryDumpsys(knownItems);
                        if (sys != null) {
                            for (CANDataItem si : sys) {
                                String n = si.name;
                                if (!n.equals("VVIN (virtual VIN)") && !n.equals("FW (firmware)")) {
                                    result.add(si);
                                }
                            }
                        }
                        break;
                }
                if (result != null && !result.isEmpty()) {
                    callback.onData(result, System.currentTimeMillis(), src);
                } else {
                    callback.onError("No data from source " + src, src);
                }
            } catch (Exception e) {
                LogBuffer.e("CANReader", "Source " + src + " error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                callback.onError(e.getClass().getSimpleName() + ": " + e.getMessage(), src);
            } finally {
                refreshing.set(false);
                LogBuffer.i("CANReader", "CAN refresh cycle took "
                        + (System.currentTimeMillis() - cycleStartMs) + " ms");
            }
        });
    }

    /** Ensure executors are alive; recreate them if a previous shutdown left them terminated. */
    private static void ensureExecutors() {
        synchronized (executorLock) {
            if (executor == null || executor.isShutdown() || executor.isTerminated()) {
                executor = Executors.newFixedThreadPool(GETDIPARS_CONCURRENCY);
            }
            if (getValExecutor == null || getValExecutor.isShutdown() || getValExecutor.isTerminated()) {
                getValExecutor = Executors.newFixedThreadPool(GETVAL_CONCURRENCY);
            }
        }
    }

    /**
     * Reset the refresh guard. Called when the service is (re)started so a stale
     * lock left by a killed service instance does not block telemetry forever.
     */
    public static void resetRefreshState() {
        refreshing.set(false);
        LogBuffer.i("CANReader", "Refresh state reset");
    }

    /** Shutdown internal executors. Kept for compatibility but executors are recreated on demand. */
    public static void shutdown() {
        synchronized (executorLock) {
            if (executor != null) {
                executor.shutdownNow();
                executor = null;
            }
            if (getValExecutor != null) {
                getValExecutor.shutdownNow();
                getValExecutor = null;
            }
        }
    }

    // ─── Full signal registry from SIGNALS.md (132 signals) ───
    // Each entry: {chinese_name, english_name, key, type}
    //   key    = stable snake_case identifier used in HA payload
    //   type   = "num" = numeric, "enum" = has enum labels (status=true returns text)
    // Chinese name is used for GET /api/getVal?name=<chinese>&status=true

        // 132 CAN-mapped signals (synthetic app-supplied signals excluded)
    public static final String[][] SIGNAL_REGISTRY = {
        {"电源状态", "Power state", "power_state", "enum"},
        {"车速", "Speed (km/h)", "speed", "num"},
        {"里程", "Range / mileage", "range", "num"},
        {"档位", "Gear", "gear", "enum"},
        {"发动机转速", "Engine RPM", "engine_rpm", "num"},
        {"刹车深度", "Brake pedal depth", "brake_pedal", "num"},
        {"加速踏板深度", "Accelerator pedal depth", "accel_pedal", "num"},
        {"前电机转速", "Front motor RPM", "front_motor_rpm", "num"},
        {"后电机转速", "Rear motor RPM", "rear_motor_rpm", "num"},
        {"发动机功率", "Engine power", "engine_power", "num"},
        {"前电机扭矩", "Front motor torque", "front_motor_torque", "num"},
        {"充电枪插枪状态", "Charge gun plug state", "charge_gun_state", "enum"},
        {"百公里电耗", "Energy per 100 km", "energy_per_100km", "num"},
        {"最高电池温度", "Max battery temp", "battery_temp_max", "num"},
        {"平均电池温度", "Avg battery temp", "battery_temp_avg", "num"},
        {"最低电池温度", "Min battery temp", "battery_temp_min", "num"},
        {"最高电池电压", "Max cell voltage", "cell_voltage_max", "num"},
        {"最低电池电压", "Min cell voltage", "cell_voltage_min", "num"},
        {"上次雨刮时间", "Last wiper time", "last_wiper_time", "num"},
        {"天气", "Weather", "weather", "enum"},
        {"主驾驶安全带状态", "Driver seatbelt state", "driver_seatbelt", "enum"},
        {"远程锁车状态", "Remote lock state", "remote_lock_state", "enum"},
        {"车内温度", "Cabin temperature", "cabin_temp", "num"},
        {"车外温度", "Outside temperature", "outside_temp", "num"},
        {"主驾驶空调温度", "Driver A/C set temp", "ac_set_temp", "num"},
        {"温度单位", "Temperature unit", "temp_unit", "enum"},
        {"电池容量", "Battery capacity", "battery_capacity", "num"},
        {"方向盘转角", "Steering wheel angle", "steering_angle", "num"},
        {"方向盘转速", "Steering wheel rate", "steering_rate", "num"},
        {"总电耗", "Total energy consumption", "total_energy", "num"},
        {"电量百分比", "Battery charge (%)", "battery_charge", "num"},
        {"油量百分比", "Fuel level (%)", "fuel_level", "num"},
        {"总燃油消耗", "Total fuel consumption", "total_fuel", "num"},
        {"车道线曲率", "Lane curvature", "lane_curvature", "num"},
        {"右侧线距离", "Right lane distance", "right_lane_distance", "num"},
        {"左侧线距离", "Left lane distance", "left_lane_distance", "num"},
        {"蓄电池电压", "12V battery voltage", "battery_voltage", "num"},
        {"雷达左前", "Radar front-left", "radar_fl", "num"},
        {"雷达右前", "Radar front-right", "radar_fr", "num"},
        {"雷达左后", "Radar rear-left", "radar_rl", "num"},
        {"雷达右后", "Radar rear-right", "radar_rr", "num"},
        {"雷达左", "Radar left", "radar_left", "num"},
        {"雷达前左中", "Radar front-left-center", "radar_flc", "num"},
        {"雷达前右中", "Radar front-right-center", "radar_frc", "num"},
        {"雷达中后", "Radar rear-center", "radar_rc", "num"},
        {"前雨刮速度", "Front wiper speed", "front_wiper_speed", "num"},
        {"雨刮档位", "Wiper mode", "wiper_mode", "num"},
        {"巡航开关", "Cruise switch", "cruise_switch", "num"},
        {"前车距离", "Distance to car ahead", "distance_to_car_ahead", "num"},
        {"充电状态", "Charging state", "charging_state", "enum"},
        {"左前轮气压", "Tyre pressure FL", "tyre_pressure_fl", "num"},
        {"右前轮气压", "Tyre pressure FR", "tyre_pressure_fr", "num"},
        {"左后轮气压", "Tyre pressure RL", "tyre_pressure_rl", "num"},
        {"右后轮气压", "Tyre pressure RR", "tyre_pressure_rr", "num"},
        {"左转向灯", "Left turn signal", "left_turn", "enum"},
        {"右转向灯", "Right turn signal", "right_turn", "enum"},
        {"主驾车门锁", "Driver door lock", "driver_door_lock", "enum"},
        {"主驾车窗打开百分比", "Window FL open (%)", "window_fl", "num"},
        {"副驾车窗打开百分比", "Window FR open (%)", "window_fr", "num"},
        {"左后车窗打开百分比", "Window RL open (%)", "window_rl", "num"},
        {"右后车窗打开百分比", "Window RR open (%)", "window_rr", "num"},
        {"天窗打开百分比", "Sunroof open (%)", "sunroof", "num"},
        {"遮阳帘打开百分比", "Sunshade open (%)", "sunshade", "num"},
        {"整车工作模式", "Powertrain work mode", "powertrain_mode", "enum"},
        {"整车运行模式", "Drive mode", "drive_mode", "enum"},
        {"月", "Month", "month", "num"},
        {"日", "Day", "day", "num"},
        {"时", "Hour", "hour", "num"},
        {"分", "Minute", "minute", "num"},
        {"副驾安全带警告", "Passenger seatbelt warning", "passenger_seatbelt_warning", "enum"},
        {"二排左安全带", "2nd row left seatbelt", "seatbelt_2nd_left", "enum"},
        {"二排右安全带", "2nd row right seatbelt", "seatbelt_2nd_right", "enum"},
        {"二排中安全带", "2nd row center seatbelt", "seatbelt_2nd_center", "enum"},
        {"空调状态", "A/C state", "ac_state", "enum"},
        {"风量档位", "Fan speed level", "fan_speed", "num"},
        {"空调循环方式", "A/C recirculation", "ac_recirculation", "enum"},
        {"空调出风模式", "A/C airflow mode", "ac_airflow_mode", "enum"},
        {"主驾车门", "Driver door", "driver_door", "enum"},
        {"副驾车门", "Passenger door", "passenger_door", "enum"},
        {"左后车门", "Rear-left door", "rear_left_door", "enum"},
        {"右后车门", "Rear-right door", "rear_right_door", "enum"},
        {"引擎盖", "Bonnet", "bonnet", "enum"},
        {"后备箱门", "Trunk", "trunk", "enum"},
        {"油箱盖", "Fuel/charge flap", "fuel_charge_flap", "enum"},
        {"自动驻车", "Auto hold", "auto_hold", "enum"},
        {"ACC巡航状态", "ACC cruise state", "acc_cruise_state", "enum"},
        {"左后接近告警", "Rear-left approach warning", "rear_left_approach_warning", "enum"},
        {"右后接近告警", "Rear-right approach warning", "rear_right_approach_warning", "enum"},
        {"车道保持状态", "Lane keep state", "lane_keep_state", "enum"},
        {"左后车门锁", "Rear-left door lock", "rear_left_door_lock", "enum"},
        {"副驾车门锁", "Passenger door lock", "passenger_door_lock", "enum"},
        {"右后车门锁", "Rear-right door lock", "rear_right_door_lock", "enum"},
        {"后备箱门锁", "Trunk lock", "trunk_lock", "enum"},
        {"左后儿童锁", "Rear-left child lock", "rear_left_child_lock", "enum"},
        {"右后儿童锁", "Rear-right child lock", "rear_right_child_lock", "enum"},
        {"小灯", "Sidelights", "sidelights", "enum"},
        {"近光灯", "Low beam", "low_beam", "enum"},
        {"远光灯", "High beam", "high_beam", "enum"},
        {"前雾灯", "Front fog", "front_fog", "enum"},
        {"后雾灯", "Rear fog", "rear_fog", "enum"},
        {"脚照灯", "Footwell light", "footwell_light", "enum"},
        {"日行灯", "DRL", "drl", "enum"},
        {"发动机水温", "Engine coolant temp", "engine_coolant_temp", "num"},
        {"双闪", "Hazard lights", "hazard", "enum"},
        {"坡度", "Slope / grade", "slope", "num"},
        {"雨量", "Rain amount", "rain_amount", "num"},
        {"副驾安全带", "Passenger seatbelt", "passenger_seatbelt", "enum"},
        {"秒", "Second", "second", "num"},
        {"SOC", "Battery SOC (%)", "soc", "num"},
        {"转向信号", "Turn signal", "turn_signal", "enum"},
        {"全景状态", "Surround-view state", "surround_view_state", "enum"},
        {"配置UI版本", "UI config version", "ui_config_version", "enum"},
        {"哨兵状态", "Sentry state", "sentry_state", "num"},
        {"熄火录像配置开关", "Parked-recording switch", "parked_recording_switch", "num"},
        {"熄火哨兵报警", "Parked sentry alarm", "parked_sentry_alarm", "num"},
        {"WIFI状态", "Wi-Fi state", "wifi_state", "enum"},
        {"蓝牙状态", "Bluetooth state", "bluetooth_state", "enum"},
        {"蓝牙信号强度", "Bluetooth signal", "bluetooth_signal", "num"},
        {"晃动幅度", "Sway magnitude", "sway_magnitude", "num"},
        {"振动幅度", "Vibration magnitude", "vibration_magnitude", "num"},
        {"屏幕宽度", "Screen width", "screen_width", "num"},
        {"屏幕高度", "Screen height", "screen_height", "num"},
        {"全景记录仪状态", "Dashcam state", "dashcam_state", "enum"},
        {"无线ADB开关", "Wireless ADB switch", "wireless_adb_switch", "enum"},
        {"媒体音量", "Media volume", "media_volume", "num"},
        {"导航音量", "Navigation volume", "navigation_volume", "num"},
        {"AI识别人可信度", "AI person confidence", "ai_person_confidence", "num"},
        {"AI识别车可信度", "AI vehicle confidence", "ai_vehicle_confidence", "num"},
        {"上次哨兵触发时间", "Last sentry trigger time", "last_sentry_trigger_time", "num"},
        {"上次录像文件开始时间", "Last clip start time", "last_clip_start_time", "num"},
        {"上次录像文件结束时间", "Last clip end time", "last_clip_end_time", "num"},
        {"前车起步状态", "Lead-car start state", "lead_car_start_state", "num"}
    };

    /** Find a signal by its stable HA key. */
    public static CANDataItem findSignalByKey(String key) {
        if (key == null) return null;
        for (CANDataItem item : createSignalItems()) {
            if (key.equals(item.key)) return item;
        }
        return null;
    }

    /** Build the default CANDataItem list from the signal registry. */
    public static List<CANDataItem> createSignalItems() {
        try {
            List<CANDataItem> list = new ArrayList<>();
            for (int i = 0; i < SIGNAL_REGISTRY.length; i++) {
                String[] sig = SIGNAL_REGISTRY[i];
                CANDataItem item = new CANDataItem(0, sig[1], "", i);
                item.diplusName = sig[0];  // Chinese name for API queries
                item.key = sig[2];    // Stable HA key
                item.rawData = sig[3]; // type (num/enum)
                list.add(item);
            }
            return list;
        } catch (Exception e) {
            LogBuffer.e("CANReader", "createSignalItems failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }

    // ─── DiPlus clean API ───

    private static boolean isDiplusAlive() {
        long now = System.currentTimeMillis();
        if (now - lastPingSuccess < PING_CACHE_TTL_MS) return true;
        if (now - lastPingFailure < PING_CACHE_TTL_MS / 2) return false;
        boolean alive = diplusPing();
        if (alive) lastPingSuccess = now;
        else lastPingFailure = now;
        return alive;
    }

    private static List<CANDataItem> tryHttpApi(Context context, List<CANDataItem> items) {
        LogBuffer.i("CANReader", "=== DiPlus: batch read via getDiPars ===");
        if (!isDiplusAlive()) {
            long now = System.currentTimeMillis();
            if (now - lastLaunchAttempt > LAUNCH_COOLDOWN_MS) {
                lastLaunchAttempt = now;
                LogBuffer.w("CANReader", "diplus not responding on 127.0.0.1:8988; trying to launch");
                launchDiplus(context);
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ignored) {}
            } else {
                LogBuffer.d("CANReader", "diplus not responding, launch on cooldown");
            }
            if (!isDiplusAlive()) {
                LogBuffer.w("CANReader", "diplus still not responding");
                return null;
            }
        }

        // Primary: batch read via getDiPars
        List<CANDataItem> result = diplusGetDiPars(context, items);
        if (result != null && !result.isEmpty()) {
            LogBuffer.i("CANReader", "diplus getDiPars returned " + result.size() + " signal values");
            return result;
        }

        // Fallback: individual getVal calls
        LogBuffer.i("CANReader", "getDiPars returned no data, falling back to individual getVal");
        return diplusReadSignals(context, items);
    }

    private static List<CANDataItem> diplusGetDiPars(Context context, List<CANDataItem> items) {
        if (items == null || items.isEmpty()) return null;

        // Unsupported signals are still polled: they stay visible in the telemetry
        // list and may become supported after a firmware update. They are filtered
        // out only when building the HA snapshot, not here.
        //
        // Signals already known to be unsupported travel in their own trailing
        // group(s): a mixed group would fail wholesale (DiPlus answers
        // {"success":false} for any group containing an unknown name) and force
        // the recursive splitter to re-isolate the bad names every cycle. With
        // a dedicated group the working groups succeed in one request each and
        // the unsupported names cost a single failed request per cycle.
        List<CANDataItem> supported = new ArrayList<>();
        List<CANDataItem> unsupported = new ArrayList<>();
        for (CANDataItem item : items) {
            if (isUnsupportedSignal(context, item.diplusName)) {
                unsupported.add(item);
            } else {
                supported.add(item);
            }
        }

        List<CANDataItem> result = new ArrayList<>();
        List<Future<List<CANDataItem>>> futures = new ArrayList<>();
        int groupCount = 0;
        for (int start = 0; start < supported.size(); start += GETDIPARS_GROUP_SIZE) {
            int end = Math.min(start + GETDIPARS_GROUP_SIZE, supported.size());
            final List<CANDataItem> group = supported.subList(start, end);
            groupCount++;
            futures.add(executor.submit(new Callable<List<CANDataItem>>() {
                @Override
                public List<CANDataItem> call() {
                    return fetchBatchRecursive(context, group);
                }
            }));
        }
        for (int start = 0; start < unsupported.size(); start += GETDIPARS_GROUP_SIZE) {
            int end = Math.min(start + GETDIPARS_GROUP_SIZE, unsupported.size());
            final List<CANDataItem> group = unsupported.subList(start, end);
            groupCount++;
            futures.add(executor.submit(new Callable<List<CANDataItem>>() {
                @Override
                public List<CANDataItem> call() {
                    return fetchBatchRecursive(context, group);
                }
            }));
        }
        int total = items.size();
        int failedGroups = 0;
        for (Future<List<CANDataItem>> future : futures) {
            try {
                List<CANDataItem> groupResult = future.get(READ_TIMEOUT_MS * 2L, TimeUnit.MILLISECONDS);
                if (groupResult != null) {
                    result.addAll(groupResult);
                } else {
                    failedGroups++;
                }
            } catch (Exception e) {
                LogBuffer.e("CANReader", "getDiPars group task failed: " + e.getMessage());
                failedGroups++;
            }
        }

        // Keep unsupported signals visible in the telemetry list. They are polled
        // every cycle (a firmware update may make them available) but are marked
        // so the UI hides their checkbox and HA never forwards them.
        if (!result.isEmpty()) {
            result = mergeUnsupportedItems(context, items, result);
        }

        LogBuffer.i("CANReader", "diplusGetDiPars: " + total + " signals in " + groupCount
                + " groups, " + requestCounter.get() + " RAW requests, " + failedGroups
                + " failed groups, " + result.size() + " values");
        return result.isEmpty() ? null : result;
    }

    /**
     * Merge the successfully-read signals with any known-unsupported signals so that
     * the telemetry list can show them (without a checkbox) even though DiPlus does
     * not return a value for them.
     */
    private static List<CANDataItem> mergeUnsupportedItems(Context context,
                                                           List<CANDataItem> requested,
                                                           List<CANDataItem> successful) {
        if (requested == null || requested.isEmpty()) return successful;
        HashMap<String, CANDataItem> byName = new HashMap<>();
        if (successful != null) {
            for (CANDataItem item : successful) {
                if (item.diplusName != null) {
                    byName.put(item.diplusName, item);
                }
            }
        }
        ArrayList<CANDataItem> merged = new ArrayList<>(successful != null ? successful : new ArrayList<CANDataItem>());
        long now = System.currentTimeMillis();
        for (CANDataItem item : requested) {
            if (item.diplusName == null) continue;
            if (byName.containsKey(item.diplusName)) continue;
            if (isUnsupportedSignal(context, item.diplusName)) {
                CANDataItem copy = new CANDataItem(item.canId, item.name, item.unit, item.route);
                copy.diplusName = item.diplusName;
                copy.key = item.key;
                copy.rawData = item.rawData;
                copy.value = "---";
                copy.lastUpdate = now;
                copy.unsupported = true;
                copy.enabled = false;
                merged.add(copy);
            }
        }
        return merged;
    }

    /**
     * Try to read a batch of signals via getDiPars. If it fails, split the group in
     * half and retry each half recursively. When we reach a single item, fall back to
     * getVal and cache unsupported names so they are skipped in future batches.
     */
    private static List<CANDataItem> fetchBatchRecursive(Context context, List<CANDataItem> items) {
        if (items == null || items.isEmpty()) return null;

        List<CANDataItem> batchResult = diplusGetDiParsGroup(context, items);
        if (batchResult != null) {
            return batchResult;
        }

        if (items.size() == 1) {
            CANDataItem item = items.get(0);
            CANDataItem single = readSingleSignal(context, item);
            if (single != null) {
                List<CANDataItem> r = new ArrayList<>();
                r.add(single);
                return r;
            }
            // Mark as unsupported so future batches skip it.
            cacheUnsupported(context, item.diplusName);
            LogBuffer.i("CANReader", "Unsupported signal cached: " + item.diplusName);
            return null;
        }

        // Split and recurse.
        int mid = items.size() / 2;
        List<CANDataItem> left = fetchBatchRecursive(context, items.subList(0, mid));
        List<CANDataItem> right = fetchBatchRecursive(context, items.subList(mid, items.size()));
        List<CANDataItem> combined = new ArrayList<>();
        if (left != null) combined.addAll(left);
        if (right != null) combined.addAll(right);
        return combined.isEmpty() ? null : combined;
    }

    private static List<CANDataItem> diplusGetDiParsGroup(Context context, List<CANDataItem> items) {
        if (items == null || items.isEmpty()) return null;

        // Build template from Chinese names, separated by a control character (US)
        // to avoid ambiguity when values themselves contain commas.
        StringBuilder template = new StringBuilder();
        for (CANDataItem item : items) {
            String name = item.diplusName;
            if (name == null || name.isEmpty()) continue;
            if (template.length() > 0) template.append(BATCH_DELIMITER);
            template.append("[").append(name).append("]");
        }
        if (template.length() == 0) return null;

        requestCounter.incrementAndGet();
        HttpURLConnection conn = null;
        try {
            String encoded = URLEncoder.encode(template.toString(), "UTF-8");
            URL url = new URL(DiplusApi.withAuth(DIPLUS_BASE + "/api/getDiPars?text=" + encoded, AppConfig.getDiplusAuth(context)));
            LogBuffer.d("CANReader", "getDiPars group: " + items.size() + " signals, " + template.length() + " chars template");
            conn = openConnection(url, "GET", CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
            int code = conn.getResponseCode();
            if (code != 200) {
                LogBuffer.w("CANReader", "getDiPars group HTTP " + code);
                return null;
            }
            String body = readResponseBodyStr(conn);
            if (body == null || body.isEmpty()) return null;

            logRawResponse(url.toString(), body);

            List<String> values = parseDiParsResponse(body, items.size());
            if (values == null || values.isEmpty()) return null;

            // Detect unsubstituted placeholders: diplus leaves "[name]" verbatim when a
            // signal is not supported on this firmware. Treat the whole batch as failed
            // so the recursive fallback can isolate unsupported names and cache them.
            boolean hasPlaceholder = false;
            for (int i = 0; i < items.size() && i < values.size(); i++) {
                CANDataItem item = items.get(i);
                String rawValue = values.get(i);
                if (rawValue != null && rawValue.contains("[" + item.diplusName + "]")) {
                    hasPlaceholder = true;
                    if (!unsupportedNames.contains(item.diplusName)) {
                        cacheUnsupported(context, item.diplusName);
                        LogBuffer.i("CANReader", "Unsupported signal cached from getDiPars placeholder: " + item.diplusName);
                    }
                }
            }
            if (hasPlaceholder) {
                return null;
            }

            List<CANDataItem> result = new ArrayList<>();
            int idx = 0;
            for (CANDataItem item : items) {
                if (idx >= values.size()) break;
                String rawValue = values.get(idx);
                idx++;
                if (rawValue == null || rawValue.isEmpty()) continue;

                CANDataItem copy = new CANDataItem(item.canId, item.name, item.unit, item.route);
                copy.diplusName = item.diplusName;
                copy.key = item.key;
                copy.rawData = item.rawData;
                copy.value = applyScale(item.key, rawValue);
                copy.lastUpdate = System.currentTimeMillis();
                cacheSupported(context, item.diplusName);
                if (copy.key != null && !copy.key.isEmpty()) {
                    lastKnownRawValues.put(copy.key, rawValue);
                }
                result.add(copy);
            }
            return result.isEmpty() ? null : result;
        } catch (Exception e) {
            LogBuffer.e("CANReader", "getDiPars group error: " + e.getMessage());
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * Parse getDiPars response.
     * The normal response is the evaluated template string split by the Unit Separator
     * control character. Some firmware variants return a JSON envelope, which we also
     * try to unpack. If parsing fails or the count is wrong, the caller falls back to
     * individual getVal calls.
     */
    private static List<String> parseDiParsResponse(String body, int expectedCount) {
        List<String> values = new ArrayList<>();
        if (body == null) return values;
        String trimmed = body.trim();
        if (trimmed.isEmpty()) return values;

        // Try JSON envelope first (some variants return {"data":[...]} or {"val":"..."}).
        boolean isJson = trimmed.startsWith("{") || trimmed.startsWith("[");
        if (isJson) {
            try {
                if (trimmed.startsWith("{")) {
                    JSONObject obj = new JSONObject(trimmed);
                    if (obj.has("success") && !obj.optBoolean("success", true)) {
                        // DiPlus reports failure for the whole batch; log the reason
                        // (or the raw body when no reason field exists) and return
                        // empty so the caller can fall back to smaller groups /
                        // individual getVal.
                        LogBuffer.w("CANReader", "getDiPars group error: " + extractDiParsErrorReason(obj, trimmed));
                        return values;
                    }
                    if (obj.has("data") && obj.get("data") instanceof org.json.JSONArray) {
                        org.json.JSONArray arr = obj.getJSONArray("data");
                        for (int i = 0; i < arr.length(); i++) {
                            values.add(String.valueOf(arr.opt(i)).trim());
                        }
                    } else if (obj.has("vals")) {
                        org.json.JSONArray arr = obj.getJSONArray("vals");
                        for (int i = 0; i < arr.length(); i++) {
                            values.add(String.valueOf(arr.opt(i)).trim());
                        }
                    } else if (obj.has("val")) {
                        String val = obj.optString("val", "").trim();
                        // getDiPars packs multiple values into the "val" string separated by
                        // the batch delimiter. Split them if present.
                        if (val.contains(BATCH_DELIMITER)) {
                            for (String part : val.split(BATCH_DELIMITER, -1)) {
                                values.add(part.trim());
                            }
                        } else {
                            values.add(val);
                        }
                    } else {
                        // JSON envelope without a known value field — nothing to parse.
                        return values;
                    }
                } else {
                    org.json.JSONArray arr = new org.json.JSONArray(trimmed);
                    for (int i = 0; i < arr.length(); i++) {
                        values.add(String.valueOf(arr.opt(i)).trim());
                    }
                }
            } catch (Exception e) {
                LogBuffer.d("CANReader", "getDiPars JSON parse failed, falling back to delimiter: " + e.getMessage());
                values.clear();
            }
        }

        // Fallback / default: split on the control character delimiter.
        // Only do this for non-JSON responses; JSON envelopes (e.g. {"success":false})
        // should not be split into a single meaningless value.
        if (values.isEmpty() && !isJson) {
            for (String part : trimmed.split(BATCH_DELIMITER, -1)) {
                values.add(part.trim());
            }
        }

        // If the count does not match, the delimiter may have been stripped or a value
        // contained it. Return empty so the caller falls back to getVal.
        if (values.size() != expectedCount) {
            LogBuffer.w("CANReader", "getDiPars value count mismatch: expected " + expectedCount
                    + ", got " + values.size());
            return new ArrayList<>();
        }
        return values;
    }

    /**
     * Extract a human-readable failure reason from a getDiPars JSON error envelope.
     * DiPlus builds report errors under different fields; fall back to the raw body
     * so the log never shows a bare "null".
     */
    private static String extractDiParsErrorReason(JSONObject obj, String rawBody) {
        for (String field : new String[]{"reason", "error", "message", "msg"}) {
            String reason = obj.optString(field, "").trim();
            if (!reason.isEmpty()) return reason;
        }
        return rawBody;
    }

    private static List<CANDataItem> diplusReadSignals(Context context, List<CANDataItem> items) {
        ensureExecutors();
        LogBuffer.i("CANReader", "diplusReadSignals: reading " + items.size() + " signals via Chinese names");

        // Fallback path: read every signal, including ones previously marked
        // unsupported, so a firmware update can make them available again.
        final Context ctx = context;
        List<Future<CANDataItem>> futures = new ArrayList<>();
        for (final CANDataItem item : items) {
            futures.add(getValExecutor.submit(new Callable<CANDataItem>() {
                @Override
                public CANDataItem call() {
                    return readSingleSignal(ctx, item);
                }
            }));
        }

        List<CANDataItem> result = new ArrayList<>();
        int successCount = 0;
        for (Future<CANDataItem> future : futures) {
            try {
                CANDataItem copy = future.get(READ_TIMEOUT_MS + 1000, TimeUnit.MILLISECONDS);
                if (copy != null) {
                    result.add(copy);
                    successCount++;
                    cacheSupported(context, copy.diplusName);
                }
            } catch (Exception e) {
                LogBuffer.d("CANReader", "getVal task failed: " + e.getMessage());
            }
        }
        LogBuffer.i("CANReader", "diplusReadSignals: got " + successCount + "/" + items.size() + " signals");
        if (result.isEmpty()) return null;
        return mergeUnsupportedItems(context, items, result);
    }

    private static CANDataItem readSingleSignal(Context context, CANDataItem item) {
        String chineseName = item.diplusName;
        if (chineseName == null || chineseName.isEmpty()) return null;
        requestCounter.incrementAndGet();
        HttpURLConnection conn = null;
        try {
            String encoded = URLEncoder.encode(chineseName, "UTF-8");
            URL url = new URL(DiplusApi.withAuth(DIPLUS_BASE + "/api/getVal?name=" + encoded + "&status=true", AppConfig.getDiplusAuth(context)));
            conn = openConnection(url, "GET", CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
            int code = conn.getResponseCode();
            if (code != 200) return null;
            String body = readResponseBodyStr(conn);
            if (body == null || body.contains("\"success\":false")) return null;

            String val = parseDiplusValueJson(url.toString(), body);
            if (val == null || val.isEmpty()) return null;

            CANDataItem copy = new CANDataItem(item.canId, item.name, item.unit, item.route);
            copy.diplusName = item.diplusName;
            copy.key = item.key;
            copy.rawData = item.rawData;
            copy.value = applyScale(item.key, val);
            copy.lastUpdate = System.currentTimeMillis();
            if (copy.key != null && !copy.key.isEmpty()) {
                lastKnownRawValues.put(copy.key, val);
            }
            return copy;
        } catch (Exception e) {
            LogBuffer.d("CANReader", "getVal failed for '" + chineseName + "': " + e.getMessage());
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * Read the current raw value of a single sensor from DiPlus synchronously.
     * Used by command verification to check whether a command really changed
     * the vehicle state. Returns null when the sensor is unknown or DiPlus
     * does not respond.
     */
    public static String querySensorValueSync(Context context, String sensorKey) {
        if (sensorKey == null || sensorKey.isEmpty()) return null;
        CANDataItem item = findSignalByKey(sensorKey);
        if (item == null || item.diplusName == null || item.diplusName.isEmpty()) {
            LogBuffer.w("CANReader", "querySensorValueSync: unknown sensor key " + sensorKey);
            return null;
        }
        try {
            CANDataItem result = readSingleSignal(context, item);
            if (result != null && result.value != null) {
                // result.value is scaled; return the raw value from the cache
                // which was stored before scaling.
                String raw = lastKnownRawValues.get(sensorKey);
                LogBuffer.d("CANReader", "querySensorValueSync " + sensorKey + " -> raw=" + raw + " scaled=" + result.value);
                return raw;
            }
        } catch (Exception e) {
            LogBuffer.e("CANReader", "querySensorValueSync failed for " + sensorKey + ": " + e.getMessage());
        }
        return null;
    }

    /** Return the last known raw value for a sensor, or null if never received. */
    public static String getLastKnownRawValue(String sensorKey) {
        return sensorKey != null ? lastKnownRawValues.get(sensorKey) : null;
    }

    /**
     * Parse a getVal JSON response. Logs the raw response for debugging.
     * Tries multiple known field names because diplus firmware variants use
     * different envelopes ({"success":true,"val":"..."}, {"value":"..."}, etc.).
     */
    private static String parseDiplusValueJson(String url, String json) {
        logRawResponse(url, json);
        try {
            JSONObject obj = new JSONObject(json);
            if (obj.has("success") && !obj.optBoolean("success", false)) {
                return null;
            }
            // Try common value field names in order of likelihood.
            for (String key : new String[]{"val", "value", "data", "result", "v"}) {
                if (obj.has(key)) {
                    String value = obj.optString(key, null);
                    if (value != null && !value.isEmpty()) {
                        return value;
                    }
                }
            }
            return null;
        } catch (Exception e) {
            LogBuffer.d("CANReader", "getVal JSON parse error: " + e.getMessage());
            return null;
        }
    }

    private static HttpURLConnection openConnection(URL url, String method, int connectTimeout, int readTimeout)
            throws Exception {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(connectTimeout);
        conn.setReadTimeout(readTimeout);
        conn.setRequestMethod(method);
        conn.setUseCaches(false);
        return conn;
    }

    private static boolean diplusPing() {
        LogBuffer.i("CANReader", "diplusPing: GET " + DIPLUS_BASE + "/");
        HttpURLConnection conn = null;
        try {
            conn = openConnection(new URL(DIPLUS_BASE + "/"), "GET", 2000, 2000);
            int code = conn.getResponseCode();
            boolean alive = code >= 200 && code < 300;
            LogBuffer.i("CANReader", "diplusPing: " + (alive ? "alive" : "not alive") + " (HTTP " + code + ")");
            return alive;
        } catch (Exception e) {
            LogBuffer.w("CANReader", "diplusPing: not responding (" + e.getMessage() + ")");
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** Launch diplus if it's not running. */
    public static void launchDiplus(Context context) {
        LogBuffer.i("CANReader", "launchDiplus: starting com.van.diplus/.activity.StartMainServiceActivity");
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(
                "com.van.diplus",
                "com.van.diplus.activity.StartMainServiceActivity"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            LogBuffer.i("CANReader", "launchDiplus: intent sent successfully");
        } catch (Exception e) {
            LogBuffer.e("CANReader", "Cannot launch diplus: " + e.getMessage());
        }
    }

    // ─── System properties (VVIN, FW, getprop attributes) ───

    private static List<CANDataItem> tryDumpsys(List<CANDataItem> items) {
        LogBuffer.i("CANReader", "Reading system properties via direct getprop");
        try {
            List<CANDataItem> result = new ArrayList<>();

            // VVIN (virtual VIN)
            String vin = getSystemProp("persist.sys.cloud.last_vin");
            if ("---".equals(vin)) vin = getSystemProp("sys.virtual.vin");
            if (!"---".equals(vin)) {
                sVin = vin;
                LogBuffer.i("CANReader", "VVIN: " + sVin);
            }

            // Firmware
            String fw = getSystemProp("sys.tcp_client_ver");
            if ("---".equals(fw)) fw = getSystemProp("ro.vehicle.type");
            if (!"---".equals(fw)) {
                sFirmware = fw;
                LogBuffer.i("CANReader", "FW: " + sFirmware);
            }

            CANDataItem vinItem = new CANDataItem(0, "VVIN (virtual VIN)", "", -1);
            vinItem.key = "vvin";
            vinItem.value = sVin;
            vinItem.lastUpdate = System.currentTimeMillis();
            result.add(vinItem);

            CANDataItem fwItem = new CANDataItem(0, "FW (firmware)", "", -1);
            fwItem.key = "firmware";
            fwItem.value = sFirmware;
            fwItem.lastUpdate = System.currentTimeMillis();
            result.add(fwItem);

            String[] propKeys = {
                "ro.vehicle.type", "ro.vehicle.type.value", "ro.car.protocol",
                "sys.tcp_client_ver", "persist.sys.byd.default_name",
                "persist.sys.byd.bluetooth_name", "persist.sys.byd.theme",
                "persist.sys.vehicle_40d_code", "persist.sys.vehicle_sales_record",
                "persist.sys.vehicle_rudder_info", "vehicle.config.map",
                "sys.byd.cdr_recording", "sys.byd.pano",
            };
            for (String propKey : propKeys) {
                String val = getSystemProp(propKey);
                if (!"---".equals(val) && !val.isEmpty()) {
                    CANDataItem propItem = new CANDataItem(0, propKey, "", -1);
                    propItem.key = propKeyToSnake(propKey);
                    propItem.value = val;
                    propItem.lastUpdate = System.currentTimeMillis();
                    result.add(propItem);
                }
            }

            LogBuffer.i("CANReader", "getprop returned " + result.size() + " items");
            return result;
        } catch (Exception e) {
            LogBuffer.e("CANReader", "getprop exception: " + e.toString());
        }
        return null;
    }

    private static String getSystemProp(String key) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"getprop", key});
            BufferedReader br = new BufferedReader(
                new InputStreamReader(p.getInputStream(), "UTF-8"));
            String value = br.readLine();
            br.close();
            p.waitFor(3, TimeUnit.SECONDS);
            if (value != null && !value.isEmpty()) {
                return value.trim();
            }
        } catch (Exception e) {
            LogBuffer.d("CANReader", "getprop '" + key + "' failed: " + e.getMessage());
        }
        return "---";
    }

    private static String propKeyToSnake(String propKey) {
        if (propKey == null || propKey.isEmpty()) return "unknown_prop";
        String safe = propKey.replaceAll("[^a-zA-Z0-9]", "_").replaceAll("_+", "_");
        if (safe.startsWith("_")) safe = safe.substring(1);
        if (safe.endsWith("_")) safe = safe.substring(0, safe.length() - 1);
        return safe.isEmpty() ? "unknown_prop" : safe;
    }

    // ─── Raw response logging / diagnostics ───

    /** Log a raw server response, truncated if extremely large. */
    private static void logRawResponse(String url, String body) {
        if (body == null) {
            LogBuffer.d("CANReader", "RAW " + url + " → (null body)");
            return;
        }
        int maxLen = 2000;
        String preview = body.length() > maxLen
                ? body.substring(0, maxLen) + "... [" + body.length() + " bytes total]"
                : body;
        LogBuffer.d("CANReader", "RAW " + url + " → " + preview);
    }

    /**
     * Probe all documented diplus HTTP endpoints and log raw responses.
     * This is intended for debugging and is called once on first successful ping
     * or on user request.
     */
    public static void probeAllEndpoints(final Context context) {
        executor.submit(() -> {
            try {
                LogBuffer.i("CANReader", "=== Probing all documented diplus endpoints ===");
                if (!isDiplusAlive()) {
                    LogBuffer.w("CANReader", "diplus not responding, skipping probe");
                    return;
                }

                // Basic root ping.
                probeHttp("GET", DIPLUS_BASE + "/", null);

                // Single-value reads.
                probeHttp("GET", DIPLUS_BASE + "/api/getVal?name=车速&status=true", null);
                probeHttp("GET", DIPLUS_BASE + "/api/getVal?name=车速", null);

                // Batch read.
                try {
                    String text = URLEncoder.encode("[车速]", "UTF-8");
                    probeHttp("GET", DIPLUS_BASE + "/api/getDiPars?text=" + text, null);
                } catch (Exception ignored) {}

                // Command endpoint.
                probeHttp("GET", DIPLUS_BASE + "/api/sendCmd?cmd=help", null);

                // Video endpoints.
                probeHttp("GET", DIPLUS_BASE + "/api/videoDirs", null);
                probeHttp("GET", DIPLUS_BASE + "/api/videoFiles", null);
                probeHttp("GET", DIPLUS_BASE + "/api/videoInfo?key=demo", null);
                probeHttp("GET", DIPLUS_BASE + "/api/videoStream?key=demo", null);

                // Trigger/XD endpoints (POST with minimal JSON).
                probeHttp("POST", DIPLUS_BASE + "/api/setTrigger", "{}");
                probeHttp("POST", DIPLUS_BASE + "/api/setTriggerV2", "{}");
                probeHttp("POST", DIPLUS_BASE + "/api/setXD", "{}");

                LogBuffer.i("CANReader", "=== End diplus probe ===");
            } catch (Exception e) {
                LogBuffer.e("CANReader", "probeAllEndpoints failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
    }

    private static void probeHttp(String method, String urlString, String postBody) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = openConnection(url, method, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
            if ("POST".equals(method) && postBody != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                try (java.io.OutputStream os = conn.getOutputStream()) {
                    os.write(postBody.getBytes("UTF-8"));
                }
            }
            int code = conn.getResponseCode();
            String body = readResponseBodyStr(conn);
            LogBuffer.i("CANReader", "PROBE " + method + " " + urlString + " → HTTP " + code
                    + " body=" + (body != null ? body.substring(0, Math.min(body.length(), 300)) : "null"));
        } catch (Exception e) {
            LogBuffer.i("CANReader", "PROBE " + method + " " + urlString + " → ERROR: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }


    /**
     * Synchronous HTTP helper used by the tester UI. Runs on a background thread.
     *
     * @param method  GET or POST
     * @param urlString full URL including query string
     * @param body    optional POST body (application/json)
     * @return response body string
     * @throws Exception on network or HTTP error
     */
    public static String sendRequestSync(String method, String urlString, String body) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = openConnection(url, method, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
            if ("POST".equals(method) && body != null && !body.isEmpty()) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes("UTF-8"));
                }
            }
            int code = conn.getResponseCode();
            String response = readResponseBodyStr(conn);
            if (code < 200 || code >= 300) {
                throw new Exception("HTTP " + code + ": " + (response != null ? response : ""));
            }
            return response != null ? response : "";
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    // ─── HTTP helpers ───

    private static String readResponseBodyStr(HttpURLConnection conn) {
        InputStream is = null;
        InputStream es = null;
        try {
            is = conn.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            return sb.toString();
        } catch (Exception e) {
            try {
                es = conn.getErrorStream();
                if (es != null) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(es, "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();
                    return sb.toString();
                }
            } catch (Exception ignored) {}
        } finally {
            if (is != null) try { is.close(); } catch (Exception ignored) {}
            if (es != null) try { es.close(); } catch (Exception ignored) {}
        }
        return null;
    }
}
