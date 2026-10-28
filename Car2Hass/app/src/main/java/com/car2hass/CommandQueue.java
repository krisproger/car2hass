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
        // Audit window commands with the caller stack: a "disabled rule fired"
        // report must be provable from the device log alone. The stack identifies
        // UI preset, HA poller, RuleEngine or any unexpected worker.
        if (commandId.startsWith("window") || "windows_close_all".equals(commandId)
                || "windows_vent".equals(commandId)) {
            LogBuffer.i("CommandQueue", "enqueue [" + source + "] " + commandId
                    + "=" + value + " thread=" + Thread.currentThread().getName()
                    + " stack=" + callerStack());
        }
        QUEUE.offer(new Task(ctx, commandId, value, source, callback));
        if (STARTED.compareAndSet(false, true)) {
            PROCESSOR.execute(CommandQueue::run);
        }
    }

    /** Short caller stack (4 frames) for the window-command audit. */
    private static String callerStack() {
        StackTraceElement[] st = new Throwable().getStackTrace();
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (StackTraceElement e : st) {
            String c = e.getClassName();
            if (c.equals(CommandQueue.class.getName())) continue;
            if (sb.length() > 0) sb.append(" <- ");
            sb.append(c.substring(c.lastIndexOf('.') + 1))
              .append('.').append(e.getMethodName())
              .append(':').append(e.getLineNumber());
            if (++n >= 4) break;
        }
        return sb.toString();
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
