package com.car2hass.vehicle.obd;

import com.car2hass.LogBuffer;

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
    public String transact(String command, int expectedLines) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(2000);
            return Elm327Io.transact(socket.getInputStream(),
                    socket.getOutputStream(), command, expectedLines);
        } catch (Exception e) {
            LogBuffer.d("TcpTransport", host + ":" + port + ": " + e.getMessage());
            return null;
        }
    }

    @Override
    public String describe() {
        return host + ":" + port;
    }
}