package com.car2hass;

import org.json.JSONObject;

/**
 * Plain-Java unit tests for {@link GeofenceZone} JSON persistence.
 *
 * <p>GeofenceZone only depends on org.json, so these tests run on a standard
 * JDK: compile GeofenceZone.java and this class against the json jar, then
 * run the main method.
 */
public class GeofenceZoneTest {

    public static void main(String[] args) throws Exception {
        testDefaults();
        testRoundTrip();
        testRoundTripPreservesLastVisited();
        testFromJsonWithoutLastVisited();
        testToJsonContainsLastVisited();

        System.out.println("All GeofenceZone tests passed.");
    }

    private static void testDefaults() {
        GeofenceZone z = new GeofenceZone("home", 55.75, 37.61, 150);
        assertEquals(0L, z.lastVisitedAtMs, "lastVisitedAtMs defaults to 0");
        assertTrue(z.id != null && !z.id.isEmpty(), "id generated");
    }

    private static void testRoundTrip() {
        GeofenceZone z = new GeofenceZone("work", 55.75, 37.61, 250);
        GeofenceZone copy = GeofenceZone.fromJson(z.toJson());
        assertEquals(z.id, copy.id, "id round-trip");
        assertEquals(z.name, copy.name, "name round-trip");
        assertEquals(z.latitude, copy.latitude, "latitude round-trip");
        assertEquals(z.longitude, copy.longitude, "longitude round-trip");
        assertEquals(z.radius, copy.radius, "radius round-trip");
    }

    private static void testRoundTripPreservesLastVisited() {
        GeofenceZone z = new GeofenceZone("gym", 55.75, 37.61, 100);
        z.lastVisitedAtMs = 1721728800000L;
        GeofenceZone copy = GeofenceZone.fromJson(z.toJson());
        assertEquals(1721728800000L, copy.lastVisitedAtMs, "lastVisitedAtMs round-trip");
    }

    private static void testFromJsonWithoutLastVisited() throws Exception {
        // Legacy zones persisted before lastVisitedAtMs existed must load
        // with a 0 (never visited) timestamp.
        JSONObject o = new JSONObject();
        o.put("id", "abc12345");
        o.put("name", "dacha");
        o.put("latitude", 55.0);
        o.put("longitude", 37.0);
        o.put("radius", 300);
        GeofenceZone z = GeofenceZone.fromJson(o);
        assertEquals(0L, z.lastVisitedAtMs, "missing lastVisitedAtMs loads as 0");
        assertEquals("abc12345", z.id, "legacy id preserved");
    }

    private static void testToJsonContainsLastVisited() {
        GeofenceZone z = new GeofenceZone("school", 55.75, 37.61, 100);
        z.lastVisitedAtMs = 42L;
        assertEquals(42L, z.toJson().optLong("lastVisitedAtMs", -1), "toJson writes lastVisitedAtMs");
    }

    private static void assertTrue(boolean actual, String message) {
        if (!actual) {
            throw new AssertionError(message + " expected=true actual=false");
        }
    }

    private static void assertEquals(long expected, long actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertEquals(float expected, float actual, String message) {
        if (Float.compare(expected, actual) != 0) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertEquals(double expected, double actual, String message) {
        if (Double.compare(expected, actual) != 0) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }
}
