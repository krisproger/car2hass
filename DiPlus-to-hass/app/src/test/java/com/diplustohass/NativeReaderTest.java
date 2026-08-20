package com.diplustohass;

import java.util.ArrayList;
import java.util.List;

/**
 * Plain-Java unit tests for NativeReader with a stubbed ShellRunner.
 *
 * <p>No Android runtime dependency: compile the native package classes (+
 * CANDataItem, LogBuffer stub) and this class, run main.
 */
public class NativeReaderTest {

    public static void main(String[] args) {
        testReadAllUpdatesItems();
        testExecFailureLeavesItemsUntouched();
        testSentinelSkipped();
        testWindowRRGen3Fallback();
        testTurnSignalDerived();
        testFormatting();

        System.out.println("All NativeReader tests passed.");
    }

    /** A batch that returns soc=50.0f and range=182345 (→18234.5). */
    private static void testReadAllUpdatesItems() {
        String output = "@soc\n"
                + "Result: Parcel(00000000 42480000)\n"   // 50.0f
                + "@range\n"
                + "Result: Parcel(00000000 0002C849)\n";  // 182345
        List<CANDataItem> items = itemsWith("soc", "range");
        new NativeReader((host, port, cmd) -> output, "127.0.0.1", 5555, false).readAll(items);

        CANDataItem soc = byKey(items, "soc");
        CANDataItem range = byKey(items, "range");
        assertEquals("50", soc.value, "soc 50.0 -> 50");
        assertEquals("18234.5", range.value, "range scaled 18234.5");
        if (soc.lastUpdate == 0 || range.lastUpdate == 0) {
            throw new AssertionError("lastUpdate must be set");
        }
    }

    /** A null/errored ADB exec must leave every item untouched. */
    private static void testExecFailureLeavesItemsUntouched() {
        List<CANDataItem> items = itemsWith("soc", "gear");
        new NativeReader((host, port, cmd) -> null, "127.0.0.1", 5555, false).readAll(items);
        assertEquals("---", byKey(items, "soc").value, "soc untouched");
        assertEquals("---", byKey(items, "gear").value, "gear untouched");
    }

    /** Sentinel values must be skipped (no value update). */
    private static void testSentinelSkipped() {
        String output = "@soc\n"
                + "Result: Parcel(00000000 BF800000)\n"   // -1.0f float sentinel
                + "@gear\n"
                + "Result: Parcel(00000000 0000FFFF)\n";  // FEATURE_LINK_ERROR
        List<CANDataItem> items = itemsWith("soc", "gear");
        new NativeReader((host, port, cmd) -> output, "127.0.0.1", 5555, false).readAll(items);
        assertEquals("---", byKey(items, "soc").value, "float sentinel soc untouched");
        assertEquals("---", byKey(items, "gear").value, "int sentinel gear untouched");
    }

    /** window_rr missing but window_rr_gen3 present → fallback used. */
    private static void testWindowRRGen3Fallback() {
        String output = "@window_rr\n"
                + "Result: Parcel(00000000 0000FFFF)\n"   // primary link error
                + "@window_rr_gen3\n"
                + "Result: Parcel(00000000 0000002D)\n";  // 45%
        List<CANDataItem> items = itemsWith("window_rr");
        new NativeReader((host, port, cmd) -> output, "127.0.0.1", 5555, false).readAll(items);
        assertEquals("45", byKey(items, "window_rr").value, "gen3 fallback 45%");
    }

    /** turn_signal mask 6 → left_turn=1, right_turn=1, hazard=2. */
    private static void testTurnSignalDerived() {
        String output = "@turn_signal\n"
                + "Result: Parcel(00000000 00000006)\n";
        List<CANDataItem> items = itemsWith("left_turn", "right_turn", "hazard");
        new NativeReader((host, port, cmd) -> output, "127.0.0.1", 5555, false).readAll(items);
        assertEquals("1", byKey(items, "left_turn").value, "left_turn on");
        assertEquals("1", byKey(items, "right_turn").value, "right_turn on");
        assertEquals("2", byKey(items, "hazard").value, "hazard on");
    }

    private static void testFormatting() {
        List<CANDataItem> items = itemsWith("soc", "battery_voltage");
        String output = "@soc\n"
                + "Result: Parcel(00000000 42480000)\n"          // 50.0
                + "@battery_voltage\n"
                + "Result: Parcel(00000000 4141999A)\n";         // 12.1
        new NativeReader((host, port, cmd) -> output, "127.0.0.1", 5555, false).readAll(items);
        assertEquals("50", byKey(items, "soc").value, "integer value no .0");
        assertEquals("12.1", byKey(items, "battery_voltage").value, "1-decimal float");
    }

    private static List<CANDataItem> itemsWith(String... keys) {
        List<CANDataItem> out = new ArrayList<>();
        for (String key : keys) {
            CANDataItem item = new CANDataItem(0, key, "", 0);
            item.key = key;
            item.rawData = "num";
            out.add(item);
        }
        return out;
    }

    private static CANDataItem byKey(List<CANDataItem> items, String key) {
        for (CANDataItem item : items) {
            if (key.equals(item.key)) {
                return item;
            }
        }
        throw new AssertionError("no item with key " + key);
    }

    private static void assertEquals(String expected, String actual, String what) {
        if (!expected.equals(actual)) {
            throw new AssertionError(what + ": expected '" + expected + "', got '" + actual + "'");
        }
    }
}
