package com.car2hass;

/**
 * Plain-Java unit tests for SignalTranslator.
 *
 * <p>These tests do not depend on the Android runtime and can be executed with
 * a standard JDK: compile both SignalTranslator.java and this class, then run
 * the main method.
 */
public class SignalTranslatorTest {

    public static void main(String[] args) {
        int failures = 0;

        failures += testChineseToEnglish();
        failures += testNumericEnumMapping();
        failures += testUnknownValuePassthrough();
        failures += testNumericSentinels();
        failures += testOffStateDetection();
        failures += testNewlyAddedTranslations();

        if (failures > 0) {
            System.err.println("FAILURES: " + failures);
            System.exit(1);
        }
        System.out.println("All SignalTranslator tests passed.");
    }

    private static int testChineseToEnglish() {
        assertEquals("on", SignalTranslator.translateValue("开"), "Chinese '开' should translate to 'on'");
        assertEquals("off", SignalTranslator.translateValue("关"), "Chinese '关' should translate to 'off'");
        assertEquals("offline", SignalTranslator.translateValue("离线"), "Chinese '离线' should translate to 'offline'");
        assertEquals("locked", SignalTranslator.translateValue("锁定"), "Chinese '锁定' should translate to 'locked'");
        assertEquals("buckled", SignalTranslator.translateValue("已系"), "Chinese '已系' should translate to 'buckled'");
        assertEquals("unbuckled", SignalTranslator.translateValue("未系"), "Chinese '未系' should translate to 'unbuckled'");
        return 0;
    }

    private static int testNumericEnumMapping() {
        assertEquals("off", SignalTranslator.translateEnumValue("power_state", "0"), "power_state 0 -> off");
        assertEquals("on", SignalTranslator.translateEnumValue("power_state", "1"), "power_state 1 -> on");
        assertEquals("driving", SignalTranslator.translateEnumValue("power_state", "2"), "power_state 2 -> driving");
        assertEquals("open", SignalTranslator.translateEnumValue("driver_door", "1"), "driver_door 1 -> open");
        assertEquals("closed", SignalTranslator.translateEnumValue("driver_door", "0"), "driver_door 0 -> closed");
        assertEquals("P", SignalTranslator.translateEnumValue("gear", "1"), "gear 1 -> P");
        return 0;
    }

    private static int testUnknownValuePassthrough() {
        assertEquals("custom_value", SignalTranslator.translateValue("custom_value"), "Unknown values pass through");
        assertEquals("custom_value", SignalTranslator.translateEnumValue("unknown_key", "custom_value"), "Unknown key passes through");
        assertEquals("99", SignalTranslator.translateEnumValue("power_state", "99"), "Unknown enum index passes through");
        return 0;
    }

    private static int testNumericSentinels() {
        assertEquals("∞", SignalTranslator.translateEnumValue("energy_per_100km", "∞"), "Infinity sentinel passes through");
        assertEquals("NaN", SignalTranslator.translateEnumValue("energy_per_100km", "NaN"), "NaN sentinel passes through");
        assertEquals("—", SignalTranslator.translateEnumValue("energy_per_100km", "—"), "Em-dash sentinel passes through");
        return 0;
    }

    private static int testNewlyAddedTranslations() {
        // Шаг 5: enum labels for sentry / lead-car signals.
        assertEquals("parked recording on", SignalTranslator.translateValue("开启熄火录制"),
                "开启熄火录制 -> parked recording on");
        assertEquals("engine-off sentry on", SignalTranslator.translateValue("开启熄火哨兵"),
                "开启熄火哨兵 -> engine-off sentry on");
        assertEquals("alarming", SignalTranslator.translateValue("报警中"), "报警中 -> alarming");
        assertEquals("no target", SignalTranslator.translateValue("无目标"), "无目标 -> no target");
        assertEquals("not moving", SignalTranslator.translateValue("未移动"), "未移动 -> not moving");
        // Шаг 6: missing VALUE_TRANS entries.
        assertEquals("AC gun", SignalTranslator.translateValue("交流枪"), "交流枪 -> AC gun");
        assertEquals("adapter gun", SignalTranslator.translateValue("转换枪"), "转换枪 -> adapter gun");
        assertEquals("discharge gun", SignalTranslator.translateValue("放电枪"), "放电枪 -> discharge gun");
        assertEquals("started", SignalTranslator.translateValue("开始"), "开始 -> started");
        assertEquals("done", SignalTranslator.translateValue("完成"), "完成 -> done");
        assertEquals("aborted", SignalTranslator.translateValue("终止"), "终止 -> aborted");
        assertEquals("active1", SignalTranslator.translateValue("激活1"), "激活1 -> active1");
        assertEquals("error", SignalTranslator.translateValue("错误"), "错误 -> error");
        assertEquals("state3", SignalTranslator.translateValue("状态3"), "状态3 -> state3");
        assertEquals("cancelled", SignalTranslator.translateValue("取消或无效"), "取消或无效 -> cancelled");
        assertEquals("state4", SignalTranslator.translateValue("状态4"), "状态4 -> state4");
        assertEquals("auto-start", SignalTranslator.translateValue("主动起步"), "主动起步 -> auto-start");
        assertEquals("defrost", SignalTranslator.translateValue("除霜"), "除霜 -> defrost");
        assertEquals("feet+defrost", SignalTranslator.translateValue("吹脚除霜"), "吹脚除霜 -> feet+defrost");
        assertEquals("face+feet+defrost", SignalTranslator.translateValue("吹面+吹脚+除霜"),
                "吹面+吹脚+除霜 -> face+feet+defrost");
        assertEquals("face+defrost", SignalTranslator.translateValue("吹面+除霜"), "吹面+除霜 -> face+defrost");
        assertEquals("storage error", SignalTranslator.translateValue("存储异常"), "存储异常 -> storage error");
        assertEquals("left2", SignalTranslator.translateValue("左转2"), "左转2 -> left2");
        assertEquals("right2", SignalTranslator.translateValue("右转2"), "右转2 -> right2");
        assertEquals("emergency", SignalTranslator.translateValue("紧急"), "紧急 -> emergency");
        assertEquals("rear flash", SignalTranslator.translateValue("后闪灯"), "后闪灯 -> rear flash");
        assertEquals("flash", SignalTranslator.translateValue("闪灯"), "闪灯 -> flash");
        // Шаг 7: DiPlus sends airflow labels without a slash.
        assertEquals("face+feet", SignalTranslator.translateValue("吹面吹脚"), "吹面吹脚 -> face+feet");
        assertEquals("feet+defrost", SignalTranslator.translateValue("吹脚除霜"), "吹脚除霜 -> feet+defrost");
        return 0;
    }

    private static int testOffStateDetection() {
        assertTrue(SignalTranslator.isOffState("off"), "off is off state");
        assertTrue(SignalTranslator.isOffState("Offline"), "Offline is off state");
        assertTrue(SignalTranslator.isOffState("Inactive"), "Inactive is off state");
        assertTrue(SignalTranslator.isOffState("disabled"), "disabled is off state");
        assertTrue(SignalTranslator.isOffState("Stopped"), "Stopped is off state");
        assertFalse(SignalTranslator.isOffState("on"), "on is not off state");
        assertFalse(SignalTranslator.isOffState("driving"), "driving is not off state");
        assertFalse(SignalTranslator.isOffState(null), "null is not off state");
        return 0;
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertTrue(boolean actual, String message) {
        if (!actual) {
            throw new AssertionError(message + " expected=true actual=false");
        }
    }

    private static void assertFalse(boolean actual, String message) {
        if (actual) {
            throw new AssertionError(message + " expected=false actual=true");
        }
    }
}
