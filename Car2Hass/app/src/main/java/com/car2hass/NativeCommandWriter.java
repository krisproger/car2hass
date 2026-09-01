package com.car2hass;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Executes a native autoservice write: {@code service call autoservice 6
 * i32 <dev> i32 <fid> i32 <value>} through the existing ADB channel.
 *
 * <p>Semantics follow BYDMate's HelperDaemon: the reply parcel's first int is
 * the setInt status (1 = real action, 0 = no-op, &lt;0 = error, -999 = no
 * parcel data). A write is {@code success} only when status == 1; anything
 * else is {@code rejected} and the caller falls back to DiPlus.
 *
 * <p>The shell call is delegated to a {@link NativeReader.ShellRunner} so unit
 * tests can stub the ADB transport (the production runner wires up
 * {@link AdbShellExecutor#executeSync}).
 */
public class NativeCommandWriter {

    private static final String TAG = "NativeWrite";

    /** Matches the first 8-hex-digit token after {@code Parcel(00000000}. */
    private static final Pattern PARCEL_REGEX =
            Pattern.compile("Parcel\\(00000000\\s+([0-9a-fA-F]{8})");

    /** Successful setInt status. */
    public static final int STATUS_OK = 1;

    public static class Result {
        public final boolean success;
        public final int status;
        public final String error;
        public final long elapsedMs;

        Result(boolean success, int status, String error, long elapsedMs) {
            this.success = success;
            this.status = status;
            this.error = error;
            this.elapsedMs = elapsedMs;
        }
    }

    private final NativeReader.ShellRunner runner;
    private final String host;
    private final int port;

    public NativeCommandWriter(NativeReader.ShellRunner runner, String host, int port) {
        this.runner = runner;
        this.host = host;
        this.port = port;
    }

    /** Builds the one-shot shell command for a single autoservice setInt. */
    public static String buildCommand(int dev, int fid, int value) {
        return "service call autoservice 6 i32 " + dev + " i32 " + fid + " i32 " + value;
    }

    /**
     * Executes one write and classifies the outcome.
     *
     * @return {@code success=true} on status 1; {@code success=false} with a
     *         parsed {@code status} on a rejected write; {@code success=false}
     *         with {@code status=-999} when the ADB exec failed or timed out
     *         (unavailable)
     */
    public Result write(int dev, int fid, int value) {
        long t0 = System.currentTimeMillis();
        String command = buildCommand(dev, fid, value);
        String output = runner.run(host, port, command);
        long elapsed = System.currentTimeMillis() - t0;

        if (output == null) {
            LogBuffer.w(TAG, "ADB exec failed or timed out for dev=" + dev + " fid=" + fid);
            return new Result(false, -999, "ADB exec failed or timed out", elapsed);
        }

        int status = parseStatus(output);
        if (status == STATUS_OK) {
            LogBuffer.i(TAG, "write ok dev=" + dev + " fid=" + fid
                    + " value=" + value + " in " + elapsed + " ms");
            return new Result(true, status, null, elapsed);
        }
        LogBuffer.w(TAG, "write rejected dev=" + dev + " fid=" + fid
                + " value=" + value + " status=" + status + " raw=" + output.trim());
        return new Result(false, status, "autoservice setInt rejected (status " + status + ")", elapsed);
    }

    /** First int of the reply parcel, or -999 when the parcel could not be parsed. */
    static int parseStatus(String output) {
        if (output == null) {
            return -999;
        }
        Matcher m = PARCEL_REGEX.matcher(output);
        if (!m.find()) {
            LogBuffer.w(TAG, "write reply has no parcel data: " + output.trim());
            return -999;
        }
        try {
            return (int) Long.parseLong(m.group(1), 16);
        } catch (NumberFormatException e) {
            return -999;
        }
    }

    static String logDevFid(int dev, int fid) {
        return String.format(Locale.US, "dev=%d fid=%d", dev, fid);
    }
}
