package com.diplustohass;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Unified config import/export helper.
 * Supports direct file access (legacy/Android 9), MediaStore (Android 10+)
 * and app-private fallback.
 */
public class ConfigStorageHelper {

    private static final String PREFIX = "config_diplus2hass_";
    private static final String SUFFIX = ".json";

    /** File prefix for automation rules exports. */
    public static final String RULES_PREFIX = "rules_";

    public static class Folder {
        public final String label;
        public final File path;

        public Folder(String label, File path) {
            this.label = label;
            this.path = path;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public static class ConfigRef {
        public final String name;
        public final File file;
        public final Uri uri;

        public ConfigRef(String name, File file, Uri uri) {
            this.name = name;
            this.file = file;
            this.uri = uri;
        }

        public boolean isUri() {
            return uri != null;
        }
    }

    public static List<Folder> getFolders(Context context) {
        List<Folder> list = new ArrayList<>();
        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (downloads != null) {
            list.add(new Folder(context.getString(R.string.folder_download), downloads));
        }
        // BYD DiLink often exposes /sdcard/Download directly.
        File sdcardDownload = new File("/sdcard/Download");
        if (sdcardDownload.exists() && sdcardDownload.isDirectory()
                && (downloads == null || !downloads.getAbsolutePath().equals(sdcardDownload.getAbsolutePath()))) {
            list.add(new Folder(context.getString(R.string.folder_sdcard_download), sdcardDownload));
        }
        list.add(new Folder(context.getString(R.string.folder_app_private), context.getFilesDir()));
        return list;
    }

    public static Folder getDefaultFolder(Context context) {
        List<Folder> folders = getFolders(context);
        return folders.isEmpty()
                ? new Folder(context.getString(R.string.folder_app_private), context.getFilesDir())
                : folders.get(0);
    }

    public static String newFileName() {
        return newFileName(PREFIX);
    }

    public static String newFileName(String prefix) {
        return prefix + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + SUFFIX;
    }

    private static boolean isDownloadFolder(Folder folder) {
        if (folder == null || folder.path == null) return false;
        String path = folder.path.getAbsolutePath();
        File publicDownload = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        return path.equalsIgnoreCase(publicDownload != null ? publicDownload.getAbsolutePath() : "")
                || path.toLowerCase(Locale.ROOT).endsWith("/download");
    }

    /**
     * Write config to the selected folder.
     *
     * @return ConfigRef pointing to the written file. If written via MediaStore,
     *         the returned ConfigRef contains a content URI and no File.
     * @throws Exception if all write paths failed.
     */
    public static ConfigRef writeConfig(Context context, Folder folder, JSONObject config) throws Exception {
        return writeConfig(context, folder, config, newFileName());
    }

    public static ConfigRef writeConfig(Context context, Folder folder, JSONObject config, String fileName) throws Exception {
        byte[] bytes = config.toString(2).getBytes("UTF-8");

        // On Android 10+ scoped storage makes direct writes to Download fail
        // with EACCES — go straight to MediaStore and skip the noisy failure.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isDownloadFolder(folder)) {
            Uri uri = writeToMediaStore(context, fileName, bytes);
            if (uri != null) {
                LogBuffer.i("ConfigStorage", "Exported config via MediaStore: " + uri);
                return new ConfigRef(fileName, null, uri);
            }
        }

        // 1. Try direct File write first.
        File target = new File(folder.path, fileName);
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try {
            try (FileOutputStream fos = new FileOutputStream(target)) {
                fos.write(bytes);
            }
            LogBuffer.i("ConfigStorage", "Exported config to " + target.getAbsolutePath());
            return new ConfigRef(fileName, target, null);
        } catch (Exception directEx) {
            LogBuffer.d("ConfigStorage", "Direct write failed: " + directEx.getMessage());
        }

        // Fallback to app-private files (always works without storage permissions).
        File privateTarget = new File(context.getFilesDir(), fileName);
        try (FileOutputStream fos = new FileOutputStream(privateTarget)) {
            fos.write(bytes);
        }
        LogBuffer.i("ConfigStorage", "Exported config to app-private " + privateTarget.getAbsolutePath());
        return new ConfigRef(fileName, privateTarget, null);
    }

    private static Uri writeToMediaStore(Context context, String fileName, byte[] bytes) {
        try {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            cv.put(MediaStore.Downloads.MIME_TYPE, "application/json");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cv.put(MediaStore.Downloads.IS_PENDING, 1);
            }
            Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) return null;
            try (OutputStream os = context.getContentResolver().openOutputStream(uri)) {
                if (os != null) os.write(bytes);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cv.clear();
                cv.put(MediaStore.Downloads.IS_PENDING, 0);
                context.getContentResolver().update(uri, cv, null, null);
            }
            return uri;
        } catch (Exception e) {
            LogBuffer.e("ConfigStorage", "MediaStore write failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * List available config files in the selected folder, including files visible
     * through MediaStore on Android 10+ when direct access is not allowed.
     */
    public static List<ConfigRef> listConfigRefs(Context context, Folder folder) {
        return listConfigRefs(context, folder, PREFIX);
    }

    /**
     * List available config files matching a file prefix in the selected
     * folder, including files visible through MediaStore on Android 10+.
     *
     * @param prefix file name prefix, e.g. "config_diplus2hass_" or "rules_"
     */
    public static List<ConfigRef> listConfigRefs(Context context, Folder folder, String prefix) {
        List<ConfigRef> refs = new ArrayList<>();

        // Direct file access.
        File[] files = folder.path.listFiles((dir, name) ->
                name.startsWith(prefix) && name.endsWith(SUFFIX));
        if (files != null) {
            // Sort newest first by filename (timestamp is in the name).
            List<File> sorted = new ArrayList<>();
            Collections.addAll(sorted, files);
            Collections.sort(sorted, (a, b) -> b.getName().compareTo(a.getName()));
            for (File f : sorted) {
                refs.add(new ConfigRef(f.getName(), f, null));
            }
        }

        // On Q+, also query MediaStore for Download files matching the prefix.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isDownloadFolder(folder)) {
            queryMediaStoreDownloads(context, refs, prefix);
        }

        return refs;
    }

    private static void queryMediaStoreDownloads(Context context, List<ConfigRef> refs) {
        queryMediaStoreDownloads(context, refs, PREFIX);
    }

    private static void queryMediaStoreDownloads(Context context, List<ConfigRef> refs, String prefix) {
        ContentResolver resolver = context.getContentResolver();
        Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
        String[] projection = {
                MediaStore.Downloads._ID,
                MediaStore.Downloads.DISPLAY_NAME,
        };
        String selection = MediaStore.Downloads.DISPLAY_NAME + " LIKE ?";
        String[] selectionArgs = { prefix + "%" + SUFFIX };
        String sortOrder = MediaStore.Downloads.DATE_ADDED + " DESC";

        try (Cursor cursor = resolver.query(collection, projection, selection, selectionArgs, sortOrder)) {
            if (cursor == null) return;
            int idCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID);
            int nameCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME);
            while (cursor.moveToNext()) {
                long id = cursor.getLong(idCol);
                String name = cursor.getString(nameCol);
                Uri uri = Uri.withAppendedPath(collection, String.valueOf(id));
                // Avoid duplicates if the file is also accessible directly.
                boolean duplicate = false;
                for (ConfigRef ref : refs) {
                    if (ref != null && name.equals(ref.name)) {
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate) {
                    refs.add(new ConfigRef(name, null, uri));
                }
            }
        } catch (Exception e) {
            LogBuffer.e("ConfigStorage", "MediaStore query failed: " + e.getMessage());
        }
    }

    public static String readConfigRef(Context context, ConfigRef ref) throws Exception {
        if (ref == null) throw new IllegalArgumentException("null config ref");
        if (ref.isUri()) {
            return readUri(context, ref.uri);
        }
        return readFile(ref.file);
    }

    private static String readUri(Context context, Uri uri) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = context.getContentResolver().openInputStream(uri);
             BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString().trim();
    }

    public static String readFile(File file) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = new FileInputStream(file);
             BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString().trim();
    }

    public static String fileNamePattern() {
        return PREFIX + "*" + SUFFIX;
    }
}
