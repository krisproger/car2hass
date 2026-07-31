package com.diplustohass;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * In-memory application log buffer with a 5 MB size cap and persistent file backup.
 *
 * <p>Old lines are dropped when the buffer would exceed {@link #MAX_BYTES}.
 * In addition, every line is appended to a rotating file so logs survive
 * application crashes and restarts. Two files are kept: the current log and
 * one backup (old). Call {@link #init(Context)} as early as possible (e.g. in
 * Application.onCreate, MainActivity.onCreate or TelemetryService.onCreate).</p>
 */
public class LogBuffer {
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    private static final String LOG_FILE = "diplus2hass_log.txt";
    private static final String OLD_LOG_FILE = "diplus2hass_log.old.txt";

    private static final ArrayList<String> buffer = new ArrayList<>();
    private static long currentBytes = 0;
    private static final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    private static Context appContext;
    private static volatile boolean fileEnabled = false;

    /**
     * Initialize file logging. Safe to call multiple times; only the first
     * non-null context is kept.
     */
    public static synchronized void init(Context context) {
        if (context == null) return;
        if (appContext == null) {
            appContext = context.getApplicationContext();
            fileEnabled = true;
            i("LogBuffer", "File logging initialized, dir=" + appContext.getFilesDir().getAbsolutePath());
        }
    }

    public static synchronized void i(String tag, String msg) {
        add("I", tag, msg);
    }

    public static synchronized void w(String tag, String msg) {
        add("W", tag, msg);
    }

    public static synchronized void e(String tag, String msg) {
        add("E", tag, msg);
    }

    public static synchronized void d(String tag, String msg) {
        add("D", tag, msg);
    }

    private static synchronized void add(String level, String tag, String msg) {
        String line = sdf.format(new Date()) + " " + level + "/" + tag + ": " + msg;
        byte[] lineBytes = line.getBytes();
        buffer.add(line);
        currentBytes += lineBytes.length;

        // Drop oldest lines until we are back under the size limit.
        while (currentBytes > MAX_BYTES && !buffer.isEmpty()) {
            String old = buffer.remove(0);
            currentBytes = Math.max(0, currentBytes - old.getBytes().length);
        }

        appendToFile(line);

        switch (level) {
            case "E": android.util.Log.e(tag, msg); break;
            case "W": android.util.Log.w(tag, msg); break;
            case "I": android.util.Log.i(tag, msg); break;
            default:  android.util.Log.d(tag, msg); break;
        }
    }

    private static void appendToFile(String line) {
        if (!fileEnabled || appContext == null) return;
        try {
            File logFile = new File(appContext.getFilesDir(), LOG_FILE);
            if (logFile.exists() && logFile.length() > MAX_FILE_BYTES) {
                rotateFile(logFile);
            }
            try (FileOutputStream fos = new FileOutputStream(logFile, true);
                 OutputStreamWriter osw = new OutputStreamWriter(fos, "UTF-8");
                 PrintWriter pw = new PrintWriter(osw)) {
                pw.println(line);
                pw.flush();
            }
        } catch (Exception e) {
            // Do not recurse into logging; silently disable file output if it fails.
            android.util.Log.w("LogBuffer", "File write failed: " + e.getMessage());
        }
    }

    private static void rotateFile(File current) {
        try {
            File old = new File(appContext.getFilesDir(), OLD_LOG_FILE);
            if (old.exists()) {
                old.delete();
            }
            current.renameTo(old);
        } catch (Exception e) {
            android.util.Log.w("LogBuffer", "Log rotation failed: " + e.getMessage());
        }
    }

    /**
     * Read persisted log files (current + old) into a single string.
     * Old log is prepended so chronological order is preserved.
     */
    public static synchronized String getFileLogText() {
        if (appContext == null) return "";
        StringBuilder sb = new StringBuilder();
        File old = new File(appContext.getFilesDir(), OLD_LOG_FILE);
        File current = new File(appContext.getFilesDir(), LOG_FILE);
        readFileInto(old, sb);
        readFileInto(current, sb);
        return sb.toString();
    }

    private static void readFileInto(File file, StringBuilder sb) {
        if (file == null || !file.exists()) return;
        try (FileInputStream fis = new FileInputStream(file);
             InputStreamReader isr = new InputStreamReader(fis, "UTF-8");
             BufferedReader br = new BufferedReader(isr)) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (IOException e) {
            sb.append("(read error: ").append(e.getMessage()).append(")\n");
        }
    }

    public static synchronized void flush() {
        // File is already flushed per-line; this method logs the current buffer size
        // and can be called at lifecycle boundaries to ensure writers reopen cleanly.
        android.util.Log.d("LogBuffer", "flush called, bytes=" + currentBytes);
    }

    public static synchronized List<String> getBuffer() {
        return new ArrayList<>(buffer);
    }

    public static synchronized String getText() {
        StringBuilder sb = new StringBuilder();
        for (String line : buffer) {
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    public static synchronized long getByteSize() {
        return currentBytes;
    }
}
