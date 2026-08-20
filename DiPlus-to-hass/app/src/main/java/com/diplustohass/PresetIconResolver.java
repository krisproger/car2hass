package com.diplustohass;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolves preset icon names (e.g. "air-conditioner") to bitmaps.
 *
 * Lookup order:
 *   1. filesDir/icons/&lt;name&gt;.png — user override
 *   2. assets/icons/&lt;name&gt;.png  — bundled with the APK
 *   3. null — the caller falls back to the emoji text icon
 *
 * Never throws: any I/O or decode problem yields null so the dashboard keeps
 * working with emoji icons even when PNG files are missing or broken.
 */
public final class PresetIconResolver {

    private static final String TAG = "PresetIconResolver";
    private static final String ICONS_DIR = "icons";
    private static final String EXT = ".png";

    /** Bundled-asset cache; a null value means "known missing". */
    private static final Map<String, Bitmap> assetCache = new HashMap<>();
    /** User-override cache keyed by name, invalidated on file mtime change. */
    private static final Map<String, Bitmap> overrideCache = new HashMap<>();
    private static final Map<String, Long> overrideMtime = new HashMap<>();
    /** Override mtimes whose decode failed (known-broken PNG), so a broken file
     *  is not re-decoded on every bind until it actually changes. */
    private static final Map<String, Long> overrideBrokenMtime = new HashMap<>();

    private PresetIconResolver() {}

    /** Bitmap for the icon, or null when no PNG exists (emoji fallback). */
    public static Bitmap resolve(Context ctx, String iconName) {
        if (ctx == null) return null;
        String name = sanitizeIconName(iconName);
        if (name == null) return null;
        Bitmap override = resolveOverride(ctx, name);
        if (override != null) return override;
        return resolveAsset(ctx, name);
    }

    /**
     * True when a cached decode result is still valid for this exact file
     * mtime: either a successfully cached bitmap or a known-broken marker.
     * With no cached result the file must be decoded again.
     */
    static boolean overrideResultCached(long mtime, Long cachedMtime, Long brokenMtime) {
        return (cachedMtime != null && cachedMtime == mtime)
                || (brokenMtime != null && brokenMtime == mtime);
    }

    private static synchronized Bitmap resolveOverride(Context ctx, String name) {
        try {
            File f = overrideFileFor(ctx.getFilesDir(), name);
            if (f == null || !f.isFile()) return null;
            long mtime = f.lastModified();
            Long cachedMtime = overrideMtime.get(name);
            Long brokenMtime = overrideBrokenMtime.get(name);
            if (overrideResultCached(mtime, cachedMtime, brokenMtime)) {
                return overrideCache.get(name); // null when the file is known-broken
            }
            Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath());
            if (bmp != null) {
                overrideCache.put(name, bmp);
                overrideMtime.put(name, mtime);
                overrideBrokenMtime.remove(name);
            } else {
                // Cache the failure so a broken PNG is not re-decoded on every
                // bind; the cache is invalidated by the mtime when the user
                // replaces the file.
                overrideBrokenMtime.put(name, mtime);
            }
            return bmp;
        } catch (Exception e) {
            LogBuffer.w(TAG, "Override icon failed for " + name + ": " + e.getMessage());
            return null;
        }
    }

    private static synchronized Bitmap resolveAsset(Context ctx, String name) {
        if (assetCache.containsKey(name)) return assetCache.get(name);
        Bitmap bmp = null;
        try (InputStream is = ctx.getAssets().open(assetPathFor(name))) {
            bmp = BitmapFactory.decodeStream(is);
        } catch (Exception e) {
            // Missing asset or decode failure — emoji fallback, logged once per name.
            LogBuffer.w(TAG, "No bundled icon for " + name);
        }
        assetCache.put(name, bmp);
        return bmp;
    }

    /**
     * Only [a-z0-9_-] names map to files; anything else (null, empty, path
     * separators, dots, non-ASCII) is rejected so icon names can never escape
     * the icons directory.
     */
    static String sanitizeIconName(String iconName) {
        if (iconName == null) return null;
        String name = iconName.trim().toLowerCase();
        if (name.isEmpty()) return null;
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            boolean ok = (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')
                    || ch == '-' || ch == '_';
            if (!ok) return null;
        }
        return name;
    }

    static String assetPathFor(String name) {
        return ICONS_DIR + "/" + name + EXT;
    }

    static File overrideFileFor(File filesDir, String name) {
        if (filesDir == null || name == null) return null;
        return new File(new File(filesDir, ICONS_DIR), name + EXT);
    }
}
