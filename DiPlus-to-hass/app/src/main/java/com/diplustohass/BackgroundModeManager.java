package com.diplustohass;

import android.content.Context;
import android.content.pm.PackageManager;

public class BackgroundModeManager {

    private static final String TAG = "BackgroundModeManager";
    private static final String KEY_BACKGROUND_MODE = "background_mode_enabled";

    public interface ResultCallback {
        void onResult(boolean success, String message);
    }

    public static boolean isEnabled(Context ctx) {
        return ctx.getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_BACKGROUND_MODE, false);
    }

    public static void setEnabled(Context ctx, boolean enabled) {
        ctx.getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_BACKGROUND_MODE, enabled).apply();
        LogBuffer.i(TAG, "Background mode set to " + enabled);
    }

    public static boolean hasWriteSecureSettings(Context ctx) {
        return ctx.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasDevicePower(Context ctx) {
        return ctx.checkSelfPermission("android.permission.DEVICE_POWER")
                == PackageManager.PERMISSION_GRANTED;
    }

    public static void applyMode(Context ctx, ResultCallback callback) {
        if (!isEnabled(ctx)) {
            callback.onResult(true, ctx.getString(R.string.background_mode_disabled));
            return;
        }

        final String pkg = ctx.getPackageName();
        final int[] pending = {4};
        final boolean[] allSuccess = {true};
        final StringBuilder msg = new StringBuilder();

        ResultCallback step = new ResultCallback() {
            @Override
            public void onResult(boolean success, String message) {
                if (!success) {
                    allSuccess[0] = false;
                }
                msg.append(message).append("\n");
                pending[0]--;
                if (pending[0] == 0) {
                    callback.onResult(allSuccess[0], msg.toString().trim());
                }
            }
        };

        grantWriteSecureSettings(ctx, pkg, step);
        grantDevicePower(ctx, pkg, step);
        enableAllowOnlineWhileCarOff(ctx, step);
        whitelistBatteryOptimization(ctx, pkg, step);
    }

    private static void grantWriteSecureSettings(Context ctx, String pkg, ResultCallback callback) {
        String command = "pm grant " + pkg + " android.permission.WRITE_SECURE_SETTINGS";
        LogBuffer.i(TAG, "Step 1/4: granting WRITE_SECURE_SETTINGS");
        executeWithFallback(ctx, command, callback,
                "WRITE_SECURE_SETTINGS granted",
                "WRITE_SECURE_SETTINGS grant failed");
    }

    private static void grantDevicePower(Context ctx, String pkg, ResultCallback callback) {
        String command = "pm grant " + pkg + " android.permission.DEVICE_POWER";
        LogBuffer.i(TAG, "Step 2/4: granting DEVICE_POWER");
        executeWithFallback(ctx, command, callback,
                "DEVICE_POWER granted",
                "DEVICE_POWER grant failed");
    }

    private static void enableAllowOnlineWhileCarOff(Context ctx, ResultCallback callback) {
        String command = "settings put system allow_online_while_car_off 1";
        LogBuffer.i(TAG, "Step 3/4: enabling allow_online_while_car_off");
        executeWithFallback(ctx, command, callback,
                "allow_online_while_car_off set",
                "allow_online_while_car_off failed");
    }

    private static void whitelistBatteryOptimization(Context ctx, String pkg, ResultCallback callback) {
        String command = "dumpsys deviceidle whitelist +" + pkg;
        LogBuffer.i(TAG, "Step 4/4: whitelisting package from battery optimization");
        executeWithFallback(ctx, command, callback,
                "Device idle whitelist added",
                "Whitelist failed");
    }

    /**
     * Attempts to execute the command via {@link AdbShellExecutor}, then falls back
     * to {@link AdbCommandExecutor#executeSu(String, AdbCommandExecutor.AdbCallback)}
     * if the first attempt fails.
     */
    private static void executeWithFallback(Context ctx, String command, ResultCallback callback,
                                            String successMessage, String failurePrefix) {
        AdbShellExecutor.execute(ctx, command, new AdbShellExecutor.AdbShellCallback() {
            @Override
            public void onSuccess(String output) {
                LogBuffer.i(TAG, successMessage + " via AdbShellExecutor");
                callback.onResult(true, successMessage);
            }

            @Override
            public void onError(String output, Exception e) {
                String reason = e != null ? e.getClass().getSimpleName() + ": " + e.getMessage() : output;
                LogBuffer.w(TAG, failurePrefix + " via AdbShellExecutor: " + reason
                        + ". Falling back to su.");
                executeSuFallback(command, callback, successMessage, failurePrefix);
            }

            @Override
            public void onFailure(String reason) {
                LogBuffer.w(TAG, failurePrefix + " via AdbShellExecutor: " + reason
                        + ". Falling back to su.");
                executeSuFallback(command, callback, successMessage, failurePrefix);
            }
        });
    }

    private static void executeSuFallback(String command, ResultCallback callback,
                                          String successMessage, String failurePrefix) {
        AdbCommandExecutor.executeSu(command, new AdbCommandExecutor.AdbCallback() {
            @Override
            public void onSuccess(String output) {
                LogBuffer.i(TAG, successMessage + " via su fallback");
                callback.onResult(true, successMessage);
            }

            @Override
            public void onError(int code, String err) {
                LogBuffer.e(TAG, failurePrefix + " via su fallback (" + code + "): " + err);
                callback.onResult(false, failurePrefix + " (" + code + "): " + err);
            }

            @Override
            public void onException(Exception e) {
                LogBuffer.e(TAG, failurePrefix + " via su fallback exception: " + e.getMessage());
                callback.onResult(false, failurePrefix + " exception: " + e.getMessage());
            }
        });
    }

    public static String[] getManualCommands(Context ctx) {
        String pkg = ctx.getPackageName();
        return new String[]{
                "adb tcpip 5555",
                "adb shell pm grant " + pkg + " android.permission.WRITE_SECURE_SETTINGS",
                "adb shell pm grant " + pkg + " android.permission.DEVICE_POWER",
                "adb shell settings put system allow_online_while_car_off 1",
                "adb shell dumpsys deviceidle whitelist +" + pkg
        };
    }
}
