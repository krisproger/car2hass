package com.car2hass;

import java.util.ArrayList;
import java.util.List;

/** Plain-Java tests for Downloads cleanup file selection. */
public class DownloadsCleanupTest {

    public static void main(String[] args) {
        testOurFileDetection();
        testFirstRunDeletesLogLike();
        testKeepFiveNewest();

        System.out.println("All DownloadsCleanup tests passed.");
    }

    private static void testOurFileDetection() {
        if (!DownloadsCleanup.isOurFile("Car2Hass_log_2024-01-01_12-00-00.txt")) {
            throw new AssertionError("Car2Hass log file must be recognized");
        }
        if (!DownloadsCleanup.isOurFile("car2hass_crash_2024.txt")) {
            throw new AssertionError("lowercase prefix must be recognized");
        }
        if (DownloadsCleanup.isOurFile("report.txt")) {
            throw new AssertionError("unrelated txt must not be ours");
        }
        if (DownloadsCleanup.isOurFile("Car2Hass_log_2024.pdf")) {
            throw new AssertionError("non-txt must not be ours");
        }
    }

    private static void testFirstRunDeletesLogLike() {
        List<DownloadsCleanup.FileRef> files = new ArrayList<>();
        files.add(new DownloadsCleanup.FileRef("Car2Hass_log_a.txt", 1));
        files.add(new DownloadsCleanup.FileRef("some_app_log.txt", 2));
        files.add(new DownloadsCleanup.FileRef("notes.txt", 3));
        files.add(new DownloadsCleanup.FileRef("photo.jpg", 4));
        List<String> del = DownloadsCleanup.selectForDeletion(files, true);
        if (del.size() != 2) throw new AssertionError("first run deletes 2 log-like files, got " + del);
        if (!del.contains("Car2Hass_log_a.txt") || !del.contains("some_app_log.txt")) {
            throw new AssertionError("first run must delete our + log-named files: " + del);
        }
    }

    private static void testKeepFiveNewest() {
        List<DownloadsCleanup.FileRef> files = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            files.add(new DownloadsCleanup.FileRef("Car2Hass_log_" + i + ".txt", 1000L + i));
        }
        List<String> del = DownloadsCleanup.selectForDeletion(files, false);
        if (del.size() != 3) throw new AssertionError("must delete the 3 oldest of 8, got " + del);
        if (!del.contains("Car2Hass_log_0.txt")
                || !del.contains("Car2Hass_log_1.txt")
                || !del.contains("Car2Hass_log_2.txt")) {
            throw new AssertionError("oldest first: " + del);
        }
    }
}
