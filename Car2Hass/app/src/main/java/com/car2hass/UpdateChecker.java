package com.car2hass;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;

import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * App update check against the project site (see BYDMate UpdateChecker for the
 * reference workflow). Endpoint returns the latest version, an apk_url and
 * localized "what's new" notes; download goes through DownloadManager, install
 * through FileProvider + ACTION_VIEW.
 */
public final class UpdateChecker {

    public static final String VERSION_URL =
            "https://mytechnic.ru/cartelemetry/api/version/index.php";

    /** Endpoint URL for a channel; null/"stable" → the plain release endpoint. */
    public static String versionUrl(String channel) {
        if (channel == null || channel.isEmpty() || "stable".equals(channel)) return VERSION_URL;
        return VERSION_URL + "?channel=" + channel;
    }
    /** Latest release info from the site. */
    public static final class UpdateInfo {
        public final String version;
        public final String apkUrl;
        public final String sha256;
        public final long size;
        public final String notesRu;
        public final String notesEn;

        UpdateInfo(String version, String apkUrl, String sha256, long size,
                   String notesRu, String notesEn) {
            this.version = version;
            this.apkUrl = apkUrl;
            this.sha256 = sha256;
            this.size = size;
            this.notesRu = notesRu;
            this.notesEn = notesEn;
        }

        /** Notes in the requested language (ru/en), falls back to the other one. */
        public String notes(String language) {
            boolean ru = "ru".equals(language);
            String own = ru ? notesRu : notesEn;
            return own != null && !own.isEmpty() ? own : (ru ? notesEn : notesRu);
        }
    }

    private UpdateChecker() {}

    /** Parses the endpoint payload; null version means "no release published". */
    public static UpdateInfo parseResponse(String json) {
        try {
            JSONObject o = new JSONObject(json);
            if (!o.optBoolean("ok", false)) return null;
            String version = o.optString("version", null);
            if (version == null || version.isEmpty()) return null;
            JSONObject wn = o.optJSONObject("whats_new");
            return new UpdateInfo(version,
                    o.optString("apk_url", ""),
                    o.optString("sha256", ""),
                    o.optLong("size", -1L),
                    wn == null ? "" : wn.optString("ru", ""),
                    wn == null ? "" : wn.optString("en", ""));
        } catch (Exception e) {
            return null;
        }
    }

    /** Component-wise semver comparison: true when remote > local. */
    public static boolean isNewer(String remote, String local) {
        int[] r = parseParts(remote);
        int[] l = parseParts(local);
        for (int i = 0; i < Math.max(r.length, l.length); i++) {
            int rv = i < r.length ? r[i] : 0;
            int lv = i < l.length ? l[i] : 0;
            if (rv != lv) return rv > lv;
        }
        // Numeric parts equal — decide by prerelease tag: a stable release
        // outranks any "-beta.N", and beta.N orders by N.
        return preRank(remote) > preRank(local);
    }

    private static long preRank(String v) {
        String clean = (v == null ? "" : v.trim()).replaceFirst("^v", "");
        int dash = clean.indexOf('-');
        if (dash < 0) return Long.MAX_VALUE; // stable release
        String tag = clean.substring(dash + 1).toLowerCase(Locale.ROOT);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("beta\\.?(\\d+)").matcher(tag);
        if (m.find()) return Integer.parseInt(m.group(1)); // beta.1 -> 1, beta.2 -> 2
        return 0; // unknown prerelease
    }

    private static int[] parseParts(String v) {
        if (v == null) return new int[0];
        String clean = v.trim().replaceFirst("^v", "").replaceFirst("[- (].*$", "");
        String[] parts = clean.split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = Integer.parseInt(parts[i].trim());
            } catch (NumberFormatException e) {
                out[i] = 0;
            }
        }
        return out;
    }

}
