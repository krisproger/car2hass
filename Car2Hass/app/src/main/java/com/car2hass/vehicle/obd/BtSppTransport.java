package com.car2hass.vehicle.obd;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;

import com.car2hass.LogBuffer;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/** ELM327 over classic Bluetooth SPP (RFCOMM). */
public final class BtSppTransport implements ObdTransport {

    public static final UUID SPP_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final int CONNECT_TIMEOUT_MS = 8000;

    private final String address;

    public BtSppTransport(String address) {
        this.address = address;
    }

    /** Connects, warms up and returns the ELM version string, null on failure. */
    public static String connectAndAti(Context ctx, String address) {
        if (!bluetoothUsable(ctx)) return null;
        BtSppTransport t = new BtSppTransport(address);
        try (ObdSession s = t.openForced()) {
            return s.initWarmUp();
        } catch (Exception e) {
            LogBuffer.d("BtSppTransport", address + ": " + e.getMessage());
            return null;
        }
    }

    public static boolean bluetoothUsable(Context ctx) {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        return adapter != null && adapter.isEnabled();
    }

    /** First bonded device whose name looks like an OBD/ELM adapter, or null. */
    public static BluetoothDevice findBondedObdAdapter() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) return null;
        java.util.Set<BluetoothDevice> bonded = adapter.getBondedDevices();
        if (bonded == null) return null;
        String[] hints = {"ELM", "OBD", "V-LINK", "V_LINK", "CAR SCANNER",
                "CARD", "OBDII", "OBD II", "KONNWEI", "VEEPEAK", "LELINK",
                "OBDFORCE", "OBDLINK", "B-TOOL"};
        for (BluetoothDevice d : bonded) {
            String n = d.getName();
            if (n == null) continue;
            String up = n.toUpperCase(Locale.ROOT);
            for (String h : hints) {
                if (up.contains(h)) return d;
            }
        }
        return null;
    }

    @Override
    public ObdSession open() throws Exception {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            throw new IllegalStateException("bluetooth off");
        }
        BluetoothDevice device;
        try {
            device = adapter.getRemoteDevice(address);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("bad address " + address, e);
        }
        return session(connect(device));
    }

    /**
     * Mandatory re-init used on first connect and manual re-bind: ELM327 clones
     * often refuse a new SPP link right after the previous one was closed, so we
     * cancel discovery and let the stack settle before retrying once.
     */
    @Override
    public ObdSession openForced() throws Exception {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            throw new IllegalStateException("bluetooth off");
        }
        BluetoothDevice device;
        try {
            device = adapter.getRemoteDevice(address);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("bad address " + address, e);
        }
        Exception last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            if (attempt > 0) Thread.sleep(1000);
            try {
                if (adapter.isDiscovering()) adapter.cancelDiscovery();
                return session(connect(device));
            } catch (Exception e) {
                last = e;
            }
        }
        throw last != null ? last : new Exception("bt connect failed");
    }

    private static ObdSession session(BluetoothSocket socket) throws java.io.IOException {
        return new Elm327Session(socket.getInputStream(), socket.getOutputStream()) {
            @Override
            public void close() {
                try { socket.close(); } catch (Exception ignored) {}
            }
        };
    }

    private static BluetoothSocket connect(BluetoothDevice device) throws Exception {
        final java.util.concurrent.atomic.AtomicReference<BluetoothSocket> ref =
                new java.util.concurrent.atomic.AtomicReference<>();
        FutureTask<BluetoothSocket> task = new FutureTask<>(() -> {
            BluetoothSocket s = device.createRfcommSocketToServiceRecord(SPP_UUID);
            ref.set(s);
            s.connect();
            return s;
        });
        Thread t = new Thread(task, "bt-connect");
        t.start();
        try {
            return task.get(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException te) {
            // Close the half-open socket so it cannot complete later and hold the
            // adapter's SPP channel (which would block the next connect). Do NOT
            // interrupt the worker: closing the socket aborts connect() itself.
            BluetoothSocket s = ref.get();
            if (s != null) {
                try { s.close(); } catch (Exception ignored) {}
            }
            throw new java.net.SocketTimeoutException("bt connect timeout (" + CONNECT_TIMEOUT_MS + "ms)");
        }
    }

    @Override
    public String describe() {
        return address;
    }
}