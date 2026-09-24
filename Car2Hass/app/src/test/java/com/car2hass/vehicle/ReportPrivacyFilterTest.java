package com.car2hass.vehicle;

import org.json.JSONArray;
import org.json.JSONObject;

public class ReportPrivacyFilterTest {
    public static void main(String[] args) throws Exception {
        testTopLevelAndNested();
        testBoundaryVvinKept();
        testValuesSurvive();
        testArraysRecursed();
        System.out.println("All ReportPrivacyFilter tests passed.");
    }

    private static void testTopLevelAndNested() throws Exception {
        JSONObject src = new JSONObject();
        src.put("vin", "LGXCE6CB1P0123456");
        src.put("fw_version", "1.2.3");
        src.put("battery_serial", "SN123");
        src.put("serial", "SN999");
        JSONObject sensors = new JSONObject();
        sensors.put("vin", "nested-vin");
        sensors.put("speed", "ok");
        JSONObject deep = new JSONObject();
        deep.put("fw_version", "9.9");
        deep.put("gear", "ok");
        sensors.put("nested", deep);
        src.put("sensors", sensors);

        JSONObject out = ReportPrivacyFilter.sanitize(src);
        check(!out.has("vin"), "top-level vin removed");
        check(!out.has("fw_version"), "top-level fw_version removed");
        check(!out.has("battery_serial"), "battery_serial removed");
        check(!out.has("serial"), "serial removed");
        JSONObject outSensors = out.getJSONObject("sensors");
        check(!outSensors.has("vin"), "nested sensors.vin removed");
        check(!outSensors.getJSONObject("nested").has("fw_version"), "deep fw_version removed");
        check("ok".equals(outSensors.getString("speed")), "speed kept");
        check("ok".equals(outSensors.getJSONObject("nested").getString("gear")), "gear kept");

        check(ReportPrivacyFilter.isDenied("vin"), "vin denied");
        check(ReportPrivacyFilter.isDenied("_vin_"), "underscore vin denied");
        check(ReportPrivacyFilter.isDenied("my_vin"), "suffix _vin denied");
        check(ReportPrivacyFilter.isDenied("vin_code"), "vin_ prefix denied");
        check(ReportPrivacyFilter.isDenied("FW_VERSION"), "case-insensitive fw_version");
        check(ReportPrivacyFilter.isDenied("battery_serial"), "serial substring denied");
    }

    private static void testBoundaryVvinKept() throws Exception {
        JSONObject src = new JSONObject();
        src.put("vvin", "keep-me");
        JSONObject out = ReportPrivacyFilter.sanitize(src);
        check(out.has("vvin"), "vvin kept (regex needs a boundary)");
        check("keep-me".equals(out.getString("vvin")), "vvin value untouched");
        check(!ReportPrivacyFilter.isDenied("vvin"), "vvin not denied");
    }

    private static void testValuesSurvive() throws Exception {
        JSONObject src = new JSONObject();
        src.put("note", "vin=LGXCE6CB; fw_version=1.0; serial=SN1");
        src.put("speed", 42);
        src.put("available", true);
        JSONObject out = ReportPrivacyFilter.sanitize(src);
        check("vin=LGXCE6CB; fw_version=1.0; serial=SN1".equals(out.getString("note")),
                "deny-listed words in values survive");
        check(out.getInt("speed") == 42, "numeric value survives");
        check(out.getBoolean("available"), "boolean value survives");
    }

    private static void testArraysRecursed() throws Exception {
        JSONArray arr = new JSONArray();
        JSONObject a = new JSONObject();
        a.put("vin", "x");
        a.put("name", "a");
        arr.put(a);
        arr.put("plain");
        JSONObject src = new JSONObject();
        src.put("channels", arr);

        JSONObject out = ReportPrivacyFilter.sanitize(src);
        JSONArray outArr = out.getJSONArray("channels");
        check(!outArr.getJSONObject(0).has("vin"), "array object vin removed");
        check("a".equals(outArr.getJSONObject(0).getString("name")), "array object name kept");
        check("plain".equals(outArr.getString(1)), "array scalar kept");

        JSONArray cleaned = ReportPrivacyFilter.sanitize(arr);
        check(!cleaned.getJSONObject(0).has("vin"), "sanitize(JSONArray) removes vin");
    }

    private static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }
}
