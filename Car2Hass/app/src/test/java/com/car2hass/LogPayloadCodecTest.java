package com.car2hass;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Plain-Java tests for the upload payload: preamble, tags and the wire timestamp unit. */
public class LogPayloadCodecTest {

    public static void main(String[] args) {
        testPreambleAndRuleEngineRoundTrip();
        testWireTimestampIsSaneSeconds();
        testTimestampToSecondsAcceptsBothUnits();

        System.out.println("All LogPayloadCodec tests passed.");
    }

    /** Header + diagnostics markers and a RuleEngine sample must survive into the payload. */
    private static void testPreambleAndRuleEngineRoundTrip() {
        List<LogRecord> logs = new ArrayList<>();
        logs.add(new LogRecord(1_790_427_824_000L, "I", "RuleEngine", "Engine started, 1s evaluation loop", "app"));
        logs.add(new LogRecord(1_790_427_824_100L, "D", "TelemetryService", "Notification updated", "app"));
        List<CrashRecord> crashes = new ArrayList<>();

        String preamble = LogPreamble.header("3.4.0", "Brand=BYD, Model=Song", "Sun Sep 27")
                + LogPreamble.diagnosticsBlock("Profile: byd", "RuleEngine: 2 rule(s)");
        String json = LogPayloadCodec.buildJson(logs, crashes, preamble);

        require(json.contains("=== Car2Hass v3.4.0 Log ==="), "header marker in payload");
        require(json.contains("--- Diagnostics ---"), "diagnostics marker in payload");
        require(json.contains("RuleEngine: 2 rule(s)"), "diagnostics RuleEngine line in payload");
        require(json.contains("\"tag\":\"RuleEngine\""), "RuleEngine record present in payload");
        require(json.contains("Engine started"), "RuleEngine sample message present in payload");
    }

    /** eps: the DB stores milliseconds; the wire must carry seconds so the server date is sane. */
    private static void testWireTimestampIsSaneSeconds() {
        String json = LogPayloadCodec.buildJson(
                Collections.singletonList(new LogRecord(1_790_427_824_000L, "I", "t", "m", "app")),
                Collections.emptyList(), "");
        long ts = firstRecordTs(json);
        if (ts != 1_790_427_824L) {
            throw new AssertionError("wire ts must be epoch seconds, got " + ts);
        }
        int year = ZonedDateTime.ofInstant(Instant.ofEpochSecond(ts), ZoneOffset.UTC).getYear();
        if (year < 2020 || year > 2100) {
            throw new AssertionError("wire ts round-trips to an insane year " + year);
        }
    }

    private static void testTimestampToSecondsAcceptsBothUnits() {
        if (LogTimestamp.toSeconds(1_700_000_000_000L) != 1_700_000_000L) {
            throw new AssertionError("milliseconds must be divided to seconds");
        }
        if (LogTimestamp.toSeconds(1_700_000_000L) != 1_700_000_000L) {
            throw new AssertionError("already-seconds value must be kept");
        }
    }

    private static long firstRecordTs(String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONArray records = root.getJSONArray("records");
            return records.getJSONObject(0).getLong("ts");
        } catch (Exception e) {
            throw new AssertionError("cannot read records[0].ts: " + e.getMessage());
        }
    }

    private static void require(boolean condition, String what) {
        if (!condition) throw new AssertionError("missing " + what);
    }
}
