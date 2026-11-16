package com.car2hass;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

public final class HaAddress {
    public final String host;
    public final int port;
    public final boolean https;

    private HaAddress(String host, int port, boolean https) {
        this.host = host;
        this.port = port;
        this.https = https;
    }

    public static HaAddress parse(String input, boolean httpsFlag) {
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("empty address");
        }
        String raw = input.trim();
        boolean hasScheme = raw.regionMatches(true, 0, "http://", 0, 7)
                || raw.regionMatches(true, 0, "https://", 0, 8);
        String candidate = hasScheme ? raw : "//" + raw;
        final URI uri;
        try {
            uri = new URI(candidate);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("invalid address", e);
        }
        String scheme = hasScheme
                ? uri.getScheme().toLowerCase(Locale.ROOT)
                : (httpsFlag ? "https" : "http");
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("unsupported scheme");
        }
        if (uri.getRawUserInfo() != null || uri.getHost() == null
                || uri.getHost().isEmpty()) {
            throw new IllegalArgumentException("invalid host");
        }
        int port = uri.getPort();
        if (port == -1) {
            port = "https".equals(scheme) ? 443 : 80;
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("invalid port");
        }
        return new HaAddress(uri.getHost(), port, "https".equals(scheme));
    }

    public String scheme() {
        return https ? "https" : "http";
    }

    public boolean isDefaultPort() {
        return (https && port == 443) || (!https && port == 80);
    }

    public String authority() {
        String h = host.indexOf(':') >= 0 && !host.startsWith("[")
                ? "[" + host + "]" : host;
        return isDefaultPort() ? h : h + ":" + port;
    }

    public String canonicalAddress() {
        return authority();
    }

    public String baseUrl() {
        return scheme() + "://" + authority();
    }

    public String url(String path) {
        String p = path == null || path.isEmpty() ? "/" : path;
        if (!p.startsWith("/")) p = "/" + p;
        return baseUrl() + p;
    }
}
