package com.car2hass;

public final class HaCommandUrlsTest {
    public static void main(String[] args) {
        HaEndpointConfig e = new HaEndpointConfig(
                "primary", "Home", "abc.ui.nabu.casa",
                true, "BYDSongPlusEV", true, true);
        check("https://abc.ui.nabu.casa/api/cartelemetry/commands?car_name=BYDSongPlusEV",
                HaCommandUrls.pollUrl(e));
        check("https://abc.ui.nabu.casa/api/cartelemetry/commands",
                HaCommandUrls.ackUrl(e));
        System.out.println("All HaCommandUrls tests passed.");
    }
    private static void check(String expected, String actual) {
        if (!expected.equals(actual)) throw new AssertionError(
                "expected=" + expected + " actual=" + actual);
    }
}
