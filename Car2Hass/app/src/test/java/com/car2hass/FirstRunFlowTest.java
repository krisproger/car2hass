package com.car2hass;

public final class FirstRunFlowTest {
    public static void main(String[] args) {
        FirstRunFlow f = new FirstRunFlow();
        check(!f.begin("3.5.6-beta.3", "3.5.6-beta.3"), "same version");
        check(!f.takePendingAutoStart(), "no pending after same");
        check(f.begin("3.5.6-beta.3", "3.5.6-beta.2"), "new version");
        check(f.takePendingAutoStart(), "pending once");
        check(!f.takePendingAutoStart(), "pending consumed");
        check(!f.begin(null, ""), "null version");
        System.out.println("All FirstRunFlow tests passed.");
    }

    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
    }
}
