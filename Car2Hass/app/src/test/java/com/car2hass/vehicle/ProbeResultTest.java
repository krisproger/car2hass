package com.car2hass.vehicle;

public class ProbeResultTest {
    public static void main(String[] args) throws Exception {
        if (ProbeResult.fromRaw("42", false).status != ProbeResult.Status.OK)
            throw new AssertionError("ok");
        if (ProbeResult.fromRaw("65535", false).status != ProbeResult.Status.SENTINEL)
            throw new AssertionError("sentinel");
        if (ProbeResult.fromRaw("-10013", false).status != ProbeResult.Status.SENTINEL)
            throw new AssertionError("sentinel2");
        if (ProbeResult.fromRaw(null, false).status != ProbeResult.Status.ERROR)
            throw new AssertionError("error on null");
        if (ProbeResult.fromRaw(null, true).status != ProbeResult.Status.UNSUPPORTED)
            throw new AssertionError("unsupported flag");
        if (!ProbeResult.unsupported().isOk()) {
            // unsupported is not ok
        }
        if (ProbeResult.fromRaw("7", false).isOk() != true) throw new AssertionError("isOk");
        System.out.println("All ProbeResult tests passed.");
    }
}
