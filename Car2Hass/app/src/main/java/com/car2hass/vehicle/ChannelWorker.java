package com.car2hass.vehicle;

/**
 * A running worker instance for exactly one channel. The concrete types are
 * {@link PullChannelWorker} (generic DataChannel-backed pull), {@link SystemWorker}
 * and {@link ObdWorker}; instances are created per channel by
 * {@link ChannelWorkerFactory} and managed by {@link ChannelWorkerRegistry}.
 *
 * <p>Each worker owns its own thread and cadence and writes every produced
 * value into the shared {@link ValueStore}, tagged with its channel id.
 */
public interface ChannelWorker {
    String channelId();
    void start();
    void stop();
    boolean isRunning();
}
