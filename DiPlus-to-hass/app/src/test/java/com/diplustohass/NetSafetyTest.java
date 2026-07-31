package com.diplustohass;

/**
 * Plain-Java unit tests for NetSafety.
 *
 * <p>These tests do not depend on the Android runtime and can be executed with
 * a standard JDK: compile both NetSafety.java and this class, then run the
 * main method.
 */
public class NetSafetyTest {

    public static void main(String[] args) {
        testLoopback();
        testRfc1918();
        testPublicHosts();
        testEdgeCases();

        System.out.println("All NetSafety tests passed.");
    }

    private static void testLoopback() {
        assertTrue(NetSafety.isPrivateHost("127.0.0.1"), "127.0.0.1");
        assertTrue(NetSafety.isPrivateHost("127.0.0.53"), "127.0.0.53");
        assertTrue(NetSafety.isPrivateHost("localhost"), "localhost");
        assertTrue(NetSafety.isPrivateHost("foo.localhost"), "foo.localhost");
        assertTrue(NetSafety.isPrivateHost("::1"), "::1");
        assertTrue(NetSafety.isPrivateHost("[::1]"), "[::1]");
    }

    private static void testRfc1918() {
        assertTrue(NetSafety.isPrivateHost("10.0.0.5"), "10.0.0.5");
        assertTrue(NetSafety.isPrivateHost("10.255.255.255"), "10.255.255.255");
        assertTrue(NetSafety.isPrivateHost("192.168.1.10"), "192.168.1.10");
        assertTrue(NetSafety.isPrivateHost("192.168.0.1"), "192.168.0.1");
        assertTrue(NetSafety.isPrivateHost("172.16.0.1"), "172.16.0.1");
        assertTrue(NetSafety.isPrivateHost("172.31.255.254"), "172.31.255.254");
        assertFalse(NetSafety.isPrivateHost("172.15.0.1"), "172.15.0.1 is public");
        assertFalse(NetSafety.isPrivateHost("172.32.0.1"), "172.32.0.1 is public");
    }

    private static void testPublicHosts() {
        assertFalse(NetSafety.isPrivateHost("8.8.8.8"), "8.8.8.8");
        assertFalse(NetSafety.isPrivateHost("1.1.1.1"), "1.1.1.1");
        assertFalse(NetSafety.isPrivateHost("example.com"), "example.com");
        assertFalse(NetSafety.isPrivateHost("teplitzky.ru"), "teplitzky.ru");
    }

    private static void testEdgeCases() {
        assertFalse(NetSafety.isPrivateHost(null), "null");
        assertFalse(NetSafety.isPrivateHost(""), "empty");
        assertFalse(NetSafety.isPrivateHost("   "), "blank");
        assertTrue(NetSafety.isPrivateHost("fe80::1"), "fe80::1 link-local");
        assertTrue(NetSafety.isPrivateHost("fd12:3456::1"), "fd12:3456::1 ULA");
        assertTrue(NetSafety.isPrivateHost("  192.168.1.1  "), "whitespace trimmed");
        assertTrue(NetSafety.isPrivateHost("LOCALHOST"), "case insensitive");
    }

    private static void assertTrue(boolean actual, String message) {
        if (!actual) {
            throw new AssertionError(message + " expected=true actual=false");
        }
    }

    private static void assertFalse(boolean actual, String message) {
        if (actual) {
            throw new AssertionError(message + " expected=false actual=true");
        }
    }
}
