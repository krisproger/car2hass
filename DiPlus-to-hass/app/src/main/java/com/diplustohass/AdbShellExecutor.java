package com.diplustohass;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import com.tananaev.adblib.AdbBase64;
import com.tananaev.adblib.AdbAuthenticationFailedException;
import com.tananaev.adblib.AdbConnection;
import com.tananaev.adblib.AdbCrypto;
import com.tananaev.adblib.AdbStream;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Executes shell commands on an ADB daemon using the vendored adblib implementation.
 *
 * <p>All network and cryptography work is performed on a dedicated background thread.
 * All callbacks are delivered on the Android main thread.</p>
 */
public class AdbShellExecutor {

    private static final String TAG = "AdbShellExecutor";
    private static final String KEYS_DIR = "adb_keys";
    private static final String PRIVATE_KEY_NAME = "adb_key";
    private static final String PUBLIC_KEY_NAME = "adb_key.pub";

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int OVERALL_TIMEOUT_MS = 30000;
    private static final int MAX_OUTPUT_LOG_CHARS = 400;

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final ScheduledExecutorService watchdogExecutor = Executors.newSingleThreadScheduledExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final AdbBase64 ADB_BASE64 = data -> Base64.encodeToString(data, Base64.NO_WRAP);

    private static volatile Context appContext;

    public interface AdbShellCallback {
        void onSuccess(String output);
        void onError(String output, Exception e);
        void onFailure(String reason);
    }

    /**
     * Initializes the executor with an application context so that ADB keys can be
     * loaded or generated. Call once at process start (e.g. from {@code Application#onCreate})
     * before any {@link #executeSync} use — the sync overload has no context of its own.
     */
    public static void init(Context ctx) {
        if (ctx == null) {
            LogBuffer.e(TAG, "init: Context is null");
            return;
        }
        if (appContext == null) {
            appContext = ctx.getApplicationContext();
            LogBuffer.i(TAG, "Initialized with application context");
        }
    }

    /**
     * Executes a shell command on the ADB daemon configured in {@link AppConfig}.
     *
     * @param ctx      application context, used to read host/port and manage ADB keys
     * @param command  the shell command to execute
     * @param callback callback invoked on the main thread with the result
     */
    public static void execute(Context ctx, String command, AdbShellCallback callback) {
        if (ctx == null) {
            LogBuffer.e(TAG, "Context is null");
            callbackOnMain(callback, () -> callback.onFailure("Context is null"));
            return;
        }
        if (appContext == null) {
            appContext = ctx.getApplicationContext();
        }

        String host = AppConfig.getAdbHost(ctx);
        int port = AppConfig.getAdbPort(ctx);
        if (host == null || host.trim().isEmpty()) {
            LogBuffer.e(TAG, "No ADB host configured");
            callbackOnMain(callback, () -> callback.onFailure("No ADB host configured"));
            return;
        }

        execute(host, port, command, callback);
    }

    /**
     * Executes a shell command on the specified ADB daemon.
     *
     * <p>This overload requires that the executor has previously been initialized
     * with a {@link Context} (via {@link #execute(Context, String, AdbShellCallback)})
     * so that the ADB RSA key pair can be loaded or generated.</p>
     *
     * @param host     ADB daemon host
     * @param port     ADB daemon port
     * @param command  the shell command to execute
     * @param callback callback invoked on the main thread with the result
     */
    public static void execute(String host, int port, String command, AdbShellCallback callback) {
        if (host == null) {
            LogBuffer.e(TAG, "Host is null");
            callbackOnMain(callback, () -> callback.onFailure("Host is null"));
            return;
        }
        final String trimmedHost = host.trim();
        if (trimmedHost.isEmpty() || trimmedHost.indexOf('\n') >= 0 || trimmedHost.indexOf('\r') >= 0) {
            LogBuffer.e(TAG, "Host is empty or contains newline");
            callbackOnMain(callback, () -> callback.onFailure("Host is empty or contains newline"));
            return;
        }
        if (command == null) {
            LogBuffer.e(TAG, "Command is null");
            callbackOnMain(callback, () -> callback.onFailure("Command is null"));
            return;
        }
        if (port < 1 || port > 65535) {
            LogBuffer.e(TAG, "Invalid ADB port: " + port);
            callbackOnMain(callback, () -> callback.onFailure("Invalid ADB port: " + port));
            return;
        }
        if (callback == null) {
            LogBuffer.e(TAG, "Callback is null; command will not be reported");
            return;
        }

        LogBuffer.i(TAG, "Scheduling ADB shell execution to " + trimmedHost + ":" + port);
        final String finalCommand = command;
        executor.execute(() -> runShell(trimmedHost, port, finalCommand, callback));
    }

    /**
     * Executes a shell command on the specified ADB daemon and blocks until the
     * result arrives or {@code OVERALL_TIMEOUT_MS + CONNECT_TIMEOUT_MS} elapses.
     *
     * <p>Must be called from a background thread (never the main thread — the
     * callback is delivered on the main looper, so a main-thread caller would
     * deadlock). Returns the full output on success, or null when the command
     * failed, timed out, or reported an error via the callback.
     *
     * @param host     ADB daemon host
     * @param port     ADB daemon port
     * @param command  the shell command to execute
     */
    public static String executeSync(String host, int port, String command) {
        return executeSync(host, port, command, OVERALL_TIMEOUT_MS + CONNECT_TIMEOUT_MS);
    }

    /**
     * Same as {@link #executeSync(String, int, String)} with an explicit wait
     * timeout for the result.
     */
    public static String executeSync(String host, int port, String command, long waitMs) {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> result = new AtomicReference<>(null);
        execute(host, port, command, new AdbShellCallback() {
            @Override
            public void onSuccess(String output) {
                result.set(output);
                latch.countDown();
            }

            @Override
            public void onError(String output, Exception e) {
                latch.countDown();
            }

            @Override
            public void onFailure(String reason) {
                latch.countDown();
            }
        });
        return NativeSyncGate.await(latch, result, waitMs);
    }

    private static void runShell(String host, int port, String command, AdbShellCallback callback) {
        Socket socket = null;
        AdbConnection connection = null;
        AdbStream stream = null;
        StringBuilder output = new StringBuilder();
        ScheduledFuture<?> watchdog = null;
        AtomicBoolean timedOut = new AtomicBoolean(false);

        try {
            if (appContext == null) {
                LogBuffer.e(TAG, "AdbShellExecutor not initialized with a Context");
                callbackOnMain(callback, () -> callback.onFailure("AdbShellExecutor not initialized with a Context"));
                return;
            }

            LogBuffer.i(TAG, "ADB command: " + command);
            LogBuffer.i(TAG, "Connecting to ADB daemon at " + host + ":" + port);
            socket = new Socket();
            final Socket watchdogSocket = socket;
            watchdog = watchdogExecutor.schedule(() -> {
                timedOut.set(true);
                LogBuffer.w(TAG, "ADB shell execution timed out after " + OVERALL_TIMEOUT_MS + " ms, forcing socket close");
                try {
                    watchdogSocket.close();
                } catch (Exception ignored) {
                }
            }, OVERALL_TIMEOUT_MS, TimeUnit.MILLISECONDS);

            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            // Infinite read timeout: the one-shot shell stream closes itself when the
            // command exits. The overall watchdog handles truly stuck commands.
            socket.setSoTimeout(0);
            LogBuffer.i(TAG, "TCP socket connected to " + host + ":" + port);

            AdbCrypto crypto = loadOrGenerateCrypto();
            connection = AdbConnection.create(socket, crypto);

            LogBuffer.i(TAG, "Starting ADB authentication");
            connection.connect();
            LogBuffer.i(TAG, "ADB authentication succeeded");

            LogBuffer.i(TAG, "Opening one-shot shell stream");
            try {
                stream = connection.open("shell:" + command);
            } catch (ConnectException e) {
                LogBuffer.e(TAG, "Shell stream rejected: " + e.getMessage());
                callbackOnMain(callback, () -> callback.onFailure(
                        "ADB daemon rejected the shell stream"));
                return;
            }

            LogBuffer.i(TAG, "Reading shell output");
            while (!stream.isClosed()) {
                try {
                    byte[] data = stream.read();
                    if (data != null) {
                        output.append(new String(data, StandardCharsets.UTF_8));
                    }
                } catch (IOException e) {
                    if (stream.isClosed()) {
                        break;
                    }
                    throw e;
                }
            }

            String result = output.toString();
            String preview = result.length() <= MAX_OUTPUT_LOG_CHARS
                    ? result.trim()
                    : result.substring(0, MAX_OUTPUT_LOG_CHARS).trim() + "...";
            LogBuffer.i(TAG, "Shell output received: " + result.length() + " chars"
                    + (preview.isEmpty() ? "" : " | " + preview.replace("\n", " ")));
            final String finalOutput = result;
            callbackOnMain(callback, () -> callback.onSuccess(finalOutput));

        } catch (AdbAuthenticationFailedException e) {
            LogBuffer.e(TAG, "ADB authentication failed: " + e.getMessage());
            callbackOnMain(callback, () -> callback.onFailure(
                    "ADB authentication failed: device rejected the RSA key. " +
                    "Accept the \"Allow USB debugging?\" / \"Allow ADB debugging?\" prompt on the head unit, " +
                    "or authorize this client on the target device."));
        } catch (SocketException e) {
            LogBuffer.e(TAG, "Connection refused to ADB daemon: " + e.getMessage());
            callbackOnMain(callback, () -> callback.onFailure(
                    "Connection refused to ADB daemon at " + host + ":" + port));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LogBuffer.e(TAG, "Interrupted during ADB shell execution: " + e.getMessage());
            final String partialOutput = output.toString();
            callbackOnMain(callback, () -> callback.onError(partialOutput, e));
        } catch (Exception e) {
            if (timedOut.get()) {
                LogBuffer.e(TAG, "ADB shell execution timed out");
                callbackOnMain(callback, () -> callback.onFailure(
                        "ADB shell execution timed out after " + OVERALL_TIMEOUT_MS + " ms"));
            } else {
                LogBuffer.e(TAG, "ADB shell execution failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                final String partialOutput = output.toString();
                callbackOnMain(callback, () -> callback.onError(partialOutput, e));
            }
        } finally {
            if (watchdog != null) {
                watchdog.cancel(false);
            }
            closeQuietly(stream);
            closeQuietly(connection);
            closeQuietly(socket);
            LogBuffer.i(TAG, "ADB shell execution finished and connection closed");
        }
    }

    private static AdbCrypto loadOrGenerateCrypto() throws Exception {
        File keysDir = new File(appContext.getFilesDir(), KEYS_DIR);
        if (!keysDir.exists() && !keysDir.mkdirs()) {
            throw new IOException("Failed to create keys directory: " + keysDir.getAbsolutePath());
        }

        File privateKey = new File(keysDir, PRIVATE_KEY_NAME);
        File publicKey = new File(keysDir, PUBLIC_KEY_NAME);

        if (privateKey.exists() && publicKey.exists()) {
            LogBuffer.i(TAG, "Loading existing ADB key pair from " + keysDir.getAbsolutePath());
            try {
                return AdbCrypto.loadAdbKeyPair(ADB_BASE64, privateKey, publicKey);
            } catch (Exception e) {
                LogBuffer.e(TAG, "Failed to load ADB key pair, deleting and regenerating: " + e.getMessage());
                if (!privateKey.delete()) {
                    LogBuffer.w(TAG, "Could not delete corrupted private key");
                }
                if (!publicKey.delete()) {
                    LogBuffer.w(TAG, "Could not delete corrupted public key");
                }
            }
        }

        LogBuffer.i(TAG, "Generating new ADB RSA key pair");
        AdbCrypto crypto = AdbCrypto.generateAdbKeyPair(ADB_BASE64);
        crypto.saveAdbKeyPair(privateKey, publicKey);
        LogBuffer.i(TAG, "ADB key pair saved to " + keysDir.getAbsolutePath());
        return crypto;
    }

    private static void callbackOnMain(AdbShellCallback callback, Runnable action) {
        if (callback == null) {
            return;
        }
        mainHandler.post(action);
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
        }
    }
}
