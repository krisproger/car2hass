package com.car2hass.vehicle;

import org.json.JSONObject;

import java.util.List;

public class RegistryStoreTest {
    public static void main(String[] args) throws Exception {
        RegistryStore rs = RegistryStore.of(
                new JSONObject("{\"sensors\":["
                        + "{\"key\":\"speed\",\"label_en\":\"Speed\",\"channels\":{\"diplus\":{\"name\":\"车速\"},\"adb\":null,\"system\":null}},"
                        + "{\"key\":\"location_lat\",\"label_en\":\"Latitude\",\"channels\":{\"diplus\":null,\"adb\":null,\"system\":{\"field\":\"lat\"}}}"
                        + "]}"),
                new JSONObject("{\"commands\":["
                        + "{\"id\":\"ac_on\",\"state_sensor\":\"ac_state\",\"channels\":{\"diplus\":{\"command\":\"x\"}}}"
                        + "]}"),
                new JSONObject("{\"profiles\":["
                        + "{\"id\":\"byd_generic\",\"expected_sensors\":[\"speed\",\"location_lat\"]}"
                        + "]}"));

        testSensorLookup(rs);
        testChannelNullForMissing(rs);
        testProfileSensors(rs);
        testCommandCrossRef(rs);
        testSensorKeys(rs);
        testChannelIdsFallback(rs);
        testChannelIdsPriority();
        testBrandAndGeneric();
        System.out.println("All RegistryStore tests passed.");
    }

    private static void testBrandAndGeneric() throws Exception {
        RegistryStore rs = RegistryStore.of(
                new JSONObject("{\"sensors\":[]}"),
                new JSONObject("{\"commands\":[]}"),
                new JSONObject("{\"generic_sensors\":[\"soc\",\"range\"],\"profiles\":["
                        + "{\"id\":\"byd_generic\",\"key_channel\":\"diplus\","
                        + "\"expected_sensors\":[\"speed\",\"soc\",\"gear\"]},"
                        + "{\"id\":\"voyah_generic\",\"key_channel\":\"voyah\","
                        + "\"expected_sensors\":[\"range\"]}]}"));
        if (!rs.isGenericSensor("soc")) throw new AssertionError("soc must be generic");
        if (rs.isGenericSensor("speed")) throw new AssertionError("speed must not be generic");
        if (!"diplus".equals(rs.profileKeyChannel("byd_generic")))
            throw new AssertionError("byd key_channel");
        if (rs.profileKeyChannel("voyah_generic") == null
                || !"voyah".equals(rs.profileKeyChannel("voyah_generic")))
            throw new AssertionError("voyah key_channel");
        if (rs.profileNonGenericSensors("byd_generic").size() != 2)
            throw new AssertionError("byd non-generic size="
                    + rs.profileNonGenericSensors("byd_generic").size());
        if (!rs.profileNonGenericSensors("voyah_generic").isEmpty())
            throw new AssertionError("voyah non-generic must be empty");
        if (rs.profileKeyChannel("missing") != null) throw new AssertionError("missing key_channel must be null");
    }

    private static void testSensorLookup(RegistryStore rs) throws Exception {
        if (rs.getSensor("speed") == null) throw new AssertionError("speed sensor missing");
        if (rs.sensorCount() != 2) throw new AssertionError("sensorCount");
    }

    private static void testChannelNullForMissing(RegistryStore rs) throws Exception {
        if (rs.sensorChannel("location_lat", "adb") != null)
            throw new AssertionError("location_lat native must be null");
        if (rs.sensorChannel("speed", "diplus") == null)
            throw new AssertionError("speed diplus channel expected");
    }

    private static void testProfileSensors(RegistryStore rs) throws Exception {
        List<String> keys = rs.profileSensorKeys("byd_generic");
        if (keys.size() != 2) throw new AssertionError("byd_generic sensors=" + keys);
        if (!rs.profileIds().contains("byd_generic")) throw new AssertionError("profileIds");
    }

    private static void testCommandCrossRef(RegistryStore rs) throws Exception {
        String ss = rs.commandStateSensor("ac_on");
        if (!"ac_state".equals(ss)) throw new AssertionError("ac_on state_sensor=" + ss);
        if (!rs.commandIds().contains("ac_on")) throw new AssertionError("commandIds");
    }

    private static void testSensorKeys(RegistryStore rs) throws Exception {
        List<String> keys = rs.sensorKeys();
        if (!keys.contains("speed") || !keys.contains("location_lat"))
            throw new AssertionError("sensorKeys=" + keys);
    }

    private static void testChannelIdsFallback(RegistryStore rs) throws Exception {
        // No channels_priority block -> union of per-sensor channel keys.
        List<String> ids = rs.channelIds();
        if (!(ids.contains("diplus") && ids.contains("adb") && ids.contains("system")))
            throw new AssertionError("fallback union ids=" + ids);
        if (ids.size() != 3) throw new AssertionError("fallback size=" + ids);
    }

    private static void testChannelIdsPriority() throws Exception {
        RegistryStore rs = RegistryStore.of(
                new JSONObject("{\"version\":1,\"channels_priority\":[\"adb\",\"diplus\"],"
                        + "\"sensors\":[{\"key\":\"speed\",\"channels\":{"
                        + "\"diplus\":{\"name\":\"x\"},\"adb\":null}}]}"),
                new JSONObject("{\"commands\":[]}"),
                new JSONObject("{\"profiles\":[]}"));
        List<String> ids = rs.channelIds();
        if (ids.size() != 2 || !"adb".equals(ids.get(0)) || !"diplus".equals(ids.get(1)))
            throw new AssertionError("priority order=" + ids);
    }
}
