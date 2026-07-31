package com.diplustohass;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Persistent command-test log.
 *
 * <p>Each command sent from the Tester tab and its result are appended to a file
 * named with the current date (e.g. {@code DiPlusCmd_2026-07-13.log}). The file is
 * created on demand and appended across app restarts, so the user does not need a
 * separate save action.</p>
 */
public class CommandLog {

    private static final String LOG_DIR = "cmd_log";
    private static final String FILE_PREFIX = "DiPlusCmd_";
    private static final String FILE_SUFFIX = ".log";
    private static final SimpleDateFormat FILE_DATE = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private static final SimpleDateFormat LINE_TIME = new SimpleDateFormat("HH:mm:ss", Locale.US);
    private static final long MAX_COMMAND_LOG_BYTES = 2L * 1024 * 1024; // 2 MB

    private static void rotateIfNeeded(File file) {
        if (file.length() > MAX_COMMAND_LOG_BYTES) {
            File old = new File(file.getParent(), file.getName() + ".old");
            if (old.exists()) old.delete();
            file.renameTo(old);
        }
    }

    private static File getLogFile(Context context) {
        File dir = new File(context.getFilesDir(), LOG_DIR);
        if (!dir.exists()) dir.mkdirs();
        String name = FILE_PREFIX + FILE_DATE.format(new Date()) + FILE_SUFFIX;
        return new File(dir, name);
    }

    /** Append one timestamped line to today's command log. */
    public static synchronized void append(Context context, String line) {
        File file = getLogFile(context);
        rotateIfNeeded(file);
        try (FileWriter fw = new FileWriter(file, true);
             PrintWriter pw = new PrintWriter(fw)) {
            String time = LINE_TIME.format(new Date());
            pw.println(time + " " + line);
        } catch (IOException e) {
            LogBuffer.e("CommandLog", "Cannot write command log: " + e.getMessage());
        }
    }

    /** Read the last {@code maxLines} lines from today's command log. */
    public static synchronized String readRecent(Context context, int maxLines) {
        File file = getLogFile(context);
        if (!file.exists()) return "(no command history yet)";
        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                lines.add(line);
                if (lines.size() > maxLines) {
                    lines.remove(0);
                }
            }
        } catch (IOException e) {
            return "(cannot read command log: " + e.getMessage() + ")";
        }
        StringBuilder sb = new StringBuilder();
        for (String l : lines) {
            sb.append(l).append('\n');
        }
        return sb.length() > 0 ? sb.toString() : "(empty command history)";
    }

    public static synchronized List<String> getAll(Context context) {
        File file = getLogFile(context);
        List<String> lines = new ArrayList<>();
        if (!file.exists()) return lines;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) lines.add(line);
        } catch (IOException e) {
            LogBuffer.e("CommandLog", "Cannot read command log: " + e.getMessage());
        }
        return lines;
    }

    public static synchronized void clear(Context context) {
        File file = getLogFile(context);
        if (file.exists()) file.delete();
    }
}
