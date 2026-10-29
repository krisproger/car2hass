package com.car2hass.vehicle;

/** Fail-closed Voyah identity classification for new direct-only features. */
public final class VoyahIdentity {
    public enum State { NOT_VOYAH, OTHER_QINGGAN, UNCONFIRMED, CONFIRMED }

    private VoyahIdentity() {}

    public static State evaluate(boolean service, boolean odometer,
                                 String canBox, String carType, Integer vehicleType,
                                 boolean userConfirmed) {
        if (!service) return State.NOT_VOYAH;
        if (!odometer) return State.OTHER_QINGGAN;
        if (userConfirmed || marker(canBox) || marker(carType)
                || (vehicleType != null && vehicleType == 134)) return State.CONFIRMED;
        return State.UNCONFIRMED;
    }

    private static boolean marker(String s) {
        if (s == null) return false;
        String v = s.toUpperCase(java.util.Locale.US);
        return v.contains("VOYAH") || v.contains("H97X") || v.contains("H97C");
    }
}
