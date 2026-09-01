package com.car2hass.vehicle;

import java.util.List;

public class ChannelResultTest {
    public static void main(String[] args) {
        ChannelResult ok = ChannelResult.ok(42);
        check(ok.isAlive(), "ok must be alive");
        check(ok.getSignalsCount() == 42, "ok keeps count");
        check(ok.summary().contains("42"), "ok summary mentions count");

        ChannelResult raw = ChannelResult.rawData("parcel bytes seen");
        check(raw.isAlive(), "raw data is alive");
        check(raw.isRawDataPresent(), "raw flag set");
        check(raw.summary().contains("какие-то данные"), "raw summary wording");

        ChannelResult dead = ChannelResult.dead("ECONNREFUSED");
        check(!dead.isAlive(), "dead not alive");
        check(dead.summary().contains("ECONNREFUSED"), "dead summary has reason");

        ChannelResult denied = ChannelResult.accessDenied("permission");
        check(!denied.isAlive(), "denied not alive");
        check(denied.summary().contains("ошибка доступа"), "denied summary wording");

        ChannelResult withErrors = ChannelResult.dead("fail");
        check(withErrors.getErrors().size() == 1, "errors list populated");
        System.out.println("All ChannelResult tests passed.");
    }

    private static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }
}