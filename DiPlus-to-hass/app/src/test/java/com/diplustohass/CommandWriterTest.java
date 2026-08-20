package com.diplustohass;

import java.util.ArrayList;
import java.util.List;

/**
 * Plain-Java tests for {@link CommandWriter} core dispatch logic (success,
 * fallback, verify handling, fan-out).
 *
 * <p>No Android runtime dependency: {@link NativeCommandMap} is fed the asset
 * JSON by relative path, {@link CommandRegistry} compiles against the test-only
 * {@link R} stub. Run from the project root (DiPlus-to-hass).
 */
public class CommandWriterTest {

    private static final String MAP_PATH = "app/src/main/assets/native_commands.json";

    public static void main(String[] args) throws Exception {
        NativeCommandMap.parse(TestUtil.loadMap(MAP_PATH));

        testNativePrimarySuccess();
        testDiplusPrimarySuccess();
        testNativeFallbackToDiplus();
        testDiplusFallbackToNative();
        testNoLinkIsSuccess();
        testVerifyMismatchFallsBack();
        testBothInterfacesFail();
        testUnknownCommand();
        testFanOutAllWritesSucceed();

        System.out.println("All CommandWriter tests passed.");
    }

    /** Native runner that replies with status 1 for every write. */
    private static ScriptedRunner okRunner() {
        return new ScriptedRunner("Result: Parcel(00000000 00000001   '....')\n");
    }

    private static void testNativePrimarySuccess() {
        CommandWriter w = writer("native", false, okRunner(), diplus(true), verified());
        CommandWriter.Result r = w.dispatch("ac_temp", "24");
        assertTrue(r.success, "native success expected");
        assertEquals(CommandWriter.IFACE_NATIVE, r.usedInterface, "usedInterface");
        assertEquals(0, r.fallbacks, "fallbacks");
        assertTrue(r.verified, "verified");
    }

    private static void testDiplusPrimarySuccess() {
        CommandWriter w = writer("diplus", false, okRunner(), diplus(true), verified());
        CommandWriter.Result r = w.dispatch("ac_temp", "24");
        assertTrue(r.success, "diplus success expected");
        assertEquals(CommandWriter.IFACE_DIPLUS, r.usedInterface, "usedInterface");
        assertEquals(0, r.fallbacks, "fallbacks");
    }

    private static void testNativeFallbackToDiplus() {
        // Native rejects (status 0) → DiPlus takes over and succeeds.
        CommandWriter w = writer("native", false,
            new ScriptedRunner("Result: Parcel(00000000 00000000   '....')\n"),
            diplus(true), verified());
        CommandWriter.Result r = w.dispatch("ac_temp", "24");
        assertTrue(r.success, "fallback success expected");
        assertEquals(CommandWriter.IFACE_DIPLUS, r.usedInterface, "usedInterface");
        assertEquals(1, r.fallbacks, "fallbacks");
    }

    private static void testDiplusFallbackToNative() {
        // DiPlus unavailable → native takes over.
        CommandWriter w = writer("diplus", false, okRunner(), diplus(false), verified());
        CommandWriter.Result r = w.dispatch("ac_temp", "24");
        assertTrue(r.success, "fallback success expected");
        assertEquals(CommandWriter.IFACE_NATIVE, r.usedInterface, "usedInterface");
        assertEquals(1, r.fallbacks, "fallbacks");
    }

    private static void testNoLinkIsSuccess() {
        // No verify sensor → an accepted write is a success (verified=false).
        CommandWriter w = writer("native", false, okRunner(), diplus(true), noLink());
        CommandWriter.Result r = w.dispatch("ac_on", "");
        assertTrue(r.success, "no-link write must be success");
        assertEquals(CommandWriter.IFACE_NATIVE, r.usedInterface, "usedInterface");
        assertFalse(r.verified, "verified must be false without a link");
    }

    private static void testVerifyMismatchFallsBack() {
        // Native accepts the write but the sensor does not confirm → DiPlus gets
        // a chance; both confirm-mismatch → the accepted write is still reported
        // as success (verified=false), not as a false failure.
        CommandWriter w = writer("native", false, okRunner(), diplus(true), mismatch());
        CommandWriter.Result r = w.dispatch("ac_on", "");
        assertTrue(r.success, "accepted-but-unconfirmed must be success");
        assertEquals(CommandWriter.IFACE_NATIVE, r.usedInterface, "usedInterface");
        assertFalse(r.verified, "verified must be false on mismatch");
        assertEquals(2, r.fallbacks, "fallbacks");
    }

    private static void testBothInterfacesFail() {
        ScriptedRunner nativeRunner = new ScriptedRunner(null);
        CommandWriter w = writer("native", false, nativeRunner, diplus(false), noLink());
        CommandWriter.Result r = w.dispatch("ac_temp", "24");
        assertFalse(r.success, "both interfaces down must fail");
        assertEquals(null, r.usedInterface, "usedInterface");
        assertEquals(2, r.fallbacks, "fallbacks");
    }

    private static void testUnknownCommand() {
        CommandWriter w = writer("auto", false, okRunner(), diplus(true), noLink());
        CommandWriter.Result r = w.dispatch("no_such_command", "24");
        assertFalse(r.success, "unknown command must fail");
        assertEquals(0, r.fallbacks, "fallbacks");
    }

    private static void testFanOutAllWritesSucceed() {
        // windows_close_all is a fan-out of 4 writes; all must be applied.
        ScriptedRunner runner = okRunner();
        CommandWriter w = writer("native", false, runner, diplus(true), noLink());
        CommandWriter.Result r = w.dispatch("windows_close_all", "");
        assertTrue(r.success, "fan-out success expected");
        assertEquals(CommandWriter.IFACE_NATIVE, r.usedInterface, "usedInterface");
        assertEquals(4, runner.commands.size(), "four writes expected");
    }

    // ---- test helpers ----

    private static CommandWriter writer(String source, boolean lastNativeWasOk,
                                        ScriptedRunner runner, DiplusSenderStub diplus,
                                        VerifierStub verifier) {
        NativeCommandWriter nativeWriter = new NativeCommandWriter(runner, "127.0.0.1", 5555);
        return new CommandWriter(nativeWriter, diplus, verifier, testMessages(),
                source, lastNativeWasOk);
    }

    private static DiplusSenderStub diplus(boolean ok) {
        return new DiplusSenderStub(ok);
    }

    private static VerifierStub verified() {
        return new VerifierStub(true, "verified", "expected", "actual");
    }

    private static VerifierStub noLink() {
        return new VerifierStub(false, "no_link", null, null);
    }

    private static VerifierStub mismatch() {
        return new VerifierStub(false, "mismatch", "expected", "actual");
    }

    private static CommandWriter.Messages testMessages() {
        return new CommandWriter.Messages() {
            @Override public String unknownCommand(String commandId, String value) {
                return "unknown " + commandId + " " + value;
            }
            @Override public String verifyNoLink() { return "no_link"; }
            @Override public String commandsResultOk() { return "ok"; }
            @Override public String commandsResultFail() { return "fail"; }
        };
    }

    private static final class ScriptedRunner implements NativeReader.ShellRunner {
        final String output;
        final List<String> commands = new ArrayList<>();
        ScriptedRunner(String output) { this.output = output; }
        @Override public String run(String host, int port, String command) {
            commands.add(command);
            return output;
        }
    }

    private static final class DiplusSenderStub implements CommandWriter.DiplusSender {
        final boolean ok;
        DiplusSenderStub(boolean ok) { this.ok = ok; }
        @Override public CommandWriter.SendResult send(String chinese) {
            return new CommandWriter.SendResult(ok, ok ? "{\"success\":true}" : null,
                    ok ? null : "connection refused");
        }
    }

    private static final class VerifierStub implements CommandWriter.Verifier {
        final boolean verified;
        final String message;
        final String expected;
        final String actual;
        VerifierStub(boolean verified, String message, String expected, String actual) {
            this.verified = verified;
            this.message = message;
            this.expected = expected;
            this.actual = actual;
        }
        @Override public CommandWriter.Verification verify(String commandId, String value) {
            boolean noLink = "no_link".equals(message);
            return new CommandWriter.Verification(verified, noLink, message, expected, actual);
        }
    }

    private static void assertTrue(boolean cond, String what) {
        if (!cond) throw new AssertionError(what);
    }

    private static void assertFalse(boolean cond, String what) {
        if (cond) throw new AssertionError(what);
    }

    private static void assertEquals(Object expected, Object actual, String what) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(what + ": expected <" + expected + "> got <" + actual + ">");
        }
    }
}
