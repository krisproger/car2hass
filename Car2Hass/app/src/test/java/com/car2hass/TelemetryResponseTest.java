package com.car2hass;

/** Plain-Java tests for HA/cloud response acceptance rules. */
public class TelemetryResponseTest {
    public static void main(String[] args) {
        testHaOk();
        testHaDropped();
        testHaInvalidProxy();
        testCloudAccepted();
        testCloudAcceptedZero();
        testCloudInvalid();
        System.out.println("All TelemetryResponse tests passed.");
    }

    private static void testHaOk() {
        HaResponse r = HaResponse.parse(200, "{\"status\":\"ok\",\"signals\":12,\"snapshots\":3}");
        check(r.ok(), "HA ok");
        check(!r.dropped, "HA not dropped");
        check(r.signals == 12 && r.snapshots == 3, "HA counts");
    }

    private static void testHaDropped() {
        HaResponse r = HaResponse.parse(200, "{\"status\":\"ok\",\"dropped\":\"out_of_order\"}");
        check(!r.ok(), "HA dropped not success");
        check(r.dropped, "HA dropped flag");
        check("out_of_order".equals(r.reason), "HA drop reason");
    }

    private static void testHaInvalidProxy() {
        HaResponse r = HaResponse.parse(200, "<html>login</html>");
        check(!r.ok(), "HTML 200 not success");
        check("invalid response".equals(r.reason), "HTML reason");
    }

    private static void testCloudAccepted() {
        CloudResponse r = CloudResponse.parse(200, "{\"ok\":true,\"accepted\":4}");
        check(r.ok && r.accepted == 4, "cloud accepted");
    }

    private static void testCloudAcceptedZero() {
        CloudResponse r = CloudResponse.parse(200, "{\"ok\":true,\"accepted\":0}");
        check(r.ok, "cloud 200 ok");
        check(r.accepted == 0, "cloud accepted zero reported");
    }

    private static void testCloudInvalid() {
        CloudResponse r = CloudResponse.parse(200, "not json");
        check(!r.ok, "cloud invalid not success");
    }

    private static void check(boolean v, String msg) {
        if (!v) throw new AssertionError(msg);
    }
}
