package com.diplustohass;

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
}
