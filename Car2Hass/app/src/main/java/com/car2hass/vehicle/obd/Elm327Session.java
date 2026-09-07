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
            String atz = null;
            try {
                atz = transact("ATZ", 1);
                Thread.sleep(800);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            // A fresh clone may drop the very first ATZ while it boots; the
            // retry attempt must answer. ATE0/ATH0/ATL0/ATSP0 must confirm OK.
            if (attempt == 1 && !cmdOk(atz, "ELM327")) continue;
            if (!cmdOk(transact("ATE0", 0), "OK")) continue;
            if (!cmdOk(transact("ATH0", 0), "OK")) continue;
            if (!cmdOk(transact("ATL0", 0), "OK")) continue;
            if (!cmdOk(transact("ATSP0", 0), "OK")) continue;
            String resp = transact("ATI", 1);
            if (resp != null && Elm327Parser.extractVersion(resp) != null) {
                return Elm327Parser.extractVersion(resp);
            }
        }
        return null;
    }

    @Override
    public String lightInit() {
        if (!cmdOk(transact("ATE0", 0), "OK")) return null;
        if (!cmdOk(transact("ATH0", 0), "OK")) return null;
        if (!cmdOk(transact("ATL0", 0), "OK")) return null;
        if (!cmdOk(transact("ATSP0", 0), "OK")) return null;
        String resp = transact("ATI", 1);
        return resp != null ? Elm327Parser.extractVersion(resp) : null;
    }

    private static boolean cmdOk(String raw, String expected) {
        if (raw == null) return false;
        return raw.toUpperCase(java.util.Locale.ROOT).contains(expected);
    }

    @Override
    public void close() {
        // subclasses close the underlying socket
    }
}