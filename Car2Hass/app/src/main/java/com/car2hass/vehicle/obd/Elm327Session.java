package com.car2hass.vehicle.obd;

import com.car2hass.vehicle.Elm327Parser;

import java.io.InputStream;
import java.io.OutputStream;

/** ELM327 over a live byte stream with a warm-up init (mirrors Car Scanner). */
public class Elm327Session implements ObdSession {
    private final InputStream in;
    private final OutputStream out;

    public Elm327Session(InputStream in, OutputStream out) {
        this.in = in;
        this.out = out;
    }

    @Override
    public String transact(String command, int expectedLines) {
        return Elm327Io.transact(in, out, command, expectedLines);
    }

    @Override
    public String initWarmUp() {
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                transact("ATZ", 1);
                Thread.sleep(800);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            transact("ATE0", 0);
            transact("ATH0", 0);
            transact("ATL0", 0);
            transact("ATSP0", 0);
            String resp = transact("ATI", 1);
            if (resp != null && Elm327Parser.extractVersion(resp) != null) {
                return Elm327Parser.extractVersion(resp);
            }
        }
        return null;
    }

    @Override
    public String lightInit() {
        transact("ATE0", 0);
        transact("ATH0", 0);
        transact("ATL0", 0);
        transact("ATSP0", 0);
        String resp = transact("ATI", 1);
        return resp != null ? Elm327Parser.extractVersion(resp) : null;
    }

    @Override
    public void close() {
        // subclasses close the underlying socket
    }
}