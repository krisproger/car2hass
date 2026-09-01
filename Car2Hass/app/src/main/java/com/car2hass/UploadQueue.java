package com.car2hass;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistent queue for uploads that could not be sent (no network / server
 * down). Entries are retried (3 attempts, pause, 3 more) and only dropped by
 * the user or on success.
 */
public final class UploadQueue {

    public static final String KIND_LOG = "log";
    public static final String KIND_PROBE = "probe";

    private static final int MAX_ATTEMPTS_BEFORE_ASK = 3;
    private static final int MAX_ATTEMPTS_TOTAL = 6;

    public static final class Entry {
        public final String id;
        public final String kind;
        public final String appVersion;
        public final String anonId;
        public final String message;      // user note for logs, "" for probe
        public final String path;         // log file path or probe_report path
        public final long createdAt;
        public int tries;
        public boolean awaitingUser;

        Entry(String id, String kind, String appVersion, String anonId,
              String message, String path, long createdAt, int tries, boolean awaitingUser) {
            this.id = id;
            this.kind = kind;
            this.appVersion = appVersion;
            this.anonId = anonId;
            this.message = message;
            this.path = path;
            this.createdAt = createdAt;
            this.tries = tries;
            this.awaitingUser = awaitingUser;
        }

        JSONObject toJson() throws Exception {
            return new JSONObject()
                    .put("id", id)
                    .put("kind", kind)
                    .put("app_version", appVersion)
                    .put("anon_id", anonId)
                    .put("message", message)
                    .put("path", path)
                    .put("created_at", createdAt)
                    .put("tries", tries)
                    .put("awaiting_user", awaitingUser);
        }

        static Entry fromJson(JSONObject o) {
            return new Entry(
                    o.optString("id"),
                    o.optString("kind"),
                    o.optString("app_version"),
                    o.optString("anon_id"),
                    o.optString("message"),
                    o.optString("path"),
                    o.optLong("created_at", System.currentTimeMillis()),
                    o.optInt("tries", 0),
                    o.optBoolean("awaiting_user", false));
        }
    }

    private static final String FILE = "upload_queue.json";
    private static final String LOCK = "UploadQueue";

    private UploadQueue() {}

    private static File file(Context ctx) {
        return new File(ctx.getFilesDir(), FILE);
    }

    public static List<Entry> load(Context ctx) {
        synchronized (LOCK) {
            List<Entry> out = new ArrayList<>();
            File f = file(ctx);
            if (!f.exists()) return out;
            try (FileInputStream fis = new FileInputStream(f)) {
                StringBuilder sb = new StringBuilder();
                byte[] buf = new byte[4096];
                int n;
                while ((n = fis.read(buf)) > 0) sb.append(new String(buf, 0, n, "UTF-8"));
                JSONArray arr = new JSONArray(sb.toString());
                for (int i = 0; i < arr.length(); i++) {
                    Entry e = Entry.fromJson(arr.getJSONObject(i));
                    if (e.path != null && !e.path.isEmpty()) out.add(e);
                }
            } catch (Exception e) {
                LogBuffer.e("UploadQueue", "load: " + e.getMessage());
            }
            return out;
        }
    }

    private static void save(Context ctx, List<Entry> entries) {
        synchronized (LOCK) {
            try (FileOutputStream fos = new FileOutputStream(file(ctx))) {
                JSONArray arr = new JSONArray();
                for (Entry e : entries) arr.put(e.toJson());
                fos.write(arr.toString().getBytes("UTF-8"));
            } catch (Exception e) {
                LogBuffer.e("UploadQueue", "save: " + e.getMessage());
            }
        }
    }

    public static void enqueue(Context ctx, String kind, String appVersion,
                               String anonId, String message, String path) {
        List<Entry> all = load(ctx);
        all.add(new Entry("u" + System.currentTimeMillis() + "-" + all.size(),
                kind, appVersion, anonId, message, path,
                System.currentTimeMillis(), 0, false));
        save(ctx, all);
        LogBuffer.i("UploadQueue", "enqueued " + kind + " (" + all.size() + " pending)");
    }

    public static void remove(Context ctx, String id) {
        List<Entry> all = load(ctx);
        List<Entry> kept = new ArrayList<>();
        for (Entry e : all) if (!e.id.equals(id)) kept.add(e);
        save(ctx, kept);
    }

    public static void update(Context ctx, Entry e) {
        List<Entry> all = load(ctx);
        List<Entry> kept = new ArrayList<>();
        for (Entry x : all) {
            kept.add(x.id.equals(e.id) ? e : x);
        }
        save(ctx, kept);
    }

    /** Any entry needing the user's decision? */
    public static boolean hasAwaitingUser(Context ctx) {
        for (Entry e : load(ctx)) if (e.awaitingUser) return true;
        return false;
    }
}