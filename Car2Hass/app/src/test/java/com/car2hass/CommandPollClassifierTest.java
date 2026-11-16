package com.car2hass;

public final class CommandPollClassifierTest {
    public static void main(String[] args) {
        check(CommandPollClassifier.Kind.OK,
                CommandPollClassifier.classify(200, "{}"));
        check(CommandPollClassifier.Kind.INTEGRATION_NOT_LOADED,
                CommandPollClassifier.classify(500,
                        "{\"status\":\"error\",\"message\":\"integration not loaded\"}"));
        check(CommandPollClassifier.Kind.UNKNOWN_CAR_NAME,
                CommandPollClassifier.classify(404,
                        "{\"status\":\"error\",\"message\":\"unknown car_name: BYD\"}"));
        check(CommandPollClassifier.Kind.ENDPOINT_MISSING,
                CommandPollClassifier.classify(404, ""));
        check(CommandPollClassifier.Kind.AUTH_ERROR,
                CommandPollClassifier.classify(401, ""));
        check(CommandPollClassifier.Kind.TRANSIENT,
                CommandPollClassifier.classify(503, ""));
        check(CommandPollClassifier.retryDelayMs(
                CommandPollClassifier.Kind.INTEGRATION_NOT_LOADED) == 12_000L);
        check(CommandPollClassifier.retryDelayMs(
                CommandPollClassifier.Kind.UNKNOWN_CAR_NAME) == 30_000L);
        check(CommandPollClassifier.retryDelayMs(
                CommandPollClassifier.Kind.ENDPOINT_MISSING) == 300_000L);
        System.out.println("All CommandPollClassifier tests passed.");
    }
    private static void check(boolean ok) {
        if (!ok) throw new AssertionError();
    }
    private static void check(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("expected=" + expected + " actual=" + actual);
        }
    }
}
