package com.car2hass;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a single ADB shell command that reads several autoservice fids in one
 * session, each preceded by an {@code echo "@key"} marker so the output can be
 * matched back to the signal it belongs to.
 *
 * <p>BYDMate opens one ADB connection per fid; with ~40 signals polled every
 * few seconds that is 40 connections per tick. Gluing every
 * {@code service call} with {@code ;} lets the existing {@link AdbShellExecutor}
 * open a single TCP connection and read the whole output until the stream
 * closes.
 *
 * <p>Output shape per entry (see {@link NativeOutputParser}):
 * <pre>
 * @soc
 * Result: Parcel(00000000 3f9df3b6   '....[...')
 * </pre>
 */
public final class NativeCommandBuilder {

    private NativeCommandBuilder() {}

    /**
     * Builds one shell command for all entries, in the given order.
     *
     * @return command string, or null when {@code entries} is empty
     */
    public static String build(List<NativeSignalMap.FidEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>(entries.size());
        for (NativeSignalMap.FidEntry e : entries) {
            parts.add("echo \"@" + e.key + "\"; service call autoservice "
                    + e.transact + " i32 " + e.device + " i32 " + e.fid);
        }
        return String.join("; ", parts);
    }
}
