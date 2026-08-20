package com.diplustohass;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the output of a batched {@code service call autoservice} command built
 * by {@link NativeCommandBuilder} back into per-key raw values.
 *
 * <p>Each entry is emitted as:
 * <pre>
 * @soc
 * Result: Parcel(00000000 3f9df3b6   '....[...')
 * </pre>
 * A marker line ({@code @key}) sets the current signal; the next line carrying a
 * {@code Result: Parcel(00000000 <8 hex>} token is assigned to that key. Lines
 * that match neither are skipped.
 *
 * <p>The 8-hex token is the 32-bit raw value (an int for tx=5, IEEE-754 float
 * bits for tx=7) — see {@link SentinelDecoder} / {@link ParamDecoder}.
 */
public final class NativeOutputParser {

    /** Matches the 8-hex-digit token right after {@code Parcel(00000000}. */
    private static final Pattern PARCEL_REGEX =
            Pattern.compile("Parcel\\(00000000\\s+([0-9a-fA-F]{8})");

    private static final Pattern MARKER_REGEX = Pattern.compile("@([a-zA-Z0-9_]+)\\s*");

    private NativeOutputParser() {}

    /**
     * Parses {@code output} line by line.
     *
     * @return key → raw 32-bit value as unsigned long; keys whose Parcel line
     *         could not be parsed are absent
     */
    public static Map<String, Long> parse(String output) {
        Map<String, Long> result = new LinkedHashMap<>();
        if (output == null || output.isEmpty()) {
            return result;
        }
        String currentKey = null;
        for (String line : output.split("\\r?\\n", -1)) {
            if (line.trim().isEmpty()) {
                continue;
            }
            Matcher marker = MARKER_REGEX.matcher(line.trim());
            if (marker.matches()) {
                currentKey = marker.group(1);
                continue;
            }
            Matcher parcel = PARCEL_REGEX.matcher(line);
            if (parcel.find()) {
                if (currentKey == null) {
                    continue;
                }
                try {
                    result.put(currentKey, Long.parseLong(parcel.group(1), 16));
                } catch (NumberFormatException e) {
                    // Malformed hex — skip the signal, keep the marker state.
                }
                currentKey = null;
            }
        }
        return result;
    }
}
