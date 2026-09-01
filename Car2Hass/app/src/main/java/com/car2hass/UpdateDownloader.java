package com.car2hass;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;

import java.io.File;
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
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File[] files = dir.listFiles((d, name) -> name.startsWith("Car2Hass-" + version));
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

    /** True when an APK for the version already landed in public Downloads. */
    public static boolean apkExists(String version) {
        return findDownloadedFile(null, -1, version) != null;
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
