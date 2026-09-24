package com.car2hass.vehicle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChannelWorkerRegistryTest {

    static final class FakeWorker implements ChannelWorker {
        final String id;
        boolean running;
        int starts;
        int stops;
        FakeWorker(String id) { this.id = id; }
        @Override public String channelId() { return id; }
        @Override public void start() { running = true; starts++; }
        @Override public void stop() { running = false; stops++; }
        @Override public boolean isRunning() { return running; }
    }

    static final class FakeFactory implements WorkerFactory {
        final List<String> created = new ArrayList<>();
        final Set<String> unsupported;
        FakeFactory(String... unsupported) {
            this.unsupported = new HashSet<>(Arrays.asList(unsupported));
        }
        @Override public ChannelWorker create(String channelId) {
            created.add(channelId);
            if (unsupported.contains(channelId)) return null;
            return new FakeWorker(channelId);
        }
    }

    public static void main(String[] args) {
        FakeFactory factory = new FakeFactory("byd_cloud");
        ChannelWorkerRegistry reg = new ChannelWorkerRegistry(factory);

        reg.sync(Arrays.asList("system", "diplus", "obd"));
        if (!reg.runningChannels().equals(new HashSet<>(Arrays.asList("system", "diplus", "obd")))) {
            throw new AssertionError("expected three workers, got " + reg.runningChannels());
        }
        if (factory.created.size() != 3) throw new AssertionError("factory create count " + factory.created);

        // Re-syncing the same set must not recreate anything.
        reg.sync(Arrays.asList("system", "diplus", "obd"));
        if (factory.created.size() != 3) throw new AssertionError("duplicate creation " + factory.created);

        // Disabling a channel stops and removes its worker only.
        reg.sync(Arrays.asList("system", "obd"));
        if (reg.isRunning("diplus")) throw new AssertionError("disabled channel still running");
        if (!reg.isRunning("system") || !reg.isRunning("obd")) throw new AssertionError("enabled channels stopped");
        if (factory.created.size() != 3) throw new AssertionError("unexpected recreate " + factory.created);

        // A channel with no worker type is skipped, never registered.
        reg.sync(Arrays.asList("system", "byd_cloud"));
        if (reg.isRunning("byd_cloud")) throw new AssertionError("unknown channel registered");
        if (factory.created.size() != 4) throw new AssertionError("unknown channel not attempted " + factory.created);

        // Empty set disables everything.
        reg.sync(new ArrayList<>());
        if (!reg.runningChannels().isEmpty()) throw new AssertionError("not all stopped " + reg.runningChannels());

        // stopAll clears the registry and stops every worker.
        reg.sync(Arrays.asList("system", "diplus"));
        reg.stopAll();
        if (!reg.runningChannels().isEmpty()) throw new AssertionError("stopAll left workers");
        if (reg.isRunning("system")) throw new AssertionError("stopAll left system running");

        System.out.println("All ChannelWorkerRegistry tests passed.");
    }
}
