package com.car2hass.vehicle.obd;

/** One ELM327 connection; commands share the same underlying byte stream. */
public interface ObdSession extends AutoCloseable {

    String transact(String command, int expectedLines);

    /** Warm-up init (ATZ → ATE0/ATH0/ATL0/ATSP0 → ATI); returns ELM version or null. */
    String initWarmUp();

    @Override
    void close();
}