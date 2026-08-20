package com.diplustohass;

public class DiplusErrorClassifierTest {
    public static void main(String[] args) {
        // connect errors
        check(DiplusErrorClassifier.isConnectError("Failed to connect to /127.0.0.1:8988"), "Failed to connect");
        check(DiplusErrorClassifier.isConnectError("Connection refused"), "Connection refused");
        check(DiplusErrorClassifier.isConnectError("connect timed out"), "timeout");
        check(DiplusErrorClassifier.isConnectError("UnknownHostException: x"), "UnknownHost");
        check(DiplusErrorClassifier.isConnectError(null) == false, "null message");
        check(DiplusErrorClassifier.isConnectError("Connection reset") == false, "reset must NOT be treated as connect error");

        // unsupported vs unavailable
        check(DiplusErrorClassifier.isUnsupportedResponse(200, "{\"success\":false}"), "success:false body");
        check(DiplusErrorClassifier.isUnsupportedResponse(200, "{\"success\":true,\"val\":\"1\"}") == false, "success:true body");
        check(DiplusErrorClassifier.isUnsupportedResponse(200, null) == false, "null body");
        check(DiplusErrorClassifier.isUnsupportedResponse(500, "{\"success\":false}") == false, "non-200 with success:false is server error, not unsupported");
        check(DiplusErrorClassifier.isNonOkHttp(200) == false, "200 is ok");
        check(DiplusErrorClassifier.isNonOkHttp(404), "404 is non-ok");

        System.out.println("All DiplusErrorClassifier tests passed.");
    }

    private static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }
}