package com.car2hass;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * Helpers for the local DiPlus HTTP API. Pure Java — unit-testable without
 * the Android runtime (see DiplusApiTest).
 */
public final class DiplusApi {

    private DiplusApi() {
    }

    /**
     * Append the DiPlus {@code auth} token to an API URL. Returns the URL
     * unchanged when the token is null/empty, so firmwares that do not
     * require authorization keep working.
     */
    public static String withAuth(String url, String auth) {
        if (url == null) {
            return null;
        }
        if (auth == null) {
            return url;
        }
        String token = auth.trim();
        if (token.isEmpty()) {
            return url;
        }
        try {
            return url + "&auth=" + URLEncoder.encode(token, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return url; // UTF-8 is always available
        }
    }

    /**
     * Replace the DiPlus {@code auth} token value in a URL with "***" for
     * safe logging (review #5). Returns the input unchanged when no auth
     * parameter is present.
     */
    public static String maskAuth(String url) {
        if (url == null) {
            return null;
        }
        // The token is URL-encoded when appended, so it can be percent-encoded
        // characters, but never an '&' — matching on (?|&)auth=<non-&> is safe.
        int idx = url.indexOf("&auth=");
        if (idx < 0) {
            idx = url.indexOf("?auth=");
        }
        if (idx < 0) {
            return url;
        }
        int end = url.indexOf('&', idx + "&auth=".length());
        if (end < 0) {
            end = url.length();
        }
        return url.substring(0, idx + "&auth=".length()) + "***" + url.substring(end);
    }
}
