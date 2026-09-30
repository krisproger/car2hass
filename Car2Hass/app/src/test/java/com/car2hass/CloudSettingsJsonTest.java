package com.car2hass;

public class CloudSettingsJsonTest {

    public static void main(String[] args) throws Exception {
        String s = "{\"hass_host\":\"ha.local\",\"car_name\":\"Мой BYD\",\"hass_token\":\"SECRET\"}";

        String noId = CloudSettingsJson.stripIdentity(s);
        if (noId.contains("car_name")) throw new AssertionError("car_name must be stripped");
        if (!noId.contains("ha.local")) throw new AssertionError("host must survive");

        String noSecret = CloudSettingsJson.stripSecret(s);
        if (noSecret.contains("hass_token")) throw new AssertionError("hass_token must be stripped");
        if (!noSecret.contains("car_name")) throw new AssertionError("identity preserved by stripSecret");

        if (!"SECRET".equals(CloudSettingsJson.extractSecret(s))) {
            throw new AssertionError("secret extraction failed");
        }
        if (!"".equals(CloudSettingsJson.extractSecret("{\"a\":1}"))) {
            throw new AssertionError("missing secret must extract as empty");
        }

        String meta = CloudSettingsJson.metaJson("BYD Song", 1700000000000L);
        if (!meta.contains("\"label\":\"BYD Song\"")) throw new AssertionError("meta label failed");
        if (!meta.contains("1700000000000")) throw new AssertionError("meta updated_at failed");

        if (!"not json".equals(CloudSettingsJson.stripIdentity("not json"))) {
            throw new AssertionError("invalid json must pass through unchanged");
        }
        System.out.println("All CloudSettingsJson tests passed.");
    }
}
