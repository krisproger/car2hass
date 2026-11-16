package com.car2hass;

public final class HaAddressTest {
    public static void main(String[] args) {
        check("http://192.168.1.10",
                HaAddress.parse("192.168.1.10", false).baseUrl());
        check("http://192.168.1.10:8123",
                HaAddress.parse("192.168.1.10:8123", false).baseUrl());
        check("https://abc.ui.nabu.casa",
                HaAddress.parse("abc.ui.nabu.casa", true).baseUrl());
        check("https://abc.ui.nabu.casa",
                HaAddress.parse("https://abc.ui.nabu.casa", false).baseUrl());
        check("https://ha.example.com:8443",
                HaAddress.parse("ha.example.com:8443", true).baseUrl());
        check("http://[fd00::1]:8123",
                HaAddress.parse("[fd00::1]:8123", false).baseUrl());
        check("abc.ui.nabu.casa",
                HaAddress.parse("https://abc.ui.nabu.casa/lovelace?x=1", true)
                        .canonicalAddress());
        check("ha.example.com:8443",
                HaAddress.parse("https://ha.example.com:8443/path", false)
                        .canonicalAddress());
        check("https://ha.example.com:8443/api/cartelemetry",
                HaAddress.parse("ha.example.com:8443", true)
                        .url("/api/cartelemetry"));
        expectFail("https://");
        expectFail("host:abc");
        expectFail("host:70000");
        System.out.println("All HaAddress tests passed.");
    }

    private static void check(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("expected=" + expected + " actual=" + actual);
        }
    }

    private static void expectFail(String input) {
        try {
            HaAddress.parse(input, false);
            throw new AssertionError("expected failure: " + input);
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
