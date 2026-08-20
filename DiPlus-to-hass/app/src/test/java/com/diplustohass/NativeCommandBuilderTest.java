package com.diplustohass;

import java.util.ArrayList;
import java.util.List;

/**
 * Plain-Java unit tests for NativeCommandBuilder.
 *
 * <p>No Android runtime dependency: compile NativeCommandBuilder.java (+
 * NativeSignalMap.java, ParamDecoder.java, SentinelDecoder.java) and this class.
 */
public class NativeCommandBuilderTest {

    public static void main(String[] args) {
        testBuildTwoEntries();
        testBuildEmpty();
        testMarkerOrder();
        testNegativeFid();

        System.out.println("All NativeCommandBuilder tests passed.");
    }

    private static void testBuildTwoEntries() {
        List<NativeSignalMap.FidEntry> entries = new ArrayList<>();
        entries.add(NativeSignalMap.get("soc"));
        entries.add(NativeSignalMap.get("range"));
        String cmd = NativeCommandBuilder.build(entries);
        assertContains(cmd, "echo \"@soc\"; service call autoservice 7 i32 1014 i32 1246777400", "soc tx7");
        assertContains(cmd, "echo \"@range\"; service call autoservice 5 i32 1014 i32 1246765072", "range tx5");
        if (!cmd.contains("; ")) {
            throw new AssertionError("entries must be joined with '; ', got: " + cmd);
        }
    }

    private static void testBuildEmpty() {
        String cmd = NativeCommandBuilder.build(new ArrayList<NativeSignalMap.FidEntry>());
        if (cmd != null) {
            throw new AssertionError("empty entries must produce null, got: " + cmd);
        }
        if (NativeCommandBuilder.build(null) != null) {
            throw new AssertionError("null entries must produce null");
        }
    }

    private static void testMarkerOrder() {
        List<NativeSignalMap.FidEntry> entries = new ArrayList<>();
        entries.add(NativeSignalMap.get("bonnet"));
        entries.add(NativeSignalMap.get("speed"));
        String cmd = NativeCommandBuilder.build(entries);
        int trunk = cmd.indexOf("@bonnet");
        int speed = cmd.indexOf("@speed");
        if (trunk == -1 || speed == -1 || trunk > speed) {
            throw new AssertionError("markers must keep entry order: " + cmd);
        }
    }

    private static void testNegativeFid() {
        List<NativeSignalMap.FidEntry> entries = new ArrayList<>();
        entries.add(NativeSignalMap.get("speed"));
        String cmd = NativeCommandBuilder.build(entries);
        assertContains(cmd, "i32 -1807745016", "speed negative fid");
    }

    private static void assertContains(String haystack, String needle, String what) {
        if (!haystack.contains(needle)) {
            throw new AssertionError(what + ": expected to contain '" + needle + "', got: " + haystack);
        }
    }
}
