package com.car2hass;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/** Android side of the update flow: check, download (DownloadManager), install. */
public final class UpdateDownloader {

    public static final long CHECK_INTERVAL_MS = 10 * 60 * 1000L; // 10 min

    private UpdateDownloader() {}

    /** True when a check ran recently enough to reuse the result. */
    public static boolean checkedRecently(Context ctx) {
        return System.currentTimeMillis() - AppConfig.getUpdateLastCheckMs(ctx) < CHECK_INTERVAL_MS;
    }

    /**
     * Fetches the endpoint and returns update info when the site version is
     * newer than the installed one; null when up to date / nothing published.
     */
    public static UpdateChecker.UpdateInfo check(Context ctx) throws Exception {
        AppConfig.setUpdateLastCheckMs(ctx, System.currentTimeMillis());
        String body = httpGet(ctx, UpdateChecker.VERSION_URL);
        UpdateChecker.UpdateInfo info = UpdateChecker.parseResponse(body);
        if (info == null) return null;
        if (!UpdateChecker.isNewer(info.version, AppInfo.getVersionName(ctx))) return null;
        return info;
    }

    /** Fetches the latest published release info, regardless of installed version. */
    public static UpdateChecker.UpdateInfo fetchLatest(Context ctx) throws Exception {
        String body = httpGet(ctx, UpdateChecker.VERSION_URL);
        return UpdateChecker.parseResponse(body);
    }

    private static String httpGet(Context ctx, String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(10_000);
        conn.setRequestProperty("User-Agent", "Car2Hass-UpdateCheck");
        conn.setRequestProperty("X-Car2Hass-Id", AppConfig.getAppInstanceId(ctx));
        int code = conn.getResponseCode();
        if (code != 200) throw new Exception("HTTP " + code);
        try (InputStream is = conn.getInputStream()) {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toString("UTF-8");
        } finally {
            conn.disconnect();
        }
    }

    /** Enqueues the APK download and returns the DownloadManager id. */
    public static long enqueueDownload(Context ctx, UpdateChecker.UpdateInfo info) {
        String fileName = "Car2Hass-" + info.version + ".apk";
        File dest = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                fileName);
        if (dest.exists()) dest.delete();

        DownloadManager dm = (DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Request req = new DownloadManager.Request(Uri.parse(info.apkUrl))
                .setTitle("Car2Hass " + info.version)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
        return dm.enqueue(req);
    }

    /**
     * Resolves the actually downloaded APK. Some head units (Voyah) save the
     * file as "*.bin" or without an extension, so the hardcoded ".apk" name
     * must not be trusted. Prefers the DownloadManager's reported path, then
     * scans Downloads for "Car2Hass-<version>*" with any extension.
     */
    public static File findDownloadedFile(Context ctx, long id, String version) {
        if (ctx != null && id >= 0) {
            try {
                DownloadManager dm = (DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);
                android.database.Cursor c = dm.query(new DownloadManager.Query().setFilterById(id));
                if (c != null) {
                    try {
                        if (c.moveToFirst() && c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                                == DownloadManager.STATUS_SUCCESSFUL) {
                            String localFile = c.getString(
                                    c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_FILENAME));
                            if (localFile != null) {
                                File f = new File(localFile);
                                if (f.exists() && f.length() > 0) return f;
                            }
                            String localUri = c.getString(
                                    c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI));
                            if (localUri != null) {
                                try {
                                    File f = new File(Uri.parse(localUri).getPath());
                                    if (f.exists() && f.length() > 0) return f;
                                } catch (Exception ignored) {
                                }
                            }
                        }
                    } finally {
                        c.close();
                    }
                }
            } catch (Exception ignored) {
            }
        }
        File best = null;
        // Public Downloads may not hold the file at all (Voyah head units save
        // elsewhere); also scan the app-private dirs used by download().
        File pub = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        best = better(best, bestMatch(pub, version));
        if (ctx != null) {
            File priv = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (priv != null) best = better(best, bestMatch(priv, version));
            best = better(best, bestMatch(ctx.getCacheDir(), version));
        }
        return best;
    }

    /** Largest candidate APK: name contains the version or the app prefix, any extension. */
    private static File bestMatch(File dir, String version) {
        File[] files = dir == null ? null : dir.listFiles((d, name) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            return lower.contains("car2hass") || lower.startsWith("download")
                    || (version != null && lower.contains(version.toLowerCase(Locale.ROOT)));
        });
        File best = null;
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && f.length() > 0 && (best == null || f.length() > best.length())) {
                    best = f;
                }
            }
        }
        return best;
    }

    private static File better(File a, File b) {
        if (a == null) return b;
        if (b == null) return a;
        return b.length() > a.length() ? b : a;
    }

    /** Fires the package installer for an existing file; false on failure. */
    public static boolean installFile(Context ctx, File file) {
        if (file == null || !file.exists()) return false;
        try {
            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    ctx, ctx.getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            ctx.startActivity(intent);
            return true;
        } catch (Exception e) {
            LogBuffer.e("UpdateDownloader", "installFile: " + e.getMessage());
            return false;
        }
    }

    /** Progress callback: done bytes / total bytes (total may be -1 when unknown). */
    public interface DownloadProgress {
        void onProgress(long done, long total);
    }

    /**
     * Downloads the APK directly into app-private storage with an explicit
     * ".apk" name, bypassing DownloadManager. DownloadManager on some head
     * units (Voyah PATEO, Android 9) renames the file to "*.bin" or saves it
     * somewhere the public-Downloads scan cannot see, so the app streams the
     * file itself. Returns the ready-to-install file.
     */
    public static File download(Context ctx, UpdateChecker.UpdateInfo info,
                                DownloadProgress progress) throws IOException {
        File dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) dir = ctx.getCacheDir();
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        File target = new File(dir, "Car2Hass-" + info.version + ".apk");
        if (target.exists() && target.length() > 0) return target;
        File tmp = new File(dir, target.getName() + ".part");
        HttpURLConnection conn = (HttpURLConnection) new URL(info.apkUrl).openConnection();
        try {
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(15_000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "Car2Hass-Updater");
            conn.setRequestProperty("X-Car2Hass-Id", AppConfig.getAppInstanceId(ctx));
            int code = conn.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
            long total = conn.getContentLengthLong();
            try (InputStream in = conn.getInputStream();
                 java.io.FileOutputStream out = new java.io.FileOutputStream(tmp)) {
                byte[] buf = new byte[8192];
                long done = 0;
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    done += n;
                    if (progress != null) progress.onProgress(done, total);
                }
                out.getFD().sync();
            }
            if (!tmp.renameTo(target)) throw new IOException("rename to " + target + " failed");
            return target;
        } finally {
            conn.disconnect();
        }
    }

    /** Polls the download status; "done", "failed" or a progress percentage. */
    public static String downloadStatus(Context ctx, long id) {
        DownloadManager dm = (DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Query q = new DownloadManager.Query().setFilterById(id);
        android.database.Cursor c = dm.query(q);
        if (c == null) return "failed";
        try {
            if (!c.moveToFirst()) return "failed";
            int status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            if (status == DownloadManager.STATUS_SUCCESSFUL) return "done";
            if (status == DownloadManager.STATUS_FAILED) return "failed";
            long total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
            long got = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
            if (total > 0) return (got * 100 / total) + "%";
            return "...";
        } finally {
            c.close();
        }
    }

    /** UI language of the app ("ru"/"en") for choosing the notes text. */
    public static String uiLanguage(Context ctx) {
        Locale loc = ctx.getResources().getConfiguration().getLocales().get(0);
        return loc.getLanguage();
    }
}
