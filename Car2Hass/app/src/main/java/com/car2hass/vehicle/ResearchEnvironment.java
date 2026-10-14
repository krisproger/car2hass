package com.car2hass.vehicle;

import android.app.ActivityManager;
import android.content.Context;
import android.os.BatteryManager;
import android.os.PowerManager;

import com.car2hass.AdbShellExecutor;
import com.car2hass.AppConfig;
import com.car2hass.LogBuffer;

import org.json.JSONObject;

import java.util.List;

/**
 * Best-effort environment snapshot for research diagnostics: memory, screen state,
 * battery, the foreground package/activity and channel reachability. Used to
 * understand a "minimize"/crash during research (which app took the foreground,
 * how much memory was free, whether DiPlus/ADB were alive).
 */
public final class ResearchEnvironment {

    private ResearchEnvironment() {}

    // Production wiring: expose this implementation to VehicleResearch without
    // forcing the Android dependency chain into the plain-Java test harness.
    static {
        VehicleResearch.envDiag = new VehicleResearch.EnvDiag() {
            @Override public void snapshot(android.content.Context c, String phase) { logSnapshot(c, phase); }
            @Override public String focus(android.content.Context c) { return foregroundFocus(c); }
            @Override public JSONObject toJson(android.content.Context c) { return ResearchEnvironment.toJson(c); }
        };
    }

    /** Memory in MB: {used, max, total}. */
    public static int[] memoryMb() {
        Runtime rt = Runtime.getRuntime();
        long used = rt.totalMemory() - rt.freeMemory();
        return new int[]{
                (int) (used / 1048576),
                (int) (rt.maxMemory() / 1048576),
                (int) (rt.totalMemory() / 1048576),
        };
    }

    /** Own-process importance name from ActivityManager (best effort, may be null). */
    public static String ownImportance(Context ctx) {
        if (ctx == null) return null;
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return null;
            int pid = android.os.Process.myPid();
            List<ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
            if (procs != null) {
                for (ActivityManager.RunningAppProcessInfo p : procs) {
                    if (p.pid == pid) return importanceName(p.importance);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String importanceName(int importance) {
        switch (importance) {
            case ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND: return "foreground";
            case ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE: return "foreground_service";
            case ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE: return "visible";
            case ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE: return "service";
            case ActivityManager.RunningAppProcessInfo.IMPORTANCE_BACKGROUND: return "background/cached";
            default: return "importance_" + importance;
        }
    }

    /**
     * Foreground window package/activity via ADB dumpsys (best effort; null when
     * ADB is unavailable — e.g. Voyah). Format: "pkg/activity".
     */
    public static String foregroundFocus(Context ctx) {
        if (ctx == null) return null;
        try {
            AdbShellExecutor.init(ctx);
            String host = AppConfig.getAdbHost(ctx);
            int port = AppConfig.getAdbPort(ctx);
            String out = AdbShellExecutor.executeSync(host, port,
                    "dumpsys window windows | grep -E 'mCurrentFocus|mFocusedApp'", 3000);
            if (out == null) return null;
            String s = out.replace('\n', ' ').trim();
            if (s.isEmpty()) return null;
            int i = s.indexOf("u0 ");
            return i >= 0 ? s.substring(i + 3).trim() : s;
        } catch (Exception e) {
            return null;
        }
    }

    /** True when the foreground focus belongs to another app (not Car2Hass). */
    public static boolean foregroundIsOther(String focus) {
        return focus != null && !focus.isEmpty()
                && !focus.contains("com.car2hass");
    }

    /** Reachability of DiPlus (8988) and ADB (5555) without heavy probes. */
    private static boolean portOpen(String host, int port) {
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress(host, port), 400);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** One-line environment summary for the log. */
    public static void logSnapshot(Context ctx, String phase) {
        try {
            int[] mem = memoryMb();
            LogBuffer.i("ResearchEnv", phase + ": importance=" + ownImportance(ctx)
                    + ", focus=" + foregroundFocus(ctx)
                    + ", heap=" + mem[0] + "/" + mem[1] + "MB"
                    + ", diplus8988=" + portOpen("127.0.0.1", 8988)
                    + ", adb5555=" + portOpen("127.0.0.1", 5555)
                    + ", obd=" + AppConfig.isObdEnabled(ctx));
        } catch (Exception e) {
            LogBuffer.d("ResearchEnv", "snapshot failed: " + e.getMessage());
        }
    }

    /** Structured environment block for the research report. */
    public static JSONObject toJson(Context ctx) {
        JSONObject o = new JSONObject();
        try {
            int[] mem = memoryMb();
            o.put("heap_used_mb", mem[0]);
            o.put("heap_max_mb", mem[1]);
            o.put("importance", ownImportance(ctx));
            o.put("focus", foregroundFocus(ctx));
            o.put("diplus_8988", portOpen("127.0.0.1", 8988));
            o.put("adb_5555", portOpen("127.0.0.1", 5555));
            o.put("obd_enabled", AppConfig.isObdEnabled(ctx));
            if (ctx != null) {
                PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
                o.put("screen_on", pm != null && pm.isInteractive());
                BatteryManager bm = (BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
                if (bm != null) o.put("battery_pct", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY));
            }
        } catch (Exception e) {
            try { o.put("error", e.getMessage()); } catch (Exception ignored) {}
        }
        return o;
    }
}
