package com.car2hass.vehicle;

import android.content.Context;
import android.provider.Settings;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Anonymous device id for probe reports: sha256(model + 2-byte Android ID
 * prefix + salt). Not linked to the user or the vehicle (spec Section 5).
 */
public final class DeviceAnon {
    private static final String SALT = "diplus2hass_probe_v1";

    private DeviceAnon() {}

    /** Pure digest, testable without Android. */
    public static String digest(String model, String androidIdPrefix2BytesHex, String salt) {
        String input = nz(model) + nz(androidIdPrefix2BytesHex) + nz(salt);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "anon";
        }
    }

    public static String fromContext(Context ctx) {
        String prefix = "";
        try {
            String id = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ANDROID_ID);
            if (id != null && id.length() >= 4) prefix = id.substring(0, 4);
        } catch (Exception ignored) {}
        return digest(android.os.Build.MODEL, prefix, SALT);
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
