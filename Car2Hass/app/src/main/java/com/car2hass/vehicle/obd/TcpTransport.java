package com.car2hass.vehicle.obd;

import java.net.InetSocketAddress;
import java.net.Socket;

/** ELM327 over TCP (WiFi adapters); kept as the fallback transport. */
public final class TcpTransport implements ObdTransport {

    private static final int CONNECT_TIMEOUT_MS = 3000;

    private final String host;
    private final int port;

    public TcpTransport(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public ObdSession open() throws Exception {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
        socket.setSoTimeout(2000);
        return new Elm327Session(socket.getInputStream(), socket.getOutputStream()) {
            @Override
            public void close() {
                try { socket.close(); } catch (Exception ignored) {}
            }
        };
    }

    @Override
    public String describe() {
        return host + ":" + port;
    }
}