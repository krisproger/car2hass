package com.car2hass;

import android.content.Context;
import android.content.pm.PackageInfo;

public class AppInfo {

    /**
     * Raw versionName (e.g. "3.0.1") for machine comparisons — the decorated
     * getVersionString() must never feed semver comparison.
     */
    public static String getVersionName(Context ctx) {
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            return pi.versionName != null ? pi.versionName : "0.0.0";
        } catch (Exception e) {
            return "0.0.0";
        }
    }

    /**
     * Returns the user-facing version string, e.g. "1.9.1 (build 128)".
     * Prefers the generated BuildConfig values; falls back to PackageInfo
     * if BuildConfig is not available (should never happen in the APK).
     */
    public static String getVersionString(Context ctx) {
        String name = BuildConfig.VERSION_NAME;
        int code = BuildConfig.VERSION_CODE;
        if (name == null || name.isEmpty()) {
            try {
                PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
                name = pi.versionName;
                code = pi.versionCode;
            } catch (Exception e) {
                name = "?";
                code = 0;
            }
        }
        return name + " (build " + code + ")";
    }
}
