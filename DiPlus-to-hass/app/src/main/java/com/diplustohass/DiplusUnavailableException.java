package com.diplustohass;

/**
 * Thrown when DiPlus is unreachable or answers with a non-200 HTTP code.
 * Unlike a {@code null} result from readSingleSignal (which means "signal
 * genuinely unsupported"), this must NOT be cached as unsupported: DiPlus
 * being down is a transient condition, not a property of the signal.
 */
public class DiplusUnavailableException extends RuntimeException {
    public DiplusUnavailableException(String message) {
        super(message);
    }

    public DiplusUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}