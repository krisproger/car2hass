package com.car2hass.vehicle.obd;

/** ELM327 transport; {@link #open()} yields a session bound to one connection. */
public interface ObdTransport {

    /** Connect and return a session; throws on connection failure. */
    ObdSession open() throws Exception;

    /**
     * First-connect / manual re-bind: transports may override this to do a
     * mandatory re-init (retry, cancel discovery, settle delay). Defaults to
     * a plain {@link #open()}.
     */
    default ObdSession openForced() throws Exception {
        return open();
    }

    /** Human-readable target (host:port or device name) for error messages. */
    String describe();
}