package com.car2hass;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Captures uncaught exceptions to Downloads and to a private fallback file.
 *
 * <p>The crash log is written with a timestamped name so it is easy to find and
 * share. If writing to Downloads fails (e.g. no permission), the same content is
 * saved to the app's private {@code crash_log.txt} as a last resort.</p>
 */
public class CrashLogger implements Thread.UncaughtExceptionHandler {

    private final Context context;
    private final Thread.UncaughtExceptionHandler defaultHandler;

    public CrashLogger(Context context) {
        this.context = context.getApplicationContext();
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    public void register() {
        LogBuffer.init(context);
        Thread.setDefaultUncaughtExceptionHandler(this);
        LogBuffer.i("CrashLogger", "Uncaught exception handler registered");
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        String crashText = buildCrashText(thread, throwable);
        // Write to persistent log first so the crash is available even if
        // the Downloads save path fails.
        LogBuffer.e("CrashLogger", "FATAL CRASH:\n" + crashText);
        boolean savedToDownloads = false;

        try {
            byte[] bytes = crashText.getBytes("UTF-8");
            String fileName = "Car2Hass_crash_"
                    + new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date())
                    + ".txt";
            Uri uri = LogExportHelper.saveLogToDownloads(context, bytes, fileName);
            if (uri != null) {
                savedToDownloads = true;
            }
        } catch (Exception e) {
            // Fallback below.
        }

        if (!savedToDownloads) {
            savePrivateFallback(crashText);
        }

        if (defaultHandler != null) {
            defaultHandler.uncaughtException(thread, throwable);
        }
    }

    private String buildCrashText(Thread thread, Throwable throwable) {
        java.io.StringWriter sw = new java.io.StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println("=== Car2Hass Crash ===");
        pw.println("Time: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                .format(new Date()));
        pw.println("Thread: " + (thread != null ? thread.getName() : "unknown"));
        pw.println("Device: " + android.os.Build.BRAND + " " + android.os.Build.MODEL);
        pw.println("Android: " + android.os.Build.VERSION.RELEASE + " (SDK "
                + android.os.Build.VERSION.SDK_INT + ")");
        pw.println();
        throwable.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }

    private void savePrivateFallback(String crashText) {
        try {
            File file = new File(context.getFilesDir(), "crash_log.txt");
            try (FileWriter fw = new FileWriter(file, true);
                 PrintWriter pw = new PrintWriter(fw)) {
                pw.println("--- Crash at " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                        .format(new Date()) + " ---");
                pw.print(crashText);
                pw.println();
            }
        } catch (IOException e) {
            android.util.Log.e("CrashLogger", "Cannot write crash log", e);
        }
    }
}
