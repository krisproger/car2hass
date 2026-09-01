package com.car2hass;

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
        testMaskAuth();

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

    private static void testMaskAuth() {
        assertEquals(null, DiplusApi.maskAuth(null), "null url stays null");
        assertEquals("http://x/api?a=1", DiplusApi.maskAuth("http://x/api?a=1"), "no auth param unchanged");
        assertEquals("http://x/api?a=1&auth=***", DiplusApi.maskAuth("http://x/api?a=1&auth=tok"), "token masked");
        assertEquals("http://x/api?a=1&auth=***&b=2", DiplusApi.maskAuth("http://x/api?a=1&auth=tok&b=2"), "token masked, later params kept");
        assertEquals("http://x/api?auth=***", DiplusApi.maskAuth("http://x/api?auth=tok"), "first query param masked");
        // Encoded token contains no literal '&' (URLEncoder escapes it), so a
        // value like "a&b" still masks the whole value.
        assertEquals("http://x/api?auth=***", DiplusApi.maskAuth("http://x/api?auth=a%26b"), "encoded token masked fully");
    }

    private static void assertEquals(String expected, String actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }
}
