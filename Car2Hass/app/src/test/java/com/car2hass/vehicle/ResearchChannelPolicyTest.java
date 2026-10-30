package com.car2hass.vehicle;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ResearchChannelPolicyTest {
    public static void main(String[] args) {
        Map<String, Boolean> avail = new HashMap<>();
        avail.put("diplus", true);
        avail.put("adb", false);
        avail.put("voyah", true);
        check(!ResearchChannelPolicy.allowAuto("voyah", "byd_generic", avail), "no voyah on BYD");
        check(ResearchChannelPolicy.allowAuto("voyah", "voyah_generic", avail), "voyah on Voyah");
        check(ResearchChannelPolicy.allowAuto("voyah", null, avail), "allow voyah when profile unknown");
        check(!ResearchChannelPolicy.allowAuto("adb", "byd_generic", avail), "skip dead adb");
        check(ResearchChannelPolicy.allowAuto("diplus", "byd_generic", avail), "keep alive diplus");
        check(ResearchChannelPolicy.allowAuto("new_channel", "byd_generic", avail), "fail-open unknown");

        List<DataChannel> all = Arrays.asList(new Fake("system"), new Fake("voyah"), new Fake("adb"));
        List<DataChannel> auto = ResearchChannelPolicy.filterAuto(all, "byd_generic", avail);
        check(auto.size() == 1 && "system".equals(auto.get(0).id()), "auto filtered");
        check(ResearchChannelPolicy.manual(all).size() == 3, "manual all");
        System.out.println("All ResearchChannelPolicy tests passed.");
    }

    private static void check(boolean ok, String name) { if (!ok) throw new AssertionError(name); }

    private static final class Fake implements DataChannel {
        private final String id;
        Fake(String id) { this.id = id; }
        public String id() { return id; }
        public String displayName() { return id; }
        public boolean supportsCommands() { return false; }
        public ChannelResult probe(android.content.Context c) { return ChannelResult.dead("fake"); }
        public List<com.car2hass.CANDataItem> read(android.content.Context c,
                                                    List<com.car2hass.CANDataItem> k) {
            return new java.util.ArrayList<>();
        }
        public boolean isAvailable(android.content.Context c) { return true; }
    }
}
