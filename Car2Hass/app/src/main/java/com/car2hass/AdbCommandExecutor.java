package com.car2hass;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdbCommandExecutor {

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface AdbCallback {
        void onSuccess(String output);
        void onError(int exitCode, String errorOutput);
        void onException(Exception e);
    }

    public static void execute(String command, AdbCallback callback) {
        executor.execute(() -> runCommand(command, false, callback));
    }

    public static void executeSu(String command, AdbCallback callback) {
        executor.execute(() -> runCommand(command, true, callback));
    }

    private static void runCommand(String command, boolean useSu, AdbCallback callback) {
        LogBuffer.i("AdbCommandExecutor", "Executing: " + (useSu ? "su -c " : "") + command);
        StringBuilder output = new StringBuilder();
        StringBuilder error = new StringBuilder();
        Process process = null;
        try {
            if (useSu) {
                process = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
            } else {
                process = Runtime.getRuntime().exec(command);
            }

            try (BufferedReader outReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                 BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = outReader.readLine()) != null) {
                    output.append(line).append("\n");
                }
                while ((line = errReader.readLine()) != null) {
                    error.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                callback.onSuccess(output.toString().trim());
            } else {
                callback.onError(exitCode, error.toString().trim());
            }
        } catch (Exception e) {
            LogBuffer.e("AdbCommandExecutor", "Command failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            callback.onException(e);
        } finally {
            if (process != null) {
                try { process.destroy(); } catch (Exception ignored) {}
            }
        }
    }
}
