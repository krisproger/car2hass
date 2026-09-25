package com.car2hass;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Pure selection logic for cleaning up exported log files in Downloads.
 *
 * <ul>
 *   <li>First run: every log-like file is deleted.</li>
 *   <li>Subsequent runs: only our files ({@code car2hass}-prefixed {@code .txt})
 *       are deleted, keeping at most {@link #MAX_KEEP} newest files.</li>
 * </ul>
 */
public final class DownloadsCleanup {

    public static final int MAX_KEEP = 5;

    public static final class FileRef {
        public final String name;
        public final long lastModified;

        public FileRef(String name, long lastModified) {
            this.name = name;
            this.lastModified = lastModified;
        }
    }

    private DownloadsCleanup() {}

    /** Our exported files: {@code car2hass}-prefixed {@code .txt}. */
    public static boolean isOurFile(String name) {
        if (name == null) return false;
        String l = name.toLowerCase(Locale.ROOT);
        return l.startsWith("car2hass") && l.endsWith(".txt");
    }

    /** Log-like files for the aggressive first-run sweep (our files + names containing "log"). */
    public static boolean isLogLike(String name) {
        if (name == null) return false;
        String l = name.toLowerCase(Locale.ROOT);
        return l.endsWith(".txt") && (isOurFile(name) || l.contains("log") || l.contains("crash"));
    }

    /** Names to delete given the current file set and whether this is the first run. */
    public static List<String> selectForDeletion(List<FileRef> files, boolean firstRun) {
        List<String> out = new ArrayList<>();
        if (files == null) return out;

        if (firstRun) {
            for (FileRef f : files) {
                if (isLogLike(f.name)) out.add(f.name);
            }
            return out;
        }

        List<FileRef> ours = new ArrayList<>();
        for (FileRef f : files) {
            if (isOurFile(f.name)) ours.add(f);
        }
        if (ours.size() <= MAX_KEEP) return out;
        ours.sort(Comparator.comparingLong((FileRef f) -> f.lastModified).reversed());
        for (int i = MAX_KEEP; i < ours.size(); i++) {
            out.add(ours.get(i).name);
        }
        return out;
    }
}
