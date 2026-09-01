package com.car2hass;

public class UpdateCheckerTest {
    public static void main(String[] args) {
        testIsNewer();
        testParseResponse();
        System.out.println("All UpdateChecker tests passed.");
    }

    private static void testIsNewer() {
        check(UpdateChecker.isNewer("2.5.0", "2.4.0"), "minor bump");
        check(UpdateChecker.isNewer("3.0.0", "2.9.9"), "major bump");
        check(UpdateChecker.isNewer("2.4.1", "2.4.0"), "patch bump");
        check(!UpdateChecker.isNewer("2.4.0", "2.4.0"), "equal is not newer");
        check(!UpdateChecker.isNewer("2.3.9", "2.4.0"), "older remote");
        check(UpdateChecker.isNewer("v2.5.0", "2.4.0"), "v-prefix tolerated");
        check(UpdateChecker.isNewer("2.5", "2.4.3"), "short remote padded with zeros");
        check(UpdateChecker.isNewer("2.5.0-beta", "2.4.9"), "suffix stripped");
    }

    private static void testParseResponse() {
        String json = "{\"ok\":true,\"version\":\"2.5.0\","
                + "\"apk_url\":\"https://mytechnic.ru/cartelemetry/download.php?file=app\","
                + "\"whats_new\":{\"ru\":\"что нового\",\"en\":\"what's new\"}}";
        UpdateChecker.UpdateInfo info = UpdateChecker.parseResponse(json);
        check(info != null, "parsed");
        if (!"2.5.0".equals(info.version)) throw new AssertionError("version=" + info.version);
        if (info.notes("ru").isEmpty() || info.notes("en").isEmpty()) throw new AssertionError("notes lost");
        if (!"что нового".equals(info.notes("ru"))) throw new AssertionError("ru notes=" + info.notes("ru"));
        // Missing language falls back to the other one.
        if (!"what's new".equals(info.notes("de"))) throw new AssertionError("fallback notes");

        // No release published / bad payloads -> null or empty version.
        if (UpdateChecker.parseResponse("{\"ok\":true,\"version\":null}") != null)
            throw new AssertionError("null version must yield null");
        if (UpdateChecker.parseResponse("{\"ok\":false}") != null)
            throw new AssertionError("ok=false must yield null");
        if (UpdateChecker.parseResponse("not json") != null)
            throw new AssertionError("garbage must yield null");
    }

    private static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }
}
