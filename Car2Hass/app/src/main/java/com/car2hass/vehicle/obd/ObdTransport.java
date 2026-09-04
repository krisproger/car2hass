package com.car2hass.vehicle.obd;

/** ELM327 transport; {@link #open()} yields a session bound to one connection. */
public interface ObdTransport {

    /** Connect and return a session; throws on connection failure. */
    ObdSession open() throws Exception;

    /** Human-readable target (host:port or device name) for error messages. */
    String describe();
}