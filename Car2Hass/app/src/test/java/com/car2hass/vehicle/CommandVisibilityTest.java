package com.car2hass.vehicle;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommandVisibilityTest {
    public static void main(String[] args) {
        List<String> cmd = Arrays.asList("diplus", "adb");

        if (CommandVisibility.shouldShow(null, cmd)) throw new AssertionError("null availability");
        if (CommandVisibility.shouldShow(new HashMap<>(), cmd)) throw new AssertionError("unknown -> hidden");

        Map<String, Boolean> dead = new HashMap<>();
        dead.put("diplus", false);
        dead.put("adb", false);
        if (CommandVisibility.shouldShow(dead, cmd)) throw new AssertionError("dead -> hidden");

        Map<String, Boolean> aliveAdb = new HashMap<>();
        aliveAdb.put("diplus", false);
        aliveAdb.put("adb", true);
        if (!CommandVisibility.shouldShow(aliveAdb, cmd)) throw new AssertionError("alive adb -> shown");

        if (CommandVisibility.shouldShow(aliveAdb, null)) throw new AssertionError("null channels");

        // commandsAllowed gate.
        if (CommandVisibility.shouldShow(aliveAdb, cmd, false))
            throw new AssertionError("commands disabled -> hidden");
        if (!CommandVisibility.shouldShow(aliveAdb, cmd, true))
            throw new AssertionError("commands allowed + live -> shown");
        if (CommandVisibility.shouldShow(dead, cmd, true))
            throw new AssertionError("commands allowed but dead -> hidden");

        System.out.println("All CommandVisibility tests passed.");
    }
}
