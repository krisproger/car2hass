package com.car2hass.vehicle;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;

import com.car2hass.CANDataItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Availability marker for the always-on system source (GPS, device). The
 * actual reading lives in LocationSource/TelemetryService and never goes
 * through DataChannel cycles, so this channel only reports whether system
 * data is really available: location permission granted and a fresh fix.
 */
public class SystemChannel implements DataChannel {

    /** Fresh-fix window for the probe. */
    private static final long FIX_MAX_AGE_MS = 5 * 60 * 1000L;

    @Override
    public String id() { return "system"; }

    @Override
    public String displayName() { return "System (GPS, device)"; }

    @Override
    public boolean supportsCommands() { return false; }

    @Override
    public ChannelResult probe(Context ctx) {
        if (!hasLocationPermission(ctx)) {
            return ChannelResult.dead("нет разрешения на локацию");
        }
        long ageMs = lastFixAgeMs(ctx);
        if (ageMs < 0) return ChannelResult.dead("GPS fix ещё не получен");
        if (ageMs > FIX_MAX_AGE_MS) return ChannelResult.dead("GPS fix устарел (" + (ageMs / 1000) + " с)");
        return ChannelResult.ok(7);
    }

    /** Per-sensor probe used by SignalProber: honest check per field type. */
    public static ProbeResult probeField(Context ctx, String field) {
        boolean location = field != null && field.startsWith("location_");
        if (location && !hasLocationPermission(ctx)) {
            return ProbeResult.error("нет разрешения на локацию");
        }
        if (location) {
            long ageMs = lastFixAgeMs(ctx);
            if (ageMs < 0 || ageMs > FIX_MAX_AGE_MS) return ProbeResult.error("нет свежего GPS fix");
        }
        return ProbeResult.fromRaw("ok", false);
    }

    private static boolean hasLocationPermission(Context ctx) {
        return ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
            || ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Age of the freshest last-known fix across providers; -1 when none. */
    private static long lastFixAgeMs(Context ctx) {
        LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return -1;
        long best = -1;
        for (String provider : lm.getProviders(true)) {
            try {
                android.location.Location f = lm.getLastKnownLocation(provider);
                if (f == null) continue;
                long age = System.currentTimeMillis() - f.getTime();
                if (best < 0 || age < best) best = age;
            } catch (SecurityException ignored) {
            }
        }
        return best;
    }

    @Override
    public List<CANDataItem> read(Context ctx, List<CANDataItem> knownItems) {
        return new ArrayList<>(); // handled by LocationSource outside the cycles
    }
}
