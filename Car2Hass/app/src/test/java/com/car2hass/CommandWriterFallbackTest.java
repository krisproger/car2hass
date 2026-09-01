package com.car2hass;

import java.util.ArrayList;
import java.util.List;

/**
 * Plain-Java tests for {@link CommandWriter} auto-source ordering and the
 * native/DiPlus mutual fallback memory.
 *
 * <p>No Android runtime dependency: {@link NativeCommandMap} is fed the asset
 * JSON by relative path, {@link CommandRegistry} compiles against the test-only
 * {@link R} stub. Run from the project root (DiPlus-to-hass).
 */
public class CommandWriterFallbackTest {

    private static final String MAP_PATH = "app/src/main/assets/native_commands.json";

    public static void main(String[] args) throws Exception {
        NativeCommandMap.parse(TestUtil.loadMap(MAP_PATH));

        testAutoPrefersNativeAfterNativeOk();
        testAutoPrefersDiplusByDefault();
        testAutoFallsBackToOtherWhenPrimaryUnavailable();
        testAutoMemoryUpdatesAfterSuccess();
        testNativeOnlyPathWinsWhenDiplusValueInvalid();
        testDiplusOnlyPathWinsWhenNativeValueUnmapped();
        testSourceNativeStaysNativeFirst();
        testSourceDiplusStaysDiplusFirst();

        System.out.println("All CommandWriterFallback tests passed.");
    }

    private static void testAutoPrefersNativeAfterNativeOk() {
        ScriptedRunner runner = okRunner();
        CommandWriter w = writer("auto", true, runner, diplus(true), noLink());
        CommandWriter.Result r = w.dispatch("ac_temp", "24");
        assertTrue(r.success, "success expected");
        assertEquals(CommandWriter.IFACE_NATIVE, r.usedInterface, "native must be primary");
        assertEquals(0, r.fallbacks, "no fallback");
        assertTrue(runner.commands.size() >= 1, "native write attempted");
    }

    private static void testAutoPrefersDiplusByDefault() {
        CommandWriter w = writer("auto", false, okRunner(), diplus(true), noLink());
        CommandWriter.Result r = w.dispatch("ac_temp", "24");
        assertTrue(r.success, "success expected");
        assertEquals(CommandWriter.IFACE_DIPLUS, r.usedInterface, "diplus must be primary first");
        assertEquals(0, r.fallbacks, "no fallback");
    }

    private static void testAutoFallsBackToOtherWhenPrimaryUnavailable() {
        // DiPlus down but native healthy, with the auto memory still diplus-first.
        CommandWriter w = writer("auto", false, okRunner(), diplus(false), noLink());
        CommandWriter.Result r = w.dispatch("ac_temp", "24");
        assertTrue(r.success, "fallback success expected");
        assertEquals(CommandWriter.IFACE_NATIVE, r.usedInterface, "native fallback");
        assertEquals(1, r.fallbacks, "one fallback");
    }

    private static void testAutoMemoryUpdatesAfterSuccess() {
        // Start diplus-primary; native fallback flips the memory to native-first.
        CommandWriter w = writer("auto", false, okRunner(), diplus(false), noLink());
        CommandWriter.Result r1 = w.dispatch("ac_temp", "24");
        assertEquals(CommandWriter.IFACE_NATIVE, r1.usedInterface, "native fallback first run");

        // Same instance keeps its memory: native is now primary.
        ScriptedRunner runner = okRunner();
        CommandWriter w2 = new CommandWriter(
                new NativeCommandWriter(runner, "127.0.0.1", 5555),
                diplus(true), noLink(), testMessages(), "auto", true);
        CommandWriter.Result r2 = w2.dispatch("ac_temp", "24");
        assertEquals(CommandWriter.IFACE_NATIVE, r2.usedInterface, "native primary after memory");
    }

    private static void testNativeOnlyPathWinsWhenDiplusValueInvalid() {
        // ac_temp value 16 is outside the DiPlus range (17-30) but valid for the
        // native valueExpr (16-30) → the writer must fall through to native even
        // when the auto memory prefers DiPlus.
        CommandWriter w = writer("auto", false, okRunner(), diplus(true), noLink());
        CommandWriter.Result r = w.dispatch("ac_temp", "16");
        assertTrue(r.success, "success expected");
        assertEquals(CommandWriter.IFACE_NATIVE, r.usedInterface, "native-only path");
    }

    private static void testDiplusOnlyPathWinsWhenNativeValueUnmapped() {
        // sunroof value 75 is not in the native valueMap (0/50/100) but DiPlus
        // accepts any 0-100 → the writer must use DiPlus even when the auto
        // memory prefers native.
        CommandWriter w = writer("auto", true, okRunner(), diplus(true), noLink());
        CommandWriter.Result r = w.dispatch("sunroof", "75");
        assertTrue(r.success, "success expected");
        assertEquals(CommandWriter.IFACE_DIPLUS, r.usedInterface, "diplus-only path");
    }

    private static void testSourceNativeStaysNativeFirst() {
        CommandWriter w = writer("native", false, okRunner(), diplus(true), noLink());
        CommandWriter.Result r = w.dispatch("ac_temp", "24");
        assertEquals(CommandWriter.IFACE_NATIVE, r.usedInterface, "explicit native first");
        assertEquals(0, r.fallbacks, "no fallback");
    }

    private static void testSourceDiplusStaysDiplusFirst() {
        CommandWriter w = writer("diplus", true, okRunner(), diplus(true), noLink());
        CommandWriter.Result r = w.dispatch("ac_temp", "24");
        assertEquals(CommandWriter.IFACE_DIPLUS, r.usedInterface, "explicit diplus first");
        assertEquals(0, r.fallbacks, "no fallback");
    }

    // ---- test helpers ----

    private static CommandWriter writer(String source, boolean lastNativeWasOk,
                                        ScriptedRunner runner, DiplusSenderStub diplus,
                                        VerifierStub verifier) {
        NativeCommandWriter nativeWriter = new NativeCommandWriter(runner, "127.0.0.1", 5555);
        return new CommandWriter(nativeWriter, diplus, verifier, testMessages(),
                source, lastNativeWasOk);
    }

    private static ScriptedRunner okRunner() {
        return new ScriptedRunner("Result: Parcel(00000000 00000001   '....')\n");
    }

    private static DiplusSenderStub diplus(boolean ok) {
        return new DiplusSenderStub(ok);
    }

    private static VerifierStub noLink() {
        return new VerifierStub();
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
        @Override public CommandWriter.Verification verify(String commandId, String value) {
            return new CommandWriter.Verification(false, true, "no_link", null, null);
        }
    }

    private static void assertTrue(boolean cond, String what) {
        if (!cond) throw new AssertionError(what);
    }

    private static void assertEquals(Object expected, Object actual, String what) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(what + ": expected <" + expected + "> got <" + actual + ">");
        }
    }
}
