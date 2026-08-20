package com.diplustohass;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Deduplicates exported log lines.
 *
 * <p>The in-memory buffer and the persistent file log overlap (the same events
 * are replayed into both sections of the export). Dedup key is the full line
 * text including its timestamp: a repeated timestamp line is the same event
 * seen again from another section/replay and is dropped. Legitimate repeats
 * (same text at a different time) carry different timestamps and are kept.
 * Blank lines are skipped; order is preserved (LinkedHashSet semantics).
 */
public final class LogDedup {

    private LogDedup() {}

    /**
     * Split {@code text} into lines and return only the first occurrence of
     * each non-blank line, preserving the original order.
     */
    public static List<String> dedupeLines(String text) {
        return dedupeLines(text, null);
    }

    /**
     * Same as {@link #dedupeLines(String)} but also drops any line already
     * present in {@code exclude} (e.g. lines of an earlier export section).
     */
    public static List<String> dedupeLines(String text, List<String> exclude) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return out;
        }
        Set<String> seen = new LinkedHashSet<>();
        if (exclude != null) {
            seen.addAll(exclude);
        }
        for (String line : text.split("\n", -1)) {
            if (line.trim().isEmpty()) {
                continue;
            }
            if (seen.add(line)) {
                out.add(line);
            }
        }
        return out;
    }
}
