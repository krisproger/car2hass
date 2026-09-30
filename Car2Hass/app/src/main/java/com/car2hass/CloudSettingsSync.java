package com.car2hass;

import android.content.Context;

import com.car2hass.vehicle.DeviceAnon;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Joins the filesettings project and keeps settings.json / rules.json /
 * geofences.json in two-way sync with `car2hass/<anonId>/`, newest mtime wins.
 * Also provides the clone action (settings files only; identity is preserved).
 */
public final class CloudSettingsSync {

    private static final String TAG = "CloudSettingsSync";
    private static final String PROJECT = "filesettings";
    private static final String[] MANAGED = {
            CloudSettingsPayload.FILE_SETTINGS,
            CloudSettingsPayload.FILE_RULES,
            CloudSettingsPayload.FILE_GEOFENCES,
    };

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean inProgress = new AtomicBoolean(false);

    public interface Callback { void done(boolean ok, String message); }

    public interface FolderCallback { void done(JSONArray folders); }

    private CloudSettingsSync() {}

    /** Called when the user ticks "Хранить настройки в облаке". */
    public static void onEnabled(Context ctx) {
        final Context app = ctx.getApplicationContext();
        AppConfig.setCloudSettingsEnabled(app, true);
        executor.submit(() -> {
            boolean joined = CloudFilesClient.join(app, PROJECT);
            if (joined) AppConfig.setCloudSettingsJoined(app, PROJECT, true);
            else AppConfig.setCloudLastStatus(app, "join_failed");
        });
        syncAsync(app, "enabled");
    }

    public static void syncAsync(Context ctx, String reason) {
        final Context app = ctx.getApplicationContext();
        if (!AppConfig.isCloudSettingsEnabled(app)) return;
        if (AppConfig.getCloudAccessToken(app).isEmpty()) return;
        if (!inProgress.compareAndSet(false, true)) return;
        executor.submit(() -> {
            try {
                syncBlocking(app, null);
            } finally {
                inProgress.set(false);
            }
        });
    }

    public static void syncNow(Context ctx, Callback cb) {
        final Context app = ctx.getApplicationContext();
        if (AppConfig.getCloudAccessToken(app).isEmpty()) {
            post(cb, false, "login");
            return;
        }
        executor.submit(() -> {
            try {
                syncBlocking(app, cb);
            } catch (Exception e) {
                LogBuffer.e(TAG, "syncNow: " + e.getMessage());
                post(cb, false, "network");
            }
        });
    }

    public static void listFolders(Context ctx, FolderCallback cb) {
        final Context app = ctx.getApplicationContext();
        executor.submit(() -> cb.done(CloudFilesClient.folders(app)));
    }

    /** Copies the source device's settings files into this device's folder. */
    public static void cloneAsync(Context ctx, String sourceAnonId, boolean includeSecret, Callback cb) {
        final Context app = ctx.getApplicationContext();
        executor.submit(() -> {
            try {
                String anon = DeviceAnon.fromContext(app);
                String src = CloudFilesClient.deviceFolder(sourceAnonId);
                String dst = CloudFilesClient.deviceFolder(anon);
                for (String f : MANAGED) {
                    Object[] got = CloudFilesClient.get(app, src + "/" + f);
                    if (got == null) continue;
                    String content = (String) got[0];
                    if (CloudSettingsPayload.FILE_SETTINGS.equals(f)) {
                        content = CloudSettingsJson.stripIdentity(content);
                        if (!includeSecret) content = CloudSettingsJson.stripSecret(content);
                    }
                    long updated = CloudFilesClient.put(app, dst + "/" + f, content);
                    if (updated < 0) {
                        post(cb, false, "upload");
                        return;
                    }
                    AppConfig.setCloudFileMtimeMs(app, f, updated * 1000L);
                    applyFile(app, f, content);
                }
                post(cb, true, "ok");
            } catch (Exception e) {
                LogBuffer.e(TAG, "clone: " + e.getMessage());
                post(cb, false, "network");
            }
        });
    }

    private static void syncBlocking(Context app, Callback cb) {
        if (!AppConfig.isCloudSettingsJoined(app, PROJECT)) {
            if (!CloudFilesClient.join(app, PROJECT)) {
                post(cb, false, "join");
                return;
            }
            AppConfig.setCloudSettingsJoined(app, PROJECT, true);
        }
        String anon = DeviceAnon.fromContext(app);
        JSONArray list = CloudFilesClient.list(app);
        if (list == null) {
            AppConfig.setCloudLastStatus(app, "list_failed");
            post(cb, false, "list");
            return;
        }
        Map<String, Long> server = new HashMap<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject o = list.optJSONObject(i);
            if (o != null) server.put(o.optString("name", ""), o.optLong("updated_at", 0L));
        }
        for (String file : MANAGED) {
            syncOne(app, anon, file, server);
        }
        ensureMeta(app, anon, server);
        AppConfig.setCloudLastSyncMs(app, System.currentTimeMillis());
        AppConfig.setCloudLastStatus(app, "ok");
        post(cb, true, "ok");
    }

    private static void syncOne(Context app, String anon, String file, Map<String, Long> server) {
        String path = CloudFilesClient.filePath(anon, file);
        String local = localContent(app, file);
        boolean hasLocal = local != null && !local.isEmpty();
        long localMs = AppConfig.getCloudFileMtimeMs(app, file);
        Long serverSec = server.get(path);
        boolean hasRemote = serverSec != null;
        long serverSeconds = serverSec == null ? 0L : serverSec;
        CloudSyncDecision.Action action =
                CloudSyncDecision.decide(hasLocal, localMs, hasRemote, serverSeconds);
        if (action == CloudSyncDecision.Action.UPLOAD) {
            long updated = CloudFilesClient.put(app, path, local);
            if (updated >= 0) AppConfig.setCloudFileMtimeMs(app, file, updated * 1000L);
        } else if (action == CloudSyncDecision.Action.DOWNLOAD) {
            Object[] got = CloudFilesClient.get(app, path);
            if (got != null) {
                applyFile(app, file, (String) got[0]);
                AppConfig.setCloudFileMtimeMs(app, file, ((Long) got[1]) * 1000L);
            }
        }
    }

    private static void ensureMeta(Context app, String anon, Map<String, Long> server) {
        String label = AppConfig.getCloudCarName(app);
        if (label == null || label.isEmpty()) return;
        String path = CloudFilesClient.filePath(anon, "_meta.json");
        boolean hasRemote = server.containsKey(path);
        if (hasRemote && label.equals(AppConfig.getCloudMetaLabel(app))) return;
        long updated = CloudFilesClient.put(app, path, CloudSettingsJson.metaJson(label,
                System.currentTimeMillis()));
        if (updated >= 0) AppConfig.setCloudMetaLabel(app, label);
    }

    private static String localContent(Context app, String file) {
        switch (file) {
            case CloudSettingsPayload.FILE_SETTINGS:
                return CloudSettingsPayload.settingsJson(app, AppConfig.isCloudStoreSecret(app));
            case CloudSettingsPayload.FILE_RULES:
                return CloudSettingsPayload.rulesJson(app);
            case CloudSettingsPayload.FILE_GEOFENCES:
                return CloudSettingsPayload.geofencesJson(app);
            default:
                return "";
        }
    }

    private static void applyFile(Context app, String file, String content) {
        switch (file) {
            case CloudSettingsPayload.FILE_SETTINGS:
                CloudSettingsPayload.applySettings(app, content);
                break;
            case CloudSettingsPayload.FILE_RULES:
                CloudSettingsPayload.applyRules(app, content);
                break;
            case CloudSettingsPayload.FILE_GEOFENCES:
                CloudSettingsPayload.applyGeofences(app, content);
                break;
            default:
                break;
        }
    }

    private static void post(Callback cb, boolean ok, String message) {
        if (cb != null) cb.done(ok, message);
    }
}
