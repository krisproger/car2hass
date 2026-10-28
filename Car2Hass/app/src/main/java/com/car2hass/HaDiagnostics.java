package com.car2hass;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.Locale;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Staged HA connectivity test: DNS -> TCP -> TLS -> HTTP. Each stage is logged
 * with timing and address (IPv4/IPv6), so an intermittent "timeout" can be
 * attributed to the exact layer instead of guessing.
 */
public final class HaDiagnostics {

    public static final class Result {
        public final boolean httpOk;
        public final String report;
        Result(boolean httpOk, String report) { this.httpOk = httpOk; this.report = report; }
    }

    private HaDiagnostics() {}

    public static Result run(String baseUrl, String token) {
        StringBuilder r = new StringBuilder();
        boolean ok = false;
        try {
            URL url = new URL(baseUrl);
            String host = url.getHost();
            int port = url.getPort() > 0 ? url.getPort() : url.getDefaultPort();
            boolean tls = "https".equalsIgnoreCase(url.getProtocol());
            r.append("HA ").append(url.getProtocol()).append("://").append(host)
             .append(':').append(port).append('\n');

            long t0 = System.currentTimeMillis();
            InetAddress[] addrs = InetAddress.getAllByName(host);
            r.append("DNS: ").append(addrs.length).append(" addr, ")
             .append(System.currentTimeMillis() - t0).append(" ms\n");
            for (InetAddress a : addrs) {
                r.append("  ").append(a.getHostAddress()).append('\n');
            }

            InetAddress reachable = null;
            for (InetAddress a : addrs) {
                long s = System.currentTimeMillis();
                try (Socket sock = new Socket()) {
                    sock.connect(new InetSocketAddress(a, port), 4000);
                    r.append("TCP ").append(a.getHostAddress()).append(':').append(port)
                     .append(": OK ").append(System.currentTimeMillis() - s).append(" ms\n");
                    reachable = a;
                    break;
                } catch (Exception e) {
                    r.append("TCP ").append(a.getHostAddress()).append(':').append(port)
                     .append(": ").append(e.getClass().getSimpleName()).append(": ")
                     .append(e.getMessage()).append('\n');
                }
            }
            if (reachable == null) {
                r.append("=> stop: no TCP connection\n");
                return new Result(false, r.toString());
            }

            if (tls) {
                long s = System.currentTimeMillis();
                try (SSLSocket ss = (SSLSocket) SSLSocketFactory.getDefault()
                        .createSocket(reachable, port)) {
                    ss.setSoTimeout(5000);
                    ss.startHandshake();
                    r.append("TLS: OK ").append(ss.getSession().getProtocol())
                     .append(' ').append(ss.getSession().getCipherSuite())
                     .append(' ').append(System.currentTimeMillis() - s).append(" ms\n");
                } catch (Exception e) {
                    r.append("TLS: ").append(e.getClass().getSimpleName()).append(": ")
                     .append(e.getMessage()).append('\n');
                    r.append("=> stop: TLS handshake failed\n");
                    return new Result(false, r.toString());
                }
            }

            long s = System.currentTimeMillis();
            HttpURLConnection conn = (HttpURLConnection) new URL(baseUrl + "/api/").openConnection();
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            if (token != null && !token.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }
            int code = conn.getResponseCode();
            String location = conn.getHeaderField("Location");
            try (InputStream es = code >= 400 ? conn.getErrorStream() : conn.getInputStream()) {
                if (es != null) { byte[] b = new byte[256]; es.read(b); }
            } catch (Exception ignored) {}
            conn.disconnect();
            r.append("HTTP: ").append(code).append(' ')
             .append(System.currentTimeMillis() - s).append(" ms")
             .append(location == null || location.isEmpty() ? "" : " -> " + location).append('\n');
            ok = code == 200;
            r.append(ok ? "=> HA OK" : "=> HA HTTP error " + code);
        } catch (Exception e) {
            r.append("FAIL: ").append(e.getClass().getSimpleName()).append(": ")
             .append(e.getMessage());
        }
        LogBuffer.i("HaDiag", r.toString().replace('\n', ' '));
        return new Result(ok, r.toString());
    }
}
