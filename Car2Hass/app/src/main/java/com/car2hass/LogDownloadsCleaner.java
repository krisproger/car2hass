package com.car2hass;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Deletes exported log files from Downloads using the pure
 * {@link DownloadsCleanup} selection rules. MediaStore on Android 10+,
 * direct file access below.
 */
final class LogDownloadsCleaner {

    private LogDownloadsCleaner() {}

    static void run(Context ctx, boolean firstRun) {
        try {
            List<DownloadsCleanup.FileRef> files = listFiles(ctx);
            List<String> toDelete = DownloadsCleanup.selectForDeletion(files, firstRun);
            for (String name : toDelete) {
                deleteByName(ctx, name);
            }
        } catch (Exception e) {
            LogBuffer.w("LogDownloadsCleaner", "cleanup: " + e.getMessage());
        }
    }

    private static List<DownloadsCleanup.FileRef> listFiles(Context ctx) {
        List<DownloadsCleanup.FileRef> out = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            String[] proj = {MediaStore.Downloads.DISPLAY_NAME, MediaStore.Downloads.DATE_MODIFIED};
            try (Cursor c = ctx.getContentResolver().query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, proj, null, null, null)) {
                if (c != null) {
                    int nIdx = c.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME);
                    int tIdx = c.getColumnIndexOrThrow(MediaStore.Downloads.DATE_MODIFIED);
                    while (c.moveToNext()) {
                        out.add(new DownloadsCleanup.FileRef(c.getString(nIdx), c.getLong(tIdx) * 1000L));
                    }
                }
            } catch (Exception ignored) {}
        } else {
            File dir = downloadsDir();
            File[] files = dir == null ? null : dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile()) out.add(new DownloadsCleanup.FileRef(f.getName(), f.lastModified()));
                }
            }
        }
        return out;
    }

    private static void deleteByName(Context ctx, String name) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                String[] proj = {MediaStore.Downloads._ID};
                try (Cursor c = ctx.getContentResolver().query(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, proj,
                        MediaStore.Downloads.DISPLAY_NAME + "=?",
                        new String[]{name}, null)) {
                    if (c != null && c.moveToFirst()) {
                        Uri uri = Uri.withAppendedPath(
                                MediaStore.Downloads.EXTERNAL_CONTENT_URI, c.getString(0));
                        ctx.getContentResolver().delete(uri, null, null);
                    }
                }
            } else {
                File dir = downloadsDir();
                if (dir != null) {
                    File f = new File(dir, name);
                    if (f.isFile()) f.delete();
                }
            }
        } catch (Exception ignored) {}
    }

    private static File downloadsDir() {
        File dir = new File("/storage/emulated/0/Download/");
        if (!dir.exists()) dir = new File("/sdcard/Download/");
        if (!dir.exists()) {
            File pub = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            dir = pub != null ? pub : dir;
        }
        return dir;
    }
}
