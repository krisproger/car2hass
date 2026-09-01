package com.car2hass.vehicle.obd;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class ObdIoTest {

    public static void main(String[] args) throws Exception {
        testElm327Io();
        testTcpTransport();
        System.out.println("All ObdIo tests passed.");
    }

    private static void testElm327Io() throws Exception {
        PipedInputStream serverCmdIn = new PipedInputStream();
        PipedOutputStream cmdOut = new PipedOutputStream(serverCmdIn);
        PipedInputStream ourRespIn = new PipedInputStream();
        PipedOutputStream respOut = new PipedOutputStream(ourRespIn);

        Thread server = daemon(() -> {
            try {
                byte[] buf = new byte[64];
                serverCmdIn.read(buf);
                respOut.write("ELM327 v1.5\r\r>".getBytes("US-ASCII"));
                respOut.flush();
            } catch (IOException ignored) {}
        });
        server.start();

        String resp = Elm327Io.transact(ourRespIn, cmdOut, "ATI", 1);
        server.join(2000);
        if (resp == null || !resp.contains("ELM327")) {
            throw new AssertionError("Elm327Io: expected ELM327 response, got " + resp);
        }
    }

    private static void testTcpTransport() throws Exception {
        ServerSocket ss = new ServerSocket(0);
        final int port = ss.getLocalPort();
        Thread server = daemon(() -> {
            try (Socket s = ss.accept()) {
                InputStream is = s.getInputStream();
                OutputStream os = s.getOutputStream();
                byte[] buf = new byte[64];
                is.read(buf);
                os.write("ELM327 v1.5\r\r>".getBytes("US-ASCII"));
                os.flush();
            } catch (IOException ignored) {}
        });
        server.start();

        TcpTransport t = new TcpTransport("127.0.0.1", port);
        String resp = t.transact("ATI", 1);
        server.join(2000);
        ss.close();
        if (resp == null || !resp.contains("ELM327")) {
            throw new AssertionError("TcpTransport: expected ELM327 response, got " + resp);
        }
        if (!t.describe().equals("127.0.0.1:" + port)) {
            throw new AssertionError("TcpTransport.describe() mismatch: " + t.describe());
        }
    }

    private static Thread daemon(Runnable r) {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    }
}