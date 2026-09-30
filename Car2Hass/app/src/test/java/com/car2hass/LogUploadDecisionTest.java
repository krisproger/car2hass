package com.car2hass;

import java.util.Arrays;
import java.util.List;

public class LogUploadDecisionTest {
    public static void main(String[] args) {
        LogRecord a = new LogRecord(1L, "I", "T", repeat('a', 1000), "s");
        LogRecord b = new LogRecord(2L, "I", "T", repeat('b', 1000), "s");
        List<LogRecord> all = Arrays.asList(a, b);

        List<LogRecord> one = LogUploadPolicy.selectForUpload(all, LogUploadPolicy.approxBytes(a) + 10);
        if (one.size() != 1 || one.get(0).ts != 1L) throw new AssertionError("expected only the oldest record");

        List<LogRecord> both = LogUploadPolicy.selectForUpload(all, 10_000_000L);
        if (both.size() != 2) throw new AssertionError("a large budget must fit both records");

        List<LogRecord> tiny = LogUploadPolicy.selectForUpload(all, 1L);
        if (tiny.size() != 1) throw new AssertionError("must always keep at least one record");

        System.out.println("All LogUploadDecision tests passed.");
    }

    private static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(c);
        return sb.toString();
    }
}
