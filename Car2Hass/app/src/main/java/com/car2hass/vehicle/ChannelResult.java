package com.car2hass.vehicle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Результат зондирования канала данных. */
public class ChannelResult {
    private final boolean alive;
    private final int signalsCount;
    private final boolean rawDataPresent;
    private final List<String> errors;
    private final String detail;

    private ChannelResult(boolean alive, int signalsCount, boolean rawDataPresent,
                          List<String> errors, String detail) {
        this.alive = alive;
        this.signalsCount = signalsCount;
        this.rawDataPresent = rawDataPresent;
        this.errors = errors != null ? new ArrayList<>(errors) : new ArrayList<>();
        this.detail = detail != null ? detail : "";
    }

    public static ChannelResult ok(int signals) {
        return new ChannelResult(true, Math.max(0, signals), false,
                new ArrayList<>(), "жив, " + Math.max(0, signals) + " сенсоров");
    }

    public static ChannelResult rawData(String detail) {
        return new ChannelResult(true, 0, true, new ArrayList<>(),
                detail != null && !detail.isEmpty() ? "есть какие-то данные: " + detail : "есть какие-то данные");
    }

    public static ChannelResult dead(String reason) {
        return new ChannelResult(false, 0, false,
                reason != null ? Collections.singletonList(reason) : new ArrayList<>(),
                "недоступен: " + (reason != null ? reason : "причина неизвестна"));
    }

    public static ChannelResult accessDenied(String reason) {
        return new ChannelResult(false, 0, false,
                reason != null ? Collections.singletonList(reason) : new ArrayList<>(),
                "ошибка доступа: " + (reason != null ? reason : "причина неизвестна"));
    }

    public boolean isAlive() { return alive; }
    public int getSignalsCount() { return signalsCount; }
    public boolean isRawDataPresent() { return rawDataPresent; }
    public List<String> getErrors() { return Collections.unmodifiableList(errors); }
    public String getDetail() { return detail; }

    /** Краткая строка для сводки на экране. */
    public String summary() { return detail; }
}