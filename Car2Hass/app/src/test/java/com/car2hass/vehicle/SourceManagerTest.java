package com.car2hass.vehicle;
import org.json.JSONObject;
import java.util.*;

public class SourceManagerTest {
    public static void main(String[] args) throws Exception {
        RegistryStore rs = RegistryStore.of(
            new JSONObject("{\"sensors\":["
                + "{\"key\":\"speed\",\"channels\":{\"diplus\":{\"name\":\"车速\"},\"adb\":null,\"system\":null}},"
                + "{\"key\":\"location_lat\",\"channels\":{\"diplus\":null,\"adb\":null,\"system\":{\"field\":\"lat\"}}},"
                + "{\"key\":\"ac_state\",\"channels\":{\"diplus\":{\"name\":\"空调\"},\"adb\":{\"dev\":1,\"fid\":2},\"system\":null}}"
                + "]}"),
            new JSONObject("{\"commands\":[]}"),
            new JSONObject("{\"profiles\":[{\"id\":\"byd_generic\",\"expected_sensors\":[\"speed\",\"ac_state\"]}]}"));

        SourceManager sm = new SourceManager(rs, Arrays.asList("diplus", "adb"), "byd_generic", null);
        testOrderedChannelsDiplus(sm);
        testSystemAlways(sm);
        testCyclePriority(sm);
        testBuildItems(sm);
        testPhoneChannelIncluded();
        System.out.println("All SourceManager tests passed.");
    }
    private static void testOrderedChannelsDiplus(SourceManager sm) throws Exception {
        List<String> ch = sm.orderedChannels("ac_state");
        if (!ch.equals(Arrays.asList("diplus", "adb"))) throw new AssertionError("ac_state channels=" + ch);
    }
    private static void testSystemAlways(SourceManager sm) throws Exception {
        List<String> ch = sm.orderedChannels("location_lat");
        if (!ch.equals(Arrays.asList("system"))) throw new AssertionError("location must be system: " + ch);
    }
    private static void testCyclePriority(SourceManager sm) {
        List<String> pri = sm.cycleSourcePriority();
        if (!pri.equals(Arrays.asList("diplus", "adb"))) throw new AssertionError("cycle=" + pri);
    }
    private static void testBuildItems(SourceManager sm) throws Exception {
        List<?> items = sm.buildSignalItems();
        if (items.size() != 3) throw new AssertionError("items=" + items.size());
    }

    /** phone is a real channel now: it participates in the cycle like any other. */
    private static void testPhoneChannelIncluded() throws Exception {
        RegistryStore rs = RegistryStore.of(
            new JSONObject("{\"channels_priority\":[\"diplus\",\"phone\"],\"sensors\":["
                + "{\"key\":\"speed\",\"channels\":{\"diplus\":{\"name\":\"车速\"},\"phone\":{\"field\":\"speed\"}}}]}"),
            new JSONObject("{\"commands\":[]}"),
            new JSONObject("{\"profiles\":[]}"));
        SourceManager sm = new SourceManager(rs, Arrays.asList("diplus", "phone"), "byd_generic", null);
        if (!sm.cycleSourcePriority().equals(Arrays.asList("diplus", "phone")))
            throw new AssertionError("phone must be in the cycle: " + sm.cycleSourcePriority());
        if (!sm.orderedChannels("speed").equals(Arrays.asList("diplus", "phone")))
            throw new AssertionError("phone must be ordered: " + sm.orderedChannels("speed"));
    }
}
