package com.car2hass;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Shared helpers for plain-Java tests (no Android runtime).
 */
public final class TestUtil {

    private TestUtil() {}

    /** Reads a file relative to the project root (DiPlus-to-hass). */
    public static String loadMap(String path) throws Exception {
        File f = new File(path);
        if (!f.isFile()) {
            throw new IllegalStateException("missing test fixture: " + f.getAbsolutePath());
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }
}
