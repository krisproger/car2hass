package com.car2hass.vehicle;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import com.car2hass.CANDataItem;

public class VehicleResearchTest {
    public static void main(String[] args) throws Exception {
        List<ChannelResult> results = new ArrayList<>();
        results.add(ChannelResult.ok(7));
        results.add(ChannelResult.dead("ECONNREFUSED"));

        String s = VehicleResearch.summary(results);
        check(s.length() > 0, "summary non-empty");

        StringBuilder sb = new StringBuilder();
        for (ChannelResult r : results) sb.append(r.summary()).append('\n');
        check(sb.toString().contains("7"), "summary keeps signals count");

        testProgressListener();
        testRunWithRegistry();
        testBrandScoped();
        System.out.println("All VehicleResearch tests passed.");
    }

    private static void testRunWithRegistry() throws Exception {
        RegistryStore rs = RegistryStore.of(
                new JSONObject("{\"sensors\":["
                        + "{\"key\":\"speed\",\"channels\":{\"diplus\":{\"name\":\"车速\"},\"adb\":null,\"system\":null}},"
                        + "{\"key\":\"gear\",\"channels\":{\"diplus\":{\"name\":\"档位\"},\"adb\":null,\"system\":null}}"
                        + "]}"),
                new JSONObject("{\"commands\":[{\"id\":\"ac_on\",\"state_sensor\":\"ac_state\","
                        + "\"channels\":{\"diplus\":{\"command\":\"x\"}}}]}"),
                new JSONObject("{\"profiles\":[{\"id\":\"byd_generic\","
                        + "\"expected_sensors\":[\"speed\",\"gear\"]}]}"));

        // stub probe: speed responds on diplus, gear does not
        VehicleResearch.SensorProbe probe = (ctx, reg, key, ch) ->
                ("speed".equals(key) && "diplus".equals(ch))
                        ? ProbeResult.fromRaw("42", false) : ProbeResult.unsupported();

        VehicleResearch.ResearchOutcome out = VehicleResearch.runWithRegistry(
                null, rs, new ArrayList<>(), null, null, probe,
                (c, p, a, rp) -> { /* no-op persister in test */ });

        check(out.report != null, "report built");
        check("byd_generic".equals(out.selectedProfile), "selected profile scored");
        check(out.report.getJSONObject("sensors").has("speed"), "sensor speed in report");
        check(out.report.getJSONObject("sensors").getJSONObject("speed")
                .getString("diplus").equals("ok"), "speed diplus ok");
    }

    /** Brand-scoped: a live diplus channel routes scoring to the byd family. */
    private static void testBrandScoped() throws Exception {
        RegistryStore rs = RegistryStore.of(
                new JSONObject("{\"sensors\":["
                        + "{\"key\":\"speed\",\"channels\":{\"diplus\":{\"name\":\"车速\"}}},"
                        + "{\"key\":\"gear\",\"channels\":{\"diplus\":{\"name\":\"档位\"}}}"
                        + "]}"),
                new JSONObject("{\"commands\":[]}"),
                new JSONObject("{\"profiles\":["
                        + "{\"id\":\"byd_generic\",\"key_channel\":\"diplus\",\"expected_sensors\":[\"speed\",\"gear\"]},"
                        + "{\"id\":\"voyah_generic\",\"key_channel\":\"voyah\",\"expected_sensors\":[\"soc\"]}]}"));

        VehicleResearch.SensorProbe probe = (ctx, reg, key, ch) ->
                ("speed".equals(key) && "diplus".equals(ch))
                        ? ProbeResult.fromRaw("42", false) : ProbeResult.unsupported();

        List<DataChannel> channels = new ArrayList<>();
        channels.add(new DataChannel() {
            @Override public String id() { return "diplus"; }
            @Override public String displayName() { return "DiPlus"; }
            @Override public boolean supportsCommands() { return false; }
            @Override public ChannelResult probe(android.content.Context ctx) {
                return ChannelResult.rawData("alive");
            }
            @Override public List<CANDataItem> read(android.content.Context ctx,
                                                    List<CANDataItem> knownItems) {
                return new ArrayList<>();
            }
        });

        VehicleResearch.ResearchOutcome out = VehicleResearch.runWithRegistry(
                null, rs, channels, null, null, probe,
                (c, p, a, rp) -> { });
        check("byd_generic".equals(out.selectedProfile), "byd selected via live brand channel");
    }

    private static void testProgressListener() {
        final int[] calls = {0};
        final int[] lastDone = {0};
        final int[] lastTotal = {0};
        VehicleResearch.ProgressListener listener = (done, total, name) -> {
            calls[0]++;
            lastDone[0] = done;
            lastTotal[0] = total;
        };
        // run() requires a Context and probes channels — not available in plain
        // Java. Here we only verify the listener contract is callable.
        listener.onChannelDone(2, 4, "DiPlus");
        check(calls[0] == 1, "listener called once");
        check(lastDone[0] == 2 && lastTotal[0] == 4, "listener got done/total");
    }

    private static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }
}