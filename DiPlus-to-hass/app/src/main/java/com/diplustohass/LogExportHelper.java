package com.diplustohass;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Collects the app log + a filtered logcat snapshot and writes it to Downloads.
 * Used both by the manual "Send log" button and by the automatic boot dump.
 */
public class LogExportHelper {

    private static final String LOG_FILE_PREFIX = "DiPlus-to-hass_log_";
    private static final String LOG_FILE_SUFFIX = ".txt";
    private static final SimpleDateFormat FILE_DATE = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US);

    private static String logFileName() {
        return LOG_FILE_PREFIX + FILE_DATE.format(new Date()) + LOG_FILE_SUFFIX;
    }

    public static byte[] buildLogBytes(Context context) {
        StringBuilder fullLog = new StringBuilder();
        String appVer = AppInfo.getVersionString(context);

        fullLog.append("=== DiPlus-to-hass v").append(appVer).append(" Log ===\n");
        fullLog.append("Device: ").append(getDeviceInfo()).append("\n");
        fullLog.append("Time: ").append(new Date().toString()).append("\n");
        fullLog.append("Source: All signals (merged)\n\n");

        // In-memory buffer and persistent file log overlap (same events
        // replayed into both sections). Dedup by full line text (timestamp
        // included): a repeated timestamp line is the same event seen again.
        // Legitimate repeats carry different timestamps and survive.
        java.util.List<String> memoryLines = LogDedup.dedupeLines(LogBuffer.getText());
        fullLog.append("--- In-memory App Log ---\n");
        appendLines(fullLog, memoryLines);
        fullLog.append("\n");

        fullLog.append("--- Persistent File Log ---\n");
        appendLines(fullLog, LogDedup.dedupeLines(LogBuffer.getFileLogText(), memoryLines));
        fullLog.append("\n");

        fullLog.append("--- Logcat (DiPlus-to-hass) ---\n");
        try {
            Process p = Runtime.getRuntime().exec(
                    new String[]{"logcat", "-d", "-t", "300", "-s",
                            "DiPlus-to-hass", "CANReader", "Main", "TelemetryService",
                            "BootReceiver", "BootActivity", "HassSettings", "HassClient"});
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) {
                fullLog.append(line).append('\n');
            }
            br.close();
        } catch (Exception e) {
            fullLog.append("(logcat error: ").append(e.getMessage()).append(")\n");
        }

        return fullLog.toString().getBytes();
    }

    /**
     * Collect the log bytes like {@link #buildLogBytes(Context)} and, when a
     * vehicle research report path is given, append its contents.
     */
    public static byte[] buildLogBytes(Context context, String researchPath) {
        byte[] base = buildLogBytes(context);
        if (researchPath == null) return base;
        File f = new File(researchPath);
        if (!f.isFile()) return base;
        try (FileInputStream in = new FileInputStream(f)) {
            StringBuilder sb = new StringBuilder(new String(base, "UTF-8"));
            sb.append("\n--- Research Report ---\n");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) sb.append(new String(buf, 0, n, "UTF-8"));
            return sb.toString().getBytes("UTF-8");
        } catch (Exception e) {
            LogBuffer.w("LogExportHelper", "research attach fail: " + e.getMessage());
            return base;
        }
    }

    private static void appendLines(StringBuilder sb, java.util.List<String> lines) {
        for (String line : lines) {
            sb.append(line).append('\n');
        }
    }

    /**
     * Save log bytes to Downloads. Returns the URI if saved via MediaStore, or a file URI if
     * saved directly. Returns null if all attempts failed.
     */
    public static Uri saveLogToDownloads(Context context, byte[] data) {
        return saveLogToDownloads(context, data, logFileName());
    }

    /**
     * Save log bytes to Downloads with a custom file name.
     */
    public static Uri saveLogToDownloads(Context context, byte[] data, String fileName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Uri uri = saveViaMediaStore(context, data, fileName);
            if (uri != null) return uri;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            File file = saveViaDirectFile(data, fileName);
            if (file != null) return Uri.fromFile(file);
        }

        LogBuffer.w("LogExportHelper", "All file saves failed");
        return null;
    }

    private static Uri saveViaMediaStore(Context context, byte[] data, String fileName) {
        try {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            cv.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
            cv.put(MediaStore.Downloads.IS_PENDING, 1);
            Uri uri = context.getContentResolver().insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) return null;
            try (OutputStream os = context.getContentResolver().openOutputStream(uri)) {
                if (os != null) {
                    os.write(data);
                    os.flush();
                }
            }
            cv.clear();
            cv.put(MediaStore.Downloads.IS_PENDING, 0);
            context.getContentResolver().update(uri, cv, null, null);
            LogBuffer.i("LogExportHelper", "Log saved via MediaStore: " + uri);
            return uri;
        } catch (Exception e) {
            LogBuffer.w("LogExportHelper", "MediaStore fail: " + e.getMessage());
            return null;
        }
    }

    private static File saveViaDirectFile(byte[] data, String fileName) {
        try {
            File dir = new File("/storage/emulated/0/Download/");
            if (!dir.exists()) dir = new File("/sdcard/Download/");
            if (!dir.exists()) {
                File pub = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                dir = pub != null ? pub : dir;
            }
            if (!dir.exists()) dir.mkdirs();
            if (dir.exists()) {
                File logFile = new File(dir, fileName);
                try (FileOutputStream fos = new FileOutputStream(logFile)) {
                    fos.write(data);
                }
                LogBuffer.i("LogExportHelper", "Log saved: " + logFile.getAbsolutePath());
                return logFile;
            }
        } catch (Exception e) {
            LogBuffer.w("LogExportHelper", "Direct write fail: " + e.getMessage());
        }
        return null;
    }

    private static String getDeviceInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("Brand=").append(android.os.Build.BRAND);
        sb.append(", Model=").append(android.os.Build.MODEL);
        sb.append(", Android=").append(android.os.Build.VERSION.RELEASE);
        sb.append(", SDK=").append(android.os.Build.VERSION.SDK_INT);
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"getprop", "ro.build.display.id"});
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = br.readLine();
            br.close();
            if (line != null && !line.isEmpty()) sb.append(", Build=").append(line);
        } catch (Exception ignored) {}
        return sb.toString();
    }
}
