package com.diplustohass;

import java.util.Locale;

/**
 * Network safety helpers. Pure Java — unit-testable without the Android
 * runtime (see NetSafetyTest).
 */
public final class NetSafety {

    private NetSafety() {
    }

    /**
     * Returns true if the host is loopback or a private/LAN address where
     * cleartext HTTP is acceptable: 127.0.0.0/8, localhost, 10.0.0.0/8,
     * 172.16.0.0/12, 192.168.0.0/16, IPv6 loopback (::1), link-local
     * (fe80::/10) and unique-local (fc00::/7).
     *
     * <p>Hostnames (e.g. "example.com") and public IPs return false.</p>
     */
    public static boolean isPrivateHost(String host) {
        if (host == null) {
            return false;
        }
        String h = host.trim().toLowerCase(Locale.ROOT);
        if (h.isEmpty()) {
            return false;
        }
        if (h.startsWith("[") && h.endsWith("]")) {
            h = h.substring(1, h.length() - 1);
        }
        if (h.equals("localhost") || h.endsWith(".localhost")) {
            return true;
        }
        if (h.equals("::1")) {
            return true;
        }
        if (h.startsWith("fe80:")) {
            return true;
        }
        if ((h.startsWith("fc") || h.startsWith("fd")) && h.indexOf(':') >= 0) {
            return true; // IPv6 unique-local fc00::/7
        }
        String[] p = h.split("\\.");
        if (p.length == 4) {
            try {
                int a = Integer.parseInt(p[0]);
                int b = Integer.parseInt(p[1]);
                if (a == 127) return true;
                if (a == 10) return true;
                if (a == 192 && b == 168) return true;
                if (a == 172 && b >= 16 && b <= 31) return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }
}
