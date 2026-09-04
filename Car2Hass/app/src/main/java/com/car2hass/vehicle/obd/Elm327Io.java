package com.car2hass.vehicle.obd;

import com.car2hass.vehicle.Elm327Parser;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Shared request/response loop for ELM327 over any byte stream. */
public final class Elm327Io {

    private static final int READ_TIMEOUT_MS = 1000;

    private Elm327Io() {}

    public static String transact(InputStream is, OutputStream os,
                                  String command, int expectedLines) {
        try {
            os.write((command + "\r").getBytes(StandardCharsets.US_ASCII));
            os.flush();
            StringBuilder sb = new StringBuilder();
            long deadline = System.currentTimeMillis() + READ_TIMEOUT_MS * 6;
            byte[] buf = new byte[512];
            while (sb.length() < 4096 && System.currentTimeMillis() < deadline) {
                if (is.available() > 0) {
                    int n = is.read(buf);
                    if (n < 0) break;
                    sb.append(new String(buf, 0, n, StandardCharsets.US_ASCII));
                    if (sb.indexOf(">") >= 0
                            && Elm327Parser.splitLines(sb.toString()).size() >= expectedLines) {
                        break;
                    }
                } else {
                    Thread.sleep(20);
                }
            }
            return sb.length() > 0 ? sb.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }
}