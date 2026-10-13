package com.car2hass;

/**
 * Pre-queue snapshot dedup shared by the HA and cloud channels: a snapshot whose
 * sensors fully repeat the previous one while the car moved less than 5 m is
 * skipped (stationary car → no redundant queue growth, network and battery use).
 * Each channel keeps its own last-kept snapshot (their signal sets differ).
 */
public final class SnapshotDedup {

    public enum Channel { HA, CLOUD }

    private static final double MIN_MOVE_M = 5.0;

    private static final class State {
        double lat = Double.NaN;
        double lon = Double.NaN;
        long ts = 0;
        String signals = null;
        boolean has = false;
    }

    private static final java.util.EnumMap<Channel, State> STATES =
            new java.util.EnumMap<>(Channel.class);
    static {
        for (Channel c : Channel.values()) STATES.put(c, new State());
    }

    private SnapshotDedup() {}

    /**
     * True when this snapshot duplicates the last kept one for the channel:
     * identical sensors AND the car moved less than 5 m. NaN coordinates are
     * never treated as duplicates.
     */
    public static synchronized boolean isDuplicate(Channel ch, double lat, double lon, String signals) {
        State s = STATES.get(ch);
        if (!s.has || signals == null) return false;
        boolean sameSensors = signals.equals(s.signals);
        boolean near = isFinite(lat, lon) && isFinite(s.lat, s.lon)
                && distanceM(lat, lon, s.lat, s.lon) < MIN_MOVE_M;
        return sameSensors && near;
    }

    /** Records a kept snapshot as the channel's new reference point. */
    public static synchronized void keep(Channel ch, double lat, double lon, long tsSec, String signals) {
        State s = STATES.get(ch);
        s.lat = lat;
        s.lon = lon;
        s.ts = tsSec;
        s.signals = signals;
        s.has = true;
    }

    private static boolean isFinite(double lat, double lon) {
        return !Double.isNaN(lat) && !Double.isNaN(lon)
                && !Double.isInfinite(lat) && !Double.isInfinite(lon)
                && !(lat == 0.0 && lon == 0.0);
    }

    /** Great-circle distance in meters. */
    static double distanceM(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371000.0;
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dp = Math.toRadians(lat2 - lat1);
        double dl = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2)
                + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * r * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }
}
