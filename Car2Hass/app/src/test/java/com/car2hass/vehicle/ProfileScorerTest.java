package com.car2hass.vehicle;

import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ProfileScorerTest {
    public static void main(String[] args) throws Exception {
        RegistryStore rs = RegistryStore.of(
                new JSONObject("{\"sensors\":["
                        + "{\"key\":\"speed\",\"channels\":{\"diplus\":{\"name\":\"车速\"}}},"
                        + "{\"key\":\"gear\",\"channels\":{\"diplus\":{\"name\":\"档位\"}}},"
                        + "{\"key\":\"ac_state\",\"channels\":{\"diplus\":{\"name\":\"空调\"}}}"
                        + "]}"),
                new JSONObject("{\"commands\":[]}"),
                new JSONObject("{\"profiles\":["
                        + "{\"id\":\"byd_generic\",\"expected_sensors\":[\"speed\",\"gear\",\"ac_state\"]},"
                        + "{\"id\":\"song_pro_2022\",\"expected_sensors\":[\"speed\",\"gear\"]}]}"));

        Map<String, Set<String>> ok = new HashMap<>();
        ok.put("speed", new HashSet<>(Arrays.asList("diplus")));
        ok.put("gear", new HashSet<>(Arrays.asList("diplus")));
        ok.put("ac_state", new HashSet<>(Arrays.asList("diplus")));

        ProfileScorer sc = new ProfileScorer(rs, ok);
        if (sc.score("byd_generic") != 3) throw new AssertionError("score generic=" + sc.score("byd_generic"));
        if (sc.score("song_pro_2022") != 2) throw new AssertionError("score song=" + sc.score("song_pro_2022"));
        if (!"byd_generic".equals(sc.selectBest())) throw new AssertionError("select=" + sc.selectBest());

        testVoyahBeatsPartiallyCoveredByd();

        System.out.println("All ProfileScorer tests passed.");
    }

    /** Voyah scenario: big profile has 6/20 ok, small Voyah has 3/3 — Voyah must win. */
    private static void testVoyahBeatsPartiallyCoveredByd() throws Exception {
        StringBuilder sensors = new StringBuilder("{\"sensors\":[");
        for (String k : new String[]{"range", "soc", "engine_coolant_temp", "x1", "x2", "x3", "x4", "x5", "x6"}) {
            sensors.append("{\"key\":\"").append(k)
                    .append("\",\"channels\":{\"voyah\":{\"name\":\"v\"},\"diplus\":{\"name\":\"d\"}}},");
        }
        sensors.setLength(sensors.length() - 1);
        sensors.append("]}");

        StringBuilder bydKeys = new StringBuilder();
        for (String k : new String[]{"x1", "x2", "x3", "x4", "x5", "x6"}) {
            bydKeys.append("\"").append(k).append("\",");
        }
        // 14 more expected sensors that return nothing -> byd has only 6/20 ok.
        for (int i = 7; i <= 20; i++) {
            bydKeys.append("\"x").append(i).append("\",");
        }
        bydKeys.setLength(bydKeys.length() - 1);

        RegistryStore rs2 = RegistryStore.of(
                new JSONObject(sensors.toString()),
                new JSONObject("{\"commands\":[]}"),
                new JSONObject("{\"profiles\":["
                        + "{\"id\":\"byd_generic\",\"expected_sensors\":[" + bydKeys + "]},"
                        + "{\"id\":\"voyah_generic\",\"expected_sensors\":[\"range\",\"soc\",\"engine_coolant_temp\"]}]}"));

        Map<String, Set<String>> ok2 = new HashMap<>();
        for (String k : new String[]{"range", "soc", "engine_coolant_temp", "x1", "x2", "x3", "x4", "x5", "x6"}) {
            ok2.put(k, new HashSet<>(Arrays.asList("voyah")));
        }
        ProfileScorer sc2 = new ProfileScorer(rs2, ok2);
        if (sc2.score("byd_generic") != 6) throw new AssertionError("byd ok=" + sc2.score("byd_generic"));
        if (sc2.score("voyah_generic") != 3) throw new AssertionError("voyah ok=" + sc2.score("voyah_generic"));
        if (sc2.failCount("byd_generic") != 14) throw new AssertionError("byd fail=" + sc2.failCount("byd_generic"));
        if (sc2.failCount("voyah_generic") != 0) throw new AssertionError("voyah fail=" + sc2.failCount("voyah_generic"));
        List<String> family = new java.util.ArrayList<>();
        family.add("voyah_generic");
        if (!"voyah_generic".equals(sc2.selectBestForBrand(family))) {
            throw new AssertionError("family select=" + sc2.selectBestForBrand(family));
        }
        if (!"voyah_generic".equals(sc2.selectBest())) {
            throw new AssertionError("voyah must win over 6/20 byd, got " + sc2.selectBest());
        }
    }
}
