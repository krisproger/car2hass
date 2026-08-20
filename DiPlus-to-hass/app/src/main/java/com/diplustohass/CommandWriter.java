package com.diplustohass;

import java.util.List;

/**
 * Orchestrates a single vehicle command across the native (autoservice) and
 * DiPlus HTTP interfaces with mutual fallback.
 *
 * <p>Every command goes through a chosen primary interface; when that interface
 * cannot apply or rejects the write, the other interface takes over. A command
 * is reported successful only when the write was accepted and the optional
 * linked sensor either confirmed the new state or was unavailable (no link). A
 * write that was accepted but not confirmed (verify mismatch) is kept as a
 * candidate while the other interface gets a chance to confirm it.
 *
 * <p>Dependencies (native writer, DiPlus sender, verifier, UI messages) are
 * injected so the core logic runs as plain Java unit tests; the production
 * wiring lives in {@link CommandExecutor}.
 */
public class CommandWriter {

    public static final String IFACE_NATIVE = "native";
    public static final String IFACE_DIPLUS = "diplus";

    private static final String SRC_AUTO = "auto";
    private static final String SRC_NATIVE = "native";
    private static final String SRC_DIPLUS = "diplus";

    private static final String TAG = "CommandWriter";

    public static class Result {
        public final boolean success;
        public final String response;
        public final String error;
        public final long elapsedMs;
        public final boolean verified;
        public final String verificationMessage;
        public final String expectedValue;
        public final String actualValue;
        /** Interface that produced the final outcome: "native", "diplus" or null. */
        public final String usedInterface;
        /** Number of interface attempts before the final one (0/1/2). */
        public final int fallbacks;

        public Result(boolean success, String response, String error, long elapsedMs,
                      boolean verified, String verificationMessage,
                      String expectedValue, String actualValue,
                      String usedInterface, int fallbacks) {
            this.success = success;
            this.response = response;
            this.error = error;
            this.elapsedMs = elapsedMs;
            this.verified = verified;
            this.verificationMessage = verificationMessage;
            this.expectedValue = expectedValue;
            this.actualValue = actualValue;
            this.usedInterface = usedInterface;
            this.fallbacks = fallbacks;
        }
    }

    /** Outcome of a sensor check; {@code noLink} marks an absent/unreadable sensor. */
    public static class Verification {
        public final boolean verified;
        public final boolean noLink;
        public final String message;
        public final String expectedValue;
        public final String actualValue;

        public Verification(boolean verified, boolean noLink, String message,
                            String expectedValue, String actualValue) {
            this.verified = verified;
            this.noLink = noLink;
            this.message = message;
            this.expectedValue = expectedValue;
            this.actualValue = actualValue;
        }
    }

    /** DiPlus send result. Mirrors {@link DiPlusCommandSender.Result} fields without the Android dep. */
    public static class SendResult {
        public final boolean success;
        public final String response;
        public final String error;

        public SendResult(boolean success, String response, String error) {
            this.success = success;
            this.response = response;
            this.error = error;
        }
    }

    /** Sends one Chinese command to DiPlus. */
    public interface DiplusSender {
        SendResult send(String chinese);
    }

    /** Checks a command against its linked sensor. */
    public interface Verifier {
        Verification verify(String commandId, String value);
    }

    /** User-facing messages, resolved via Context in production. */
    public interface Messages {
        String unknownCommand(String commandId, String value);
        String verifyNoLink();
        String commandsResultOk();
        String commandsResultFail();
    }

    private static final class Attempt {
        final boolean accepted;
        final String response;
        final String error;
        final String reason; // "unavailable", "rejected" or null
        final boolean verified;
        final boolean noLink;
        final String verifyMessage;
        final String expected;
        final String actual;
        String iface;

        Attempt(boolean accepted, String response, String error, String reason,
                boolean verified, boolean noLink, String verifyMessage,
                String expected, String actual) {
            this.accepted = accepted;
            this.response = response;
            this.error = error;
            this.reason = reason;
            this.verified = verified;
            this.noLink = noLink;
            this.verifyMessage = verifyMessage;
            this.expected = expected;
            this.actual = actual;
        }
    }

    private final NativeCommandWriter nativeWriter;
    private final DiplusSender diplusSender;
    private final Verifier verifier;
    private final Messages messages;
    private final String telemetrySource;
    private boolean lastNativeWasOk;

    public CommandWriter(NativeCommandWriter nativeWriter, DiplusSender diplusSender,
                         Verifier verifier, Messages messages,
                         String telemetrySource, boolean lastNativeWasOk) {
        this.nativeWriter = nativeWriter;
        this.diplusSender = diplusSender;
        this.verifier = verifier;
        this.messages = messages;
        this.telemetrySource = telemetrySource != null ? telemetrySource : SRC_AUTO;
        this.lastNativeWasOk = lastNativeWasOk;
    }

    /**
     * Executes one command with mutual native/DiPlus fallback.
     *
     * @return the final outcome, including which interface was used and how many
     *         interfaces were tried before it
     */
    public Result dispatch(String commandId, String value) {
        long start = System.currentTimeMillis();

        boolean nativeOk = hasNativePath(commandId, value);
        String chinese = CommandRegistry.buildCommand(commandId, value);
        boolean diplusOk = chinese != null && !chinese.isEmpty();

        if (!nativeOk && !diplusOk) {
            String msg = messages.unknownCommand(commandId, value);
            LogBuffer.w(TAG, "No interface supports " + commandId + " value=" + value);
            return new Result(false, null, msg, System.currentTimeMillis() - start,
                    false, messages.verifyNoLink(), null, null, null, 0);
        }

        String[] order = interfaceOrder(nativeOk, diplusOk);

        Attempt acceptedCandidate = null;
        Attempt lastFailed = null;
        int fallbacks = 0;
        for (String iface : order) {
            Attempt attempt = tryInterface(iface, commandId, value, chinese);
            if (attempt == null) {
                continue;
            }
            attempt.iface = iface;

            if (attempt.accepted) {
                if (attempt.verified || attempt.noLink) {
                    lastNativeWasOk = IFACE_NATIVE.equals(iface);
                    return new Result(true, attempt.response, attempt.error,
                            System.currentTimeMillis() - start,
                            attempt.verified, attempt.verifyMessage,
                            attempt.expected, attempt.actual, iface, fallbacks);
                }
                if (acceptedCandidate == null) {
                    acceptedCandidate = attempt;
                }
                fallbacks++;
                continue;
            }

            lastFailed = attempt;
            fallbacks++;
        }

        // A write was accepted but no interface could confirm it — report the
        // accepted write so the user is not shown a false failure.
        if (acceptedCandidate != null) {
            lastNativeWasOk = false;
            return new Result(true, acceptedCandidate.response, acceptedCandidate.error,
                    System.currentTimeMillis() - start,
                    false, acceptedCandidate.verifyMessage,
                    acceptedCandidate.expected, acceptedCandidate.actual,
                    acceptedCandidate.iface, fallbacks);
        }

        // Every interface failed to apply the command.
        lastNativeWasOk = false;
        String error = lastFailed != null && lastFailed.error != null
                ? lastFailed.error : messages.commandsResultFail();
        String response = lastFailed != null ? lastFailed.response : null;
        return new Result(false, response, error, System.currentTimeMillis() - start,
                false, messages.verifyNoLink(), null, null, null, fallbacks);
    }

    /** Orders interfaces by telemetry source and the auto-mode memory. */
    private String[] interfaceOrder(boolean nativeOk, boolean diplusOk) {
        String primary;
        if (SRC_DIPLUS.equals(telemetrySource)) {
            primary = diplusOk ? IFACE_DIPLUS : IFACE_NATIVE;
        } else if (SRC_NATIVE.equals(telemetrySource)) {
            primary = nativeOk ? IFACE_NATIVE : IFACE_DIPLUS;
        } else {
            primary = lastNativeWasOk ? IFACE_NATIVE : IFACE_DIPLUS;
            if (IFACE_NATIVE.equals(primary) && !nativeOk) {
                primary = IFACE_DIPLUS;
            }
            if (IFACE_DIPLUS.equals(primary) && !diplusOk) {
                primary = IFACE_NATIVE;
            }
        }
        String fallback = IFACE_NATIVE.equals(primary) ? IFACE_DIPLUS : IFACE_NATIVE;
        boolean fallbackOk = IFACE_NATIVE.equals(fallback) ? nativeOk : diplusOk;
        if (fallbackOk) {
            return new String[]{primary, fallback};
        }
        return new String[]{primary};
    }

    private Attempt tryInterface(String iface, String commandId, String value, String chinese) {
        if (IFACE_NATIVE.equals(iface)) {
            return tryNative(commandId, value);
        }
        return tryDiplus(chinese, commandId, value);
    }

    private Attempt tryNative(String commandId, String value) {
        List<NativeCommandMap.WriteOp> ops = NativeCommandMap.resolve(commandId, value);
        if (ops == null || ops.isEmpty()) {
            return null;
        }
        for (NativeCommandMap.WriteOp op : ops) {
            NativeCommandWriter.Result wr = nativeWriter.write(op.device, op.fid, op.value);
            if (!wr.success) {
                String reason = wr.status == -999 ? "unavailable" : "rejected";
                LogBuffer.i(TAG, "native write failed for " + commandId + ": "
                        + reason + " " + wr.error);
                return new Attempt(false, null, wr.error, reason,
                        false, false, null, null, null);
            }
        }
        Verification v = verifier.verify(commandId, value);
        return new Attempt(true, null, messages.commandsResultOk(), null,
                v.verified, v.noLink, v.message, v.expectedValue, v.actualValue);
    }

    private Attempt tryDiplus(String chinese, String commandId, String value) {
        if (chinese == null || chinese.isEmpty()) {
            return null;
        }
        SendResult sr = diplusSender.send(chinese);
        if (!sr.success) {
            String reason = sr.error != null ? "unavailable" : "rejected";
            String error = sr.error != null ? sr.error : messages.commandsResultFail();
            LogBuffer.i(TAG, "diplus send failed for " + commandId + ": " + reason);
            return new Attempt(false, sr.response, error, reason,
                    false, false, null, null, null);
        }
        Verification v = verifier.verify(commandId, value);
        return new Attempt(true, sr.response, messages.commandsResultOk(), null,
                v.verified, v.noLink, v.message, v.expectedValue, v.actualValue);
    }

    private static boolean hasNativePath(String commandId, String value) {
        if (!NativeCommandMap.hasNative(commandId)) {
            return false;
        }
        List<NativeCommandMap.WriteOp> ops = NativeCommandMap.resolve(commandId, value);
        return ops != null && !ops.isEmpty();
    }
}
