package com.car2hass;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

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

    /** Fallback source when the project site is unreachable. */
    public static final String GITHUB_API =
            "https://api.github.com/repos/krisproger/car2hass";

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
        /** "site" or "github" — where this update was resolved from. */
        public final String source;

        UpdateInfo(String version, String apkUrl, String sha256, long size,
                   String notesRu, String notesEn, String source) {
            this.version = version;
            this.apkUrl = apkUrl;
            this.sha256 = sha256;
            this.size = size;
            this.notesRu = notesRu;
            this.notesEn = notesEn;
            this.source = source;
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
                    wn == null ? "" : wn.optString("en", ""),
                    "site");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parses GitHub Releases JSON (a single release from /releases/latest or an
     * array from /releases?per_page=). Picks the newest release allowed by the
     * channel and requires the {@code Car2Hass_v<version>.apk} asset.
     */
    public static UpdateInfo parseGithubReleases(String json, String channel) {
        try {
            Object root = new JSONTokener(json).nextValue();
            boolean beta = "beta".equalsIgnoreCase(channel);
            if (root instanceof JSONArray) {
                JSONArray arr = (JSONArray) root;
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o == null) continue;
                    UpdateInfo info = fromGithubRelease(o, beta);
                    // Skip drafts/channel-mismatched/no-APK releases and keep
                    // looking for an installable one.
                    if (info != null) return info;
                }
                return null;
            }
            return root instanceof JSONObject
                    ? fromGithubRelease((JSONObject) root, beta) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static UpdateInfo fromGithubRelease(JSONObject release, boolean beta) {
        if (release == null || release.optBoolean("draft", false)) return null;
        if (!beta && release.optBoolean("prerelease", false)) return null;
        String tag = release.optString("tag_name", "");
        String version = tag.startsWith("v") ? tag.substring(1) : tag;
        if (version.isEmpty()) return null;

        String apkUrl = "";
        String sha256 = "";
        long size = -1L;
        String wanted = "Car2Hass_v" + version + ".apk";
        JSONArray assets = release.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject a = assets.optJSONObject(i);
                if (a == null || !wanted.equals(a.optString("name", ""))) continue;
                apkUrl = a.optString("browser_download_url", "");
                size = a.optLong("size", -1L);
                String digest = a.optString("digest", "");
                if (digest.startsWith("sha256:")) sha256 = digest.substring(7);
                break;
            }
        }
        if (apkUrl.isEmpty()) return null;
        return new UpdateInfo(version, apkUrl, sha256, size,
                release.optString("body", ""), "", "github");
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
