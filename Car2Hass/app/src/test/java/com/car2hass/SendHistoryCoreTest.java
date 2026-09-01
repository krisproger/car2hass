package com.car2hass;

public class SendHistoryCoreTest {

    public static void main(String[] args) throws Exception {
        long now = 10_000_000_000L;
        String h = "";
        h = SendHistoryCore.append(h, now - 60_000, 1000);          // 1 min ago
        h = SendHistoryCore.append(h, now - 10 * 60_000, 2000);     // 10 min ago
        h = SendHistoryCore.append(h, now - 3 * 3600_000, 99999);   // 3h ago — вне окна
        if (SendHistoryCore.computeReference(h, now) != 3000) {
            throw new AssertionError("computeReference expected 3000, got "
                    + SendHistoryCore.computeReference(h, now));
        }
        if (SendHistoryCore.append("not-json", now, 5).length() == 0) {
            throw new AssertionError("append must survive broken input");
        }
        if (SendHistoryCore.computeReference("garbage", now) != 0) {
            throw new AssertionError("computeReference on garbage must be 0");
        }
        if (SendHistoryCore.computeReference(null, now) != 0) {
            throw new AssertionError("computeReference(null) must be 0");
        }
        System.out.println("All SendHistoryCore tests passed.");
    }
}