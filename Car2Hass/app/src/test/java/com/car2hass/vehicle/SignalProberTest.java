package com.car2hass.vehicle;

import org.json.JSONObject;

public class SignalProberTest {
    public static void main(String[] args) throws Exception {
        testClassifySentinel();
        testClassifyUnsupported();
        testClassifyOk();
        testUndeclaredChannel();
        testSystemChannel();
        System.out.println("All SignalProber tests passed.");
    }

    private static void testClassifySentinel() {
        ProbeResult r = ProbeResult.fromRaw("65535", false);
        if (r.status != ProbeResult.Status.SENTINEL) throw new AssertionError("expected SENTINEL");
    }

    private static void testClassifyUnsupported() {
        ProbeResult r = ProbeResult.fromRaw(null, true);
        if (r.status != ProbeResult.Status.UNSUPPORTED) throw new AssertionError("expected UNSUPPORTED");
    }

    private static void testClassifyOk() {
        ProbeResult r = ProbeResult.fromRaw("42", false);
        if (r.status != ProbeResult.Status.OK) throw new AssertionError("expected OK");
    }

    private static void testUndeclaredChannel() throws Exception {
        RegistryStore rs = RegistryStore.of(
                new JSONObject("{\"sensors\":[{\"key\":\"speed\",\"channels\":{\"diplus\":{\"name\":\"车速\"}}}]}"),
                new JSONObject("{\"commands\":[]}"),
                new JSONObject("{\"profiles\":[]}"));
        ProbeResult r = SignalProber.probe(null, rs, "speed", "obd");
        if (r.status != ProbeResult.Status.UNSUPPORTED) throw new AssertionError("expected UNSUPPORTED for undeclared channel");
    }

    private static void testSystemChannel() throws Exception {
        RegistryStore rs = RegistryStore.of(
                new JSONObject("{\"sensors\":[{\"key\":\"location_lat\",\"channels\":{\"system\":{\"field\":\"lat\"}}}]}"),
                new JSONObject("{\"commands\":[]}"),
                new JSONObject("{\"profiles\":[]}"));
        ProbeResult r = SignalProber.probe(null, rs, "location_lat", "system");
        if (r.status != ProbeResult.Status.OK) throw new AssertionError("expected OK for system channel");
    }
}
