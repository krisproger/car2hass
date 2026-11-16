package com.car2hass;

import java.util.Arrays;

public final class EndpointRouterTest {
    public static void main(String[] args) {
        HaEndpointConfig on = new HaEndpointConfig(
                "a", "A", "a.local:8123", false, "Car", true, false);
        HaEndpointConfig off = new HaEndpointConfig(
                "b", "B", "b.local:8123", false, "Car", false, true);
        check(Arrays.asList("a").equals(
                EndpointRouter.enabledIds(Arrays.asList(on, off))));
        System.out.println("All EndpointRouter tests passed.");
    }
    private static void check(boolean ok) {
        if (!ok) throw new AssertionError();
    }
}
