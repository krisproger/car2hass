package com.car2hass.vehicle.obd;

/** One ELM327 command exchange; implementations own the connection lifecycle. */
public interface ObdTransport {

    /** Sends {@code command} and returns the raw adapter response, null on failure. */
    String transact(String command, int expectedLines);

    /** Human-readable target (host:port or device name) for error messages. */
    String describe();
}