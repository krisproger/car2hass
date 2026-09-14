package com.car2hass;

import android.content.Context;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Serial command queue with a single processing thread. Every command entry
 * point (HA polling, UI, rules) enqueues here instead of executing inline, so
 * vehicle commands never overlap and the UI/poller threads are never blocked
 * by a write+verify round-trip.
 */
public final class CommandQueue {

    /** Called on the processing thread when the command has completed. */
    public interface Callback {
        void onResult(CommandExecutor.Result result);
    }

    private static final BlockingQueue<Task> QUEUE = new LinkedBlockingQueue<>();
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private static final ExecutorService PROCESSOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CommandProcessor");
        t.setDaemon(true);
        return t;
    });

    private static final class Task {
        final Context ctx;
        final String commandId;
        final String value;
        final CommandExecutor.Source source;
        final Callback callback;

        Task(Context c, String id, String v, CommandExecutor.Source s, Callback cb) {
            ctx = c.getApplicationContext();
            commandId = id;
            value = v;
            source = s;
            callback = cb;
        }
    }

    private CommandQueue() {}

    /** Enqueue a command for serial execution. The callback fires after completion. */
    public static void enqueue(Context ctx, String commandId, String value,
                               CommandExecutor.Source source, Callback callback) {
        if (ctx == null || commandId == null) return;
        QUEUE.offer(new Task(ctx, commandId, value, source, callback));
        if (STARTED.compareAndSet(false, true)) {
            PROCESSOR.execute(CommandQueue::run);
        }
    }

    private static void run() {
        while (true) {
            try {
                Task t = QUEUE.take();
                CommandExecutor.Result r = CommandExecutor.execute(t.ctx, t.commandId, t.value, t.source);
                if (t.callback != null) {
                    try {
                        t.callback.onResult(r);
                    } catch (Exception e) {
                        LogBuffer.e("CommandQueue", "callback failed: " + e.getMessage());
                    }
                }
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                LogBuffer.e("CommandQueue", "processor error: " + e.getMessage());
            }
        }
    }
}
