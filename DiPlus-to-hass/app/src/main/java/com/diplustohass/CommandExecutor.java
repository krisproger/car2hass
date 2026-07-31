package com.diplustohass;

import android.content.Context;

import java.util.List;
import java.util.Locale;

/**
 * Central executor for vehicle commands.
 *
 * <p>All command paths (Home Assistant poll, quick commands, selected commands)
 * should go through this class so that every command is logged and, when a
 * linked sensor exists, verified against the real vehicle state.</p>
 */
public class CommandExecutor {

    /** Source of the command, used only for logging. */
    public enum Source { HA, UI }

    private static final long VERIFY_INITIAL_DELAY_MS = 600;
    private static final long VERIFY_RETRY_DELAY_MS = 600;
    private static final int VERIFY_MAX_ATTEMPTS = 4;

    private static final long WINDOW_VERIFY_RETRY_DELAY_MS = 800;
    private static final int WINDOW_VERIFY_MAX_ATTEMPTS = 8;

    public static class Result {
        public final boolean success;
        public final String response;
        public final String error;
        public final long elapsedMs;
        public final boolean verified;
        public final String verificationMessage;
        public final String expectedValue;
        public final String actualValue;

        public Result(boolean success, String response, String error, long elapsedMs,
                      boolean verified, String verificationMessage,
                      String expectedValue, String actualValue) {
            this.success = success;
            this.response = response;
            this.error = error;
            this.elapsedMs = elapsedMs;
            this.verified = verified;
            this.verificationMessage = verificationMessage;
            this.expectedValue = expectedValue;
            this.actualValue = actualValue;
        }
    }

    /**
     * Execute a command and verify the result when a linked sensor is known.
     *
     * @param ctx       application context
     * @param commandId stable command id from {@link CommandRegistry}
     * @param value     optional parameter value (empty string when none)
     * @param source    where the command originated
     * @return execution and verification result
     */
    public static Result execute(Context ctx, String commandId, String value, Source source) {
        long start = System.currentTimeMillis();
        CommandRegistry.CommandEntry entry = CommandRegistry.getById(commandId);
        String chineseCommand = CommandRegistry.buildCommand(entry, value);

        LogBuffer.i("CommandExecutor",
            "[" + source + "] command id=" + commandId
                + " value=" + (value != null && !value.isEmpty() ? value : "-")
                + " chinese=" + (chineseCommand != null ? chineseCommand : "INVALID"));

        if (chineseCommand == null) {
            String msg = ctx.getString(R.string.commands_unknown_command, commandId,
                value != null ? value : "");
            LogBuffer.w("CommandExecutor", "Unknown or invalid command: " + commandId + " value=" + value);
            return new Result(false, null, msg, 0, false,
                ctx.getString(R.string.command_verify_no_link), null, null);
        }

        DiPlusCommandSender.Result sendResult;
        try {
            sendResult = DiPlusCommandSender.send(ctx, chineseCommand);
        } catch (Exception e) {
            String msg = e.getClass().getSimpleName() + ": " + e.getMessage();
            LogBuffer.e("CommandExecutor", "Send failed for " + chineseCommand + ": " + msg);
            return new Result(false, null, msg, System.currentTimeMillis() - start,
                false, ctx.getString(R.string.command_verify_send_failed), null, null);
        }

        long elapsed = System.currentTimeMillis() - start;
        LogBuffer.i("CommandExecutor",
            "DiPlus response success=" + sendResult.success
                + " elapsed=" + sendResult.elapsedMs + "ms response=" + sendResult.response);

        if (!sendResult.success) {
            String error = sendResult.error != null ? sendResult.error
                : ctx.getString(R.string.commands_result_fail);
            return new Result(false, sendResult.response, error, elapsed,
                false, ctx.getString(R.string.command_verify_send_failed), null, null);
        }

        // Verification: try to confirm the command really changed the vehicle state.
        Verification verification = verifyCommand(ctx, commandId, value);
        LogBuffer.i("CommandExecutor",
            "Verification for " + commandId + ": " + verification.message
                + (verification.actualValue != null ? " (actual=" + verification.actualValue + ")" : "")
                + (verification.expectedValue != null ? " (expected=" + verification.expectedValue + ")" : ""));

        String message = ctx.getString(R.string.commands_result_ok_short);
        return new Result(true, sendResult.response, message, elapsed,
            verification.verified, verification.message,
            verification.expectedValue, verification.actualValue);
    }

    private static class Verification {
        final boolean verified;
        final String message;
        final String expectedValue;
        final String actualValue;

        Verification(boolean verified, String message, String expectedValue, String actualValue) {
            this.verified = verified;
            this.message = message;
            this.expectedValue = expectedValue;
            this.actualValue = actualValue;
        }
    }

    private static Verification verifyCommand(Context ctx, String commandId, String value) {
        List<SensorCommandRegistry.SensorLink> links =
            SensorCommandRegistry.getInstance(ctx).getSensorsForCommand(commandId);
        if (links == null || links.isEmpty()) {
            return new Verification(false, ctx.getString(R.string.command_verify_no_link), null, null);
        }

        // Prefer the exact non-parameter link when value matches; otherwise use the
        // first parameter-based link.
        SensorCommandRegistry.SensorLink chosen = null;
        for (SensorCommandRegistry.SensorLink link : links) {
            if (link.needsParameter) {
                if (chosen == null) chosen = link;
                continue;
            }
            if (value != null && value.equals(link.expectedValue)) {
                chosen = link;
                break;
            }
            if (chosen == null) chosen = link;
        }
        if (chosen == null) {
            return new Verification(false, ctx.getString(R.string.command_verify_no_link), null, null);
        }

        String expected = chosen.needsParameter ? value : chosen.expectedValue;
        if (expected == null || expected.isEmpty()) {
            return new Verification(false, ctx.getString(R.string.command_verify_no_link), null, null);
        }

        // Give DiPlus a moment to apply the command before reading the sensor.
        sleep(VERIFY_INITIAL_DELAY_MS);

        int maxAttempts = VERIFY_MAX_ATTEMPTS;
        long retryDelay = VERIFY_RETRY_DELAY_MS;
        if (isWindowSensor(chosen.sensorKey)) {
            // Windows move slowly; allow more time for the requested position.
            maxAttempts = WINDOW_VERIFY_MAX_ATTEMPTS;
            retryDelay = WINDOW_VERIFY_RETRY_DELAY_MS;
        }

        String actual = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            actual = CANDataReader.querySensorValueSync(ctx, chosen.sensorKey);
            LogBuffer.d("CommandExecutor",
                "Verify attempt " + attempt + "/" + maxAttempts
                    + " sensor=" + chosen.sensorKey + " raw=" + actual);

            if (actual != null) {
                String translatedActual = SignalTranslator.translateEnumValue(chosen.sensorKey, actual);
                if (valuesMatch(expected, translatedActual, chosen.sensorKey)) {
                    return new Verification(true,
                        ctx.getString(R.string.command_verify_ok),
                        expected, translatedActual);
                }
            }
            if (attempt < maxAttempts) {
                sleep(retryDelay);
            }
        }

        String translatedActual = actual != null
            ? SignalTranslator.translateEnumValue(chosen.sensorKey, actual)
            : null;
        return new Verification(false,
            ctx.getString(R.string.command_verify_mismatch),
            expected, translatedActual);
    }

    /**
     * Compare expected and actual translated values.
     *
     * <p>For numeric sensors a small tolerance is allowed; otherwise a
     * case-insensitive string match is used.</p>
     */
    private static boolean valuesMatch(String expected, String actual, String sensorKey) {
        if (expected == null || actual == null) return false;
        try {
            double exp = Double.parseDouble(expected.replace(',', '.'));
            double act = Double.parseDouble(actual.replace(',', '.'));
            double tolerance = getNumericTolerance(sensorKey);
            return Math.abs(exp - act) <= tolerance;
        } catch (NumberFormatException e) {
            return expected.equalsIgnoreCase(actual.trim());
        }
    }

    private static double getNumericTolerance(String sensorKey) {
        // Window positions are reported as integers; allow a small rounding window.
        if (sensorKey != null && sensorKey.startsWith("window_")) return 2.0;
        if ("sunroof".equals(sensorKey) || "sunshade".equals(sensorKey)) return 2.0;
        // Temperature: allow 1.0 °C tolerance (car may round the target).
        if ("ac_set_temp".equals(sensorKey)) return 1.0;
        // SOC and generic percentages.
        if ("soc".equals(sensorKey)) return 1.0;
        // Media volume may not match the requested value exactly.
        if ("media_volume".equals(sensorKey)) return 5.0;
        return 0.01;
    }

    private static boolean isWindowSensor(String sensorKey) {
        if (sensorKey == null) return false;
        return sensorKey.startsWith("window_") || "sunroof".equals(sensorKey) || "sunshade".equals(sensorKey);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
