package com.diplustohass;

/**
 * Plain-Java unit tests for DiplusApi.
 *
 * <p>These tests do not depend on the Android runtime and can be executed with
 * a standard JDK: compile both DiplusApi.java and this class, then run the
 * main method.
 */
public class DiplusApiTest {

    public static void main(String[] args) {
        testNoAuth();
        testWithAuth();
        testEncoding();

        System.out.println("All DiplusApi tests passed.");
    }

    private static void testNoAuth() {
        assertEquals("http://x/api?a=1", DiplusApi.withAuth("http://x/api?a=1", null), "null auth leaves URL unchanged");
        assertEquals("http://x/api?a=1", DiplusApi.withAuth("http://x/api?a=1", ""), "empty auth leaves URL unchanged");
        assertEquals("http://x/api?a=1", DiplusApi.withAuth("http://x/api?a=1", "   "), "blank auth leaves URL unchanged");
    }

    private static void testWithAuth() {
        assertEquals("http://x/api?a=1&auth=tok", DiplusApi.withAuth("http://x/api?a=1", "tok"), "token appended");
        assertEquals("http://x/api?a=1&auth=tok", DiplusApi.withAuth("http://x/api?a=1", " tok "), "token trimmed");
        assertEquals(null, DiplusApi.withAuth(null, "tok"), "null url stays null");
    }

    private static void testEncoding() {
        assertEquals("http://x/api?a=1&auth=a%26b%3Dc", DiplusApi.withAuth("http://x/api?a=1", "a&b=c"), "special chars url-encoded");
        assertEquals("http://x/api?a=1&auth=%D1%82%D0%BE%D0%BA", DiplusApi.withAuth("http://x/api?a=1", "ток"), "cyrillic url-encoded");
    }

    private static void assertEquals(String expected, String actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }
}
