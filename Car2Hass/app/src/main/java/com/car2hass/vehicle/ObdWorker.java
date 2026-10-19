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
public final class ObdWorker implements ChannelWorker {
    private final Context ctx;
    private final ValueStore store;
    private volatile boolean running;
    private volatile ChannelWorkerStatus.Result lastResult = ChannelWorkerStatus.Result.OK;
    private volatile String lastError = "";
    private volatile String lastPhase = "";
    private volatile long retryBackoffMs = POLL_INTERVAL_MS;
    private volatile long cycleCount;
    private volatile long lastCycleAtMs;
    private volatile long threadStartAtMs;
    private volatile long errorCount;
    /** Consecutive failed connects; after a few, try the heavier forced open. */
    private int openFailures = 0;
    private Thread thread;
    private final WorkerRetryPolicy policy =
            new WorkerRetryPolicy(POLL_INTERVAL_MS, MAX_RETRY_BACKOFF_MS);
    private static final long POLL_INTERVAL_MS = 2000;
    private static final long MAX_RETRY_BACKOFF_MS = 30_000;
    /** Consecutive empty cycles before forcing a reconnect (adapter may be wedged). */
    private static final int MAX_EMPTY_CYCLES = 3;
    private static final long EMPTY_LOG_INTERVAL_MS = 30_000;
    private int emptyCycles = 0;
    private long lastEmptyLogMs = 0;

    public ObdWorker(Context ctx, ValueStore store) {
        this.ctx = ctx;
        this.store = store;
    }

    @Override
    public String channelId() { return "obd"; }

    @Override
    public boolean isRunning() { return running; }

    @Override
    public ChannelWorkerStatus status() {
        return new ChannelWorkerStatus("obd", running, lastResult, lastError, lastPhase,
                POLL_INTERVAL_MS, retryBackoffMs, cycleCount, lastCycleAtMs,
                threadStartAtMs, errorCount);
    }

    @Override
    public synchronized void start() {
        if (running) return;
        running = true;
        threadStartAtMs = System.currentTimeMillis();
        thread = new Thread(this::loop, "obd-worker");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public synchronized void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }

    private void loop() {
        ObdSession session = null;
        boolean vinRead = false;
        while (running) {
            if (!AppConfig.isObdEnabled(ctx)) {
                close(session);
                session = null;
                vinRead = false;
                policy.onSuccess();
                retryBackoffMs = POLL_INTERVAL_MS;
                // OBD is off: clear the runtime state so the diagnostics line
                // does not keep a stale status/error from a previous session.
                AppConfig.setObdStatus(ctx, "disconnected");
                AppConfig.setObdLastError(ctx, "");
                AppConfig.setObdPhase(ctx, "");
                AppConfig.setObdBackoffMs(ctx, 0);
                AppConfig.setObdErrorCount(ctx, 0);
                errorCount = 0;
                openFailures = 0;
                sleep(2000);
                continue;
            }
            long cycleT0 = System.currentTimeMillis();
            String phase = "open";
            try {
                if (session == null) {
                    AppConfig.setObdStatus(ctx, "connecting");
                    ObdTransport t = ObdTransportFactory.create(ctx);
                    // A cheap ELM327 clone may accept the SPP link only
                    // intermittently ("read failed ... read ret: -1"), so try the
                    // full open+warmUp a few times within the cycle before paying
                    // the long backoff. Plain open() avoids adapter.isDiscovering()
                    // (BLUETOOTH_SCAN on Android 12+); after repeated connect
                    // failures the heavier forced open is used.
                    final int maxAttempts = 3;
                    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                        AppConfig.setObdPhase(ctx, "open");
                        try {
                            if (openFailures >= 3) {
                                try {
                                    session = t.openForced();
                                } catch (Exception forcedEx) {
                                    session = t.open();
                                }
                            } else {
                                session = t.open();
                            }
                            phase = "warmUp";
                            AppConfig.setObdPhase(ctx, "warmUp");
                            if (session.initWarmUp() != null) break;
                        } catch (Exception ex) {
                            if (attempt >= maxAttempts) throw ex;
                        }
                        close(session);
                        session = null;
                        if (attempt < maxAttempts) {
                            LogBuffer.d("ObdWorker", "open/warmUp attempt " + attempt + " failed, retrying");
                            sleep(1000);
                        }
                    }
                    if (session == null) {
                        String code = ObdError.INIT_FAILED;
                        AppConfig.setObdStatus(ctx, "disconnected");
                        AppConfig.setObdLastError(ctx, code);
                        AppConfig.setObdPhase(ctx, "warmUp");
                        lastResult = ChannelWorkerStatus.Result.ERROR;
                        lastError = code;
                        errorCount++;
                        retryBackoffMs = policy.onFailure();
                        LogBuffer.w("ObdWorker", "warmUp failed after "
                                + (System.currentTimeMillis() - cycleT0) + "ms — retry in "
                                + retryBackoffMs + " ms");
                        AppConfig.setObdErrorCount(ctx, (int) errorCount);
                        AppConfig.setObdBackoffMs(ctx, retryBackoffMs);
                        cycleCount++;
                        lastCycleAtMs = System.currentTimeMillis();
                        sleep(retryBackoffMs);
                        continue;
                    }
                    openFailures = 0;
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
                AppConfig.setObdPhase(ctx, "read");
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
                AppConfig.setObdStatus(ctx, ok > 0 ? "connected" : "disconnected");
                AppConfig.setObdLastError(ctx, ok > 0 ? "" : ObdError.NO_DATA);
                AppConfig.setObdPhase(ctx, "done");
                lastResult = ok > 0 ? ChannelWorkerStatus.Result.OK : ChannelWorkerStatus.Result.EMPTY;
                lastError = ok > 0 ? "" : ObdError.NO_DATA;
                if (ok > 0) {
                    emptyCycles = 0;
                    retryBackoffMs = policy.onSuccess();
                } else {
                    // Connected but no PID data: do not spin at 2 s forever. Force a
                    // reconnect after a few empty cycles and back off like an error.
                    emptyCycles++;
                    if (emptyCycles >= MAX_EMPTY_CYCLES) {
                        LogBuffer.w("ObdWorker", "no PID data for " + emptyCycles
                                + " cycles — reconnecting");
                        close(session);
                        session = null;
                        vinRead = false;
                        emptyCycles = 0;
                    }
                    retryBackoffMs = policy.onFailure();
                    long nowLog = System.currentTimeMillis();
                    if (nowLog - lastEmptyLogMs > EMPTY_LOG_INTERVAL_MS) {
                        lastEmptyLogMs = nowLog;
                        LogBuffer.d("ObdWorker", "cycle ok=0 pids (read phase took "
                                + (nowLog - cycleT0) + "ms), backoff " + retryBackoffMs + " ms");
                    }
                }
            } catch (Exception ex) {
                String code = ObdError.classify(phase, ex);
                AppConfig.setObdStatus(ctx, "disconnected");
                AppConfig.setObdLastError(ctx, code);
                AppConfig.setObdPhase(ctx, phase);
                lastResult = ChannelWorkerStatus.Result.ERROR;
                lastError = code;
                errorCount++;
                openFailures++;
                retryBackoffMs = policy.onFailure();
                LogBuffer.w("ObdWorker", "cycle failed in " + phase + " after "
                        + (System.currentTimeMillis() - cycleT0) + "ms: " + code
                        + " (" + ex.getMessage() + ") — retry in " + retryBackoffMs + " ms");
                close(session);
                session = null;
                vinRead = false;
            }
            lastPhase = phase;
            AppConfig.setObdErrorCount(ctx, (int) errorCount);
            AppConfig.setObdBackoffMs(ctx, retryBackoffMs);
            cycleCount++;
            lastCycleAtMs = System.currentTimeMillis();
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