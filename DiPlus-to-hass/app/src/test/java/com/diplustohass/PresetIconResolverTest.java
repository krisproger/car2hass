package com.diplustohass;

import java.io.File;

/**
 * Plain-Java unit tests for {@link PresetIconResolver} name handling.
 *
 * <p>Only the pure path/sanitisation helpers are exercised here (no Android
 * runtime calls), so this test runs on a standard JDK with android.jar on the
 * classpath for the resolver's Bitmap/Context references.
 */
public class PresetIconResolverTest {

    public static void main(String[] args) throws Exception {
        testSanitizeAcceptsValidNames();
        testSanitizeRejectsBadNames();
        testAssetPath();
        testOverrideFile();
        testOverrideFileNullGuards();
        testOverrideResultCached();

        System.out.println("All PresetIconResolver tests passed.");
    }

    private static void testSanitizeAcceptsValidNames() {
        assertEquals("air-conditioner",
                PresetIconResolver.sanitizeIconName("air-conditioner"), "kebab name kept");
        assertEquals("air-conditioner",
                PresetIconResolver.sanitizeIconName("  Air-Conditioner  "), "trimmed + lowercased");
        assertEquals("window_open",
                PresetIconResolver.sanitizeIconName("window_open"), "underscore allowed");
        assertEquals("icon2",
                PresetIconResolver.sanitizeIconName("icon2"), "digits allowed");
    }

    private static void testSanitizeRejectsBadNames() {
        assertEquals(null, PresetIconResolver.sanitizeIconName(null), "null rejected");
        assertEquals(null, PresetIconResolver.sanitizeIconName(""), "empty rejected");
        assertEquals(null, PresetIconResolver.sanitizeIconName("   "), "blank rejected");
        assertEquals(null, PresetIconResolver.sanitizeIconName("../evil"), "path traversal rejected");
        assertEquals(null, PresetIconResolver.sanitizeIconName("a/b"), "slash rejected");
        assertEquals(null, PresetIconResolver.sanitizeIconName("a.png"), "dot rejected");
        assertEquals(null, PresetIconResolver.sanitizeIconName("🚪"), "emoji rejected");
    }

    private static void testAssetPath() {
        assertEquals("icons/lock.png", PresetIconResolver.assetPathFor("lock"), "asset path");
    }

    private static void testOverrideFile() {
        File f = PresetIconResolver.overrideFileFor(new File("/data/files"), "lock");
        assertEquals(new File("/data/files/icons/lock.png"), f, "override path");
    }

    private static void testOverrideFileNullGuards() {
        assertEquals(null, PresetIconResolver.overrideFileFor(null, "lock"), "null filesDir");
        assertEquals(null, PresetIconResolver.overrideFileFor(new File("/data/files"), null),
                "null name");
    }

    private static void testOverrideResultCached() {
        assertTrue(PresetIconResolver.overrideResultCached(100L, 100L, null), "fresh bitmap");
        assertTrue(PresetIconResolver.overrideResultCached(100L, null, 100L), "fresh broken");
        assertTrue(PresetIconResolver.overrideResultCached(100L, 100L, 50L),
                "bitmap wins over stale broken marker");
        assertFalse(PresetIconResolver.overrideResultCached(100L, 99L, null), "stale bitmap");
        assertFalse(PresetIconResolver.overrideResultCached(100L, null, 99L), "stale broken");
        assertFalse(PresetIconResolver.overrideResultCached(100L, null, null), "nothing cached");
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void assertFalse(boolean value, String message) {
        if (value) throw new AssertionError(message);
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }
}
