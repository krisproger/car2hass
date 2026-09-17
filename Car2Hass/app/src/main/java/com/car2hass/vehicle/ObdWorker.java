package com.car2hass.vehicle;

import android.content.Context;

import com.car2hass.AppConfig;
import com.car2hass.LogBuffer;
import com.car2hass.vehicle.obd.ObdSession;
import com.car2hass.vehicle.obd.ObdTransport;
import com.car2hass.vehicle.obd.ObdTransportFactory;

import java.util.Map;
import java.util.Set;

/**
 * OBD worker: holds a persistent Bluetooth session, polls the supported PIDs
 * on a fixed cadence and reconnects (with forced re-init) on any failure.
 */
public final class ObdWorker {
    private final Context ctx;
    private final ValueStore store;
    private volatile boolean running;
    private Thread thread;
    private static final long POLL_INTERVAL_MS = 2000;
    private static final long MAX_RETRY_BACKOFF_MS = 30_000;

    public ObdWorker(Context ctx, ValueStore store) {
        this.ctx = ctx;
        this.store = store;
    }

    public void start() {
        if (running) return;
        running = true;
        thread = new Thread(this::loop, "obd-worker");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }

    private void loop() {
        ObdSession session = null;
        long retryBackoffMs = POLL_INTERVAL_MS;
        boolean vinRead = false;
        while (running) {
            if (!AppConfig.isObdEnabled(ctx)) {
                close(session);
                session = null;
                vinRead = false;
                retryBackoffMs = POLL_INTERVAL_MS;
                sleep(2000);
                continue;
            }
            long cycleT0 = System.currentTimeMillis();
            String phase = "open";
            try {
                if (session == null) {
                    ObdTransport t = ObdTransportFactory.create(ctx);
                    // Plain open(): unlike openForced it never calls
                    // adapter.isDiscovering(), which requires the runtime
                    // BLUETOOTH_SCAN permission on Android 12+.
                    session = t.open();
                    phase = "warmUp";
                    if (session.initWarmUp() == null) {
                        LogBuffer.w("ObdWorker", "warmUp failed after "
                                + (System.currentTimeMillis() - cycleT0) + "ms");
                        close(session);
                        session = null;
                        sleep(3000);
                        continue;
                    }
                    LogBuffer.i("ObdWorker", "session established in "
                            + (System.currentTimeMillis() - cycleT0) + "ms");
                    // Real VIN over OBD (Mode 09 PID 02) — read once per session,
                    // separate key from the DiLink virtual VIN ("vvin"). Falls back
                    // to UDS ReadDataByIdentifier (0x22 DID 0xF190) after an
                    // extended-session request, both with and without an ECU header.
                    if (!vinRead) {
                        try {
                            String vin = Elm327Parser.parseVin(session.transact("0902", 6));
                            if (vin == null) {
                                session.transact("1003", 0);              // extended session
                                vin = Elm327Parser.parseVin(session.transact("22F190", 6));
                            }
                            if (vin == null) {
                                for (String hdr : new String[]{"7E0", "7E8"}) {
                                    session.transact("ATSH" + hdr, 0);    // target an ECU
                                    String v = Elm327Parser.parseVin(session.transact("22F190", 6));
                                    if (v != null) { vin = v; break; }
                                }
                                session.transact("ATSH", 0);              // restore auto header
                            }
                            if (vin != null) {
                                store.put("vin", vin, "obd");
                                vinRead = true;
                                LogBuffer.i("ObdWorker", "VIN: " + vin);
                            }
                        } catch (Exception e) {
                            LogBuffer.d("ObdWorker", "VIN read failed: " + e.getMessage());
                        }
                    }
                }
                phase = "read";
                Set<String> supported = ObdChannel.supportedPidsPref(ctx);
                int ok = 0;
                for (Map.Entry<String, String> e : ObdPidCodec.PID_TO_KEY.entrySet()) {
                    String pid = e.getKey();
                    if (supported != null && !supported.contains(pid)) continue;
                    String resp = session.transact(ObdPidCodec.command(pid).trim(), 1);
                    Integer value = resp != null ? ObdPidCodec.parse(pid, resp) : null;
                    if (value != null) {
                        store.put(e.getValue(), String.valueOf(value), "obd");
                        ok++;
                    }
                }
                phase = "done";
                AppConfig.setObdStatus(ctx, ok > 0 ? "connected" : "connected");
                AppConfig.setObdLastError(ctx, "");
                retryBackoffMs = POLL_INTERVAL_MS; // healthy cycle — reset backoff
                if (ok == 0) {
                    LogBuffer.d("ObdWorker", "cycle ok=0 pids (read phase took "
                            + (System.currentTimeMillis() - cycleT0) + "ms)");
                }
            } catch (Exception ex) {
                AppConfig.setObdStatus(ctx, "disconnected");
                AppConfig.setObdLastError(ctx, ex.getMessage());
                retryBackoffMs = Math.min(retryBackoffMs * 2, MAX_RETRY_BACKOFF_MS);
                LogBuffer.w("ObdWorker", "cycle failed in " + phase + " after "
                        + (System.currentTimeMillis() - cycleT0) + "ms: " + ex.getMessage()
                        + " — retry in " + retryBackoffMs + " ms");
                close(session);
                session = null;
                vinRead = false;
            }
            sleep(retryBackoffMs);
        }
        close(session);
    }

    private static void close(ObdSession s) {
        if (s != null) {
            try { s.close(); } catch (Exception ignored) {}
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}