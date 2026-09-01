package com.car2hass.vehicle;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

public class ResearchUiModelTest {
    public static void main(String[] args) throws Exception {
        RegistryStore rs = RegistryStore.of(
            new JSONObject("{\"sensors\":[],\"commands\":[]}"),
            new JSONObject("{\"commands\":[]}"),
            new JSONObject("{\"profiles\":["
                + "{\"id\":\"byd_generic\",\"label\":\"BYD (generic)\"},"
                + "{\"id\":\"song_pro_2022\",\"label\":\"Song Pro 2022\"}]}"));

        List<ResearchUiModel.ProfileOption> profs = ResearchUiModel.profiles(rs);
        if (profs.size() != 2) throw new AssertionError("profs=" + profs.size());
        if (!"BYD (generic)".equals(profs.get(0).label)) throw new AssertionError("label=" + profs.get(0).label);
        if (!"song_pro_2022".equals(profs.get(1).id)) throw new AssertionError("id=" + profs.get(1).id);

        // no report -> all available; active = [diplus, native]
        List<ResearchUiModel.ChannelView> ch = ResearchUiModel.channels(rs, null,
                Arrays.asList("diplus", "adb"));
        Map<String, ResearchUiModel.ChannelView> byName = new HashMap<>();
        for (ResearchUiModel.ChannelView c : ch) byName.put(c.name, c);
        if (byName.size() != ResearchUiModel.CYCLE_PRIORITY.size()) throw new AssertionError("ch count");
        if (!byName.get("system").available || !byName.get("system").checked)
            throw new AssertionError("system must be available+checked");
        if (!byName.get("diplus").checked) throw new AssertionError("diplus checked");
        if (byName.get("obd").checked) throw new AssertionError("obd unchecked");

        // report marks native/obd/diplus_push/byd_cloud unavailable
        JSONObject report = new JSONObject()
                .put("channels", new JSONArray("["
                        + "{\"id\":\"diplus\",\"available\":true},"
                        + "{\"id\":\"adb\",\"available\":false},"
                        + "{\"id\":\"dumpsys\",\"available\":true},"
                        + "{\"id\":\"system\",\"available\":true},"
                        + "{\"id\":\"obd\",\"available\":false},"
                        + "{\"id\":\"diplus_push\",\"available\":false},"
                        + "{\"id\":\"byd_cloud\",\"available\":false}]"));
        List<ResearchUiModel.ChannelView> ch2 = ResearchUiModel.channels(rs, report,
                Arrays.asList("diplus", "adb", "dumpsys"));
        Map<String, ResearchUiModel.ChannelView> by2 = new HashMap<>();
        for (ResearchUiModel.ChannelView c : ch2) by2.put(c.name, c);
        if (by2.get("adb").available) throw new AssertionError("native should be unavailable");
        if (by2.get("obd").available) throw new AssertionError("obd unavailable");
        if (by2.get("diplus_push").available) throw new AssertionError("diplus_push unavailable");
        if (!by2.get("dumpsys").checked) throw new AssertionError("dumpsys checked");
        if (!by2.get("system").available) throw new AssertionError("system always available");

        testUnionActive();
        System.out.println("All ResearchUiModel tests passed.");
    }

    private static void testUnionActive() {
        // Alive channels are added; user-enabled ones are never dropped.
        List<String> out = ResearchUiModel.unionActive(
                Arrays.asList("diplus"), Arrays.asList("adb", "obd"));
        if (!out.contains("diplus") || !out.contains("adb") || !out.contains("obd")
                || out.size() != 3)
            throw new AssertionError("union=" + out);
        // Empty alive check must not clear the user's selection.
        if (!ResearchUiModel.unionActive(Arrays.asList("diplus", "adb"),
                new ArrayList<>()).equals(Arrays.asList("diplus", "adb")))
            throw new AssertionError("empty alive must keep selection");
        // Legacy ids are migrated on merge.
        List<String> legacy = ResearchUiModel.unionActive(
                Arrays.asList("native", "sysprops"), new ArrayList<>());
        if (legacy.contains("native") || legacy.contains("sysprops"))
            throw new AssertionError("legacy ids=" + legacy);
    }
}
