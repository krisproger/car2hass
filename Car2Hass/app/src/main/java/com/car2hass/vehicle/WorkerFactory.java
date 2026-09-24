package com.car2hass.vehicle;

/** Creates a worker instance for a channel id; returns null when the id has no worker type. */
public interface WorkerFactory {
    ChannelWorker create(String channelId);
}
