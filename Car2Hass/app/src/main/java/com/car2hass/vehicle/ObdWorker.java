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
        while (running) {
            if (!AppConfig.isObdEnabled(ctx)) {
                close(session);
                session = null;
                sleep(2000);
                continue;
            }
            try {
                if (session == null) {
                    ObdTransport t = ObdTransportFactory.create(ctx);
                    session = t.openForced();
                    if (session.initWarmUp() == null) {
                        close(session);
                        session = null;
                        sleep(3000);
                        continue;
                    }
                    LogBuffer.i("ObdWorker", "session established");
                }
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
                AppConfig.setObdStatus(ctx, ok > 0 ? "connected" : "connected");
                AppConfig.setObdLastError(ctx, "");
            } catch (Exception ex) {
                AppConfig.setObdStatus(ctx, "disconnected");
                AppConfig.setObdLastError(ctx, ex.getMessage());
                LogBuffer.w("ObdWorker", "cycle failed: " + ex.getMessage());
                close(session);
                session = null;
            }
            sleep(POLL_INTERVAL_MS);
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