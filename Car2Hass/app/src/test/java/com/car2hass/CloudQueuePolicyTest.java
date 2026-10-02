package com.car2hass;

public class CloudQueuePolicyTest {
    public static void main(String[] args) {
        if (CloudQueuePolicy.sizeBytes("") != 200)
            throw new AssertionError("empty signals -> 200");
        if (CloudQueuePolicy.sizeBytes("{\"a\":1}") != 207)
            throw new AssertionError("7-char signals -> 207");

        long[] ids = {10, 11, 12, 13};
        long[] sizes = {200, 200, 200, 200};
        // Freeing 250 needs the first two rows (400 >= 250) -> cutoff id 11.
        if (CloudQueuePolicy.evictionCutoffId(250, ids, sizes) != 11)
            throw new AssertionError("cutoff for 250");
        if (CloudQueuePolicy.evictionCutoffId(0, ids, sizes) != -1)
            throw new AssertionError("nothing to free -> -1");
        if (CloudQueuePolicy.evictionCutoffId(1000, ids, sizes) != 13)
            throw new AssertionError("free everything -> last id");
        if (CloudQueuePolicy.evictionCutoffId(100, new long[0], new long[0]) != -1)
            throw new AssertionError("empty queue -> -1");
        System.out.println("All CloudQueuePolicy tests passed.");
    }
}
