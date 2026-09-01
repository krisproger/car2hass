package com.car2hass.vehicle;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BrandSelectorTest {

    public static void main(String[] args) throws Exception {
        RegistryStore rs = RegistryStore.of(
                new JSONObject("{\"sensors\":[]}"),
                new JSONObject("{\"commands\":[]}"),
                new JSONObject("{\"profiles\":["
                        + "{\"id\":\"byd_generic\",\"key_channel\":\"diplus\"},"
                        + "{\"id\":\"song_pro_2022\",\"key_channel\":\"diplus\"},"
                        + "{\"id\":\"voyah_generic\",\"key_channel\":\"voyah\"}]}"));

        Map<String, Boolean> avail = new HashMap<>();
        avail.put("diplus", true);
        avail.put("voyah", false);
        List<String> byd = BrandSelector.detect(rs, avail);
        if (!byd.contains("byd_generic") || !byd.contains("song_pro_2022") || byd.contains("voyah_generic")) {
            throw new AssertionError("byd family: " + byd);
        }

        avail.clear();
        avail.put("voyah", true);
        List<String> voy = BrandSelector.detect(rs, avail);
        if (!voy.contains("voyah_generic") || voy.contains("byd_generic")) {
            throw new AssertionError("voyah family: " + voy);
        }

        avail.clear();
        avail.put("diplus", true);
        avail.put("voyah", true);
        if (!BrandSelector.detect(rs, avail).isEmpty()) {
            throw new AssertionError("ambiguous must be empty");
        }

        avail.clear();
        if (!BrandSelector.detect(rs, avail).isEmpty()) {
            throw new AssertionError("no live channel must be empty");
        }

        System.out.println("All BrandSelector tests passed.");
    }
}