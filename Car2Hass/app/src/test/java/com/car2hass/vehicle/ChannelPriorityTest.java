package com.car2hass.vehicle;

import java.util.Arrays;

public class ChannelPriorityTest {
    public static void main(String[] args) {
        if (!ChannelPriority.ORDER.equals(Arrays.asList(
                "adb", "system", "dumpsys", "diplus", "obd", "byd_cloud")))
            throw new AssertionError("priority order mismatch: " + ChannelPriority.ORDER);
        if (ChannelPriority.rank("adb") != 0) throw new AssertionError("adb rank");
        if (!(ChannelPriority.rank("system") < ChannelPriority.rank("dumpsys")))
            throw new AssertionError("system must rank above dumpsys");
        if (ChannelPriority.rank("voyah") != ChannelPriority.rank("diplus"))
            throw new AssertionError("voyah and diplus are mutually exclusive and must share one slot");
        if (!(ChannelPriority.rank("obd") < ChannelPriority.rank("byd_cloud")))
            throw new AssertionError("obd must rank above byd_cloud");
        if (ChannelPriority.rank("diplus_push") != ChannelPriority.UNKNOWN_RANK)
            throw new AssertionError("diplus_push is not in the order and must be unknown");
        if (ChannelPriority.rank("weird") != ChannelPriority.UNKNOWN_RANK)
            throw new AssertionError("unknown channel must be lowest");
        if (ChannelPriority.rank(null) != ChannelPriority.UNKNOWN_RANK)
            throw new AssertionError("null channel must be lowest");

        if (!ChannelPriority.shouldAccept(ChannelPriority.rank("diplus"), 100L,
                ChannelPriority.rank("adb"), 50L))
            throw new AssertionError("higher priority must replace even when older");
        if (ChannelPriority.shouldAccept(ChannelPriority.rank("adb"), 100L,
                ChannelPriority.rank("diplus"), 200L))
            throw new AssertionError("lower priority must be ignored even when newer");
        if (!ChannelPriority.shouldAccept(5, 100L, 5, 100L))
            throw new AssertionError("same rank/same time accepted");
        if (!ChannelPriority.shouldAccept(5, 100L, 5, 150L))
            throw new AssertionError("same rank/newer accepted");
        if (ChannelPriority.shouldAccept(5, 100L, 5, 50L))
            throw new AssertionError("same rank/older ignored");

        System.out.println("All ChannelPriority tests passed.");
    }
}
