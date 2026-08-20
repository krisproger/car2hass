package com.diplustohass;

/**
 * Plain-Java unit tests for NativeCommandWriter.
 *
 * <p>No Android runtime dependency: compile NativeCommandWriter.java (+
 * LogBuffer stub) and this class, run main.
 */
public class NativeCommandWriterTest {

    public static void main(String[] args) {
        testBuildCommand();
        testStatusOk();
        testStatusRejected();
        testStatusError();
        testNoParcelData();
        testNullOutputUnavailable();
        testEmptyOutputUnavailable();

        System.out.println("All NativeCommandWriter tests passed.");
    }

    private static void testBuildCommand() {
        String cmd = NativeCommandWriter.buildCommand(1000, 501219364, 1);
        if (!cmd.equals("service call autoservice 6 i32 1000 i32 501219364 i32 1")) {
            throw new AssertionError("unexpected command: " + cmd);
        }
    }

    private static void testStatusOk() {
        String output = "Result: Parcel(00000000 00000001   '....')\n";
        NativeCommandWriter.Result r = run(output, 1000, 501219364, 1);
        if (!r.success || r.status != 1) {
            throw new AssertionError("status 1 must succeed, got " + r.status + " success=" + r.success);
        }
    }

    private static void testStatusRejected() {
        // status 0 = no-op: write accepted but not a real action → fallback.
        NativeCommandWriter.Result r = run("Result: Parcel(00000000 00000000   '....')\n", 1000, 501219364, 1);
        if (r.success || r.status != 0) {
            throw new AssertionError("status 0 must be rejected, got " + r.status + " success=" + r.success);
        }
    }

    private static void testStatusError() {
        // Negative error codes (e.g. -10011) appear as two's-complement.
        NativeCommandWriter.Result r = run("Result: Parcel(00000000 FFFFD8E5)\n", 1000, 501219364, 1);
        if (r.success || r.status != -10011) {
            throw new AssertionError("expected status -10011, got " + r.status + " success=" + r.success);
        }
    }

    private static void testNoParcelData() {
        NativeCommandWriter.Result r = run("Result: Parcel()\n", 1000, 501219364, 1);
        if (r.success || r.status != -999) {
            throw new AssertionError("no parcel data must be -999, got " + r.status);
        }
    }

    private static void testNullOutputUnavailable() {
        NativeCommandWriter w = new NativeCommandWriter((h, p, c) -> null, "127.0.0.1", 5555);
        NativeCommandWriter.Result r = w.write(1000, 501219364, 1);
        if (r.success || r.status != -999) {
            throw new AssertionError("null output must be unavailable, got " + r.status + " success=" + r.success);
        }
    }

    private static void testEmptyOutputUnavailable() {
        NativeCommandWriter.Result r = run("", 1000, 501219364, 1);
        if (r.success || r.status != -999) {
            throw new AssertionError("empty output must be unavailable, got " + r.status);
        }
    }

    private static NativeCommandWriter.Result run(String output, int dev, int fid, int value) {
        NativeCommandWriter w = new NativeCommandWriter((h, p, c) -> output, "127.0.0.1", 5555);
        return w.write(dev, fid, value);
    }
}
