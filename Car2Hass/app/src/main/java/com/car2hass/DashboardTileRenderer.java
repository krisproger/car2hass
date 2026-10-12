package com.car2hass;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Computes and applies the displayed value/sub/alert of a dashboard tile for the
 * non-interactive preset sources (single/primary numeric, numeric list, binary
 * any/all, HA status, fallback). Interactive toggle/select tiles stay in
 * MainActivity (they drive the command UI). Extracted from MainActivity.
 */
public final class DashboardTileRenderer {

    private DashboardTileRenderer() {}

    /** Stable fingerprint of the displayed tiles so unchanged data skips a redraw. */
    public static String fingerprint(List<DashboardTile> tiles) {
        StringBuilder sb = new StringBuilder(256);
        for (DashboardTile t : tiles) {
            sb.append(t.value).append('\u0001').append(t.unit).append('\u0001')
                    .append(t.sub).append('\u0001').append(t.alert)
                    .append(t.tapRightZone).append('\u0002');
        }
        return sb.toString();
    }

    /** Renders a tile from a preset whose behavior is not toggle/select. */
    public static void renderState(Context ctx, DashboardTile tile,
                                   DashboardPresetRegistry.DashboardPreset preset,
                                   Map<String, CANDataItem> byKey) {
        switch (preset.state.source) {
            case "single_numeric": {
                CANDataItem item = byKey.get(preset.state.primarySensor);
                if (item != null && !"---".equals(item.value)) {
                    tile.setValue(SignalTranslator.translateValue(item.value), item.unit != null ? item.unit : "");
                    tile.setSub("");
                    tile.setAlert(false);
                } else {
                    tile.setValue("—", "");
                    tile.setSub("");
                    tile.setAlert(false);
                }
                break;
            }
            case "primary_numeric": {
                CANDataItem item = byKey.get(preset.state.primarySensor);
                if (item != null && !"---".equals(item.value)) {
                    double val = DashboardLogic.parseDoubleSafe(item.value);
                    tile.setValue(SignalTranslator.translateValue(item.value), "%");
                    tile.setAlert(preset.state.thresholdOpValue > 0 && val < preset.state.thresholdOpValue);
                } else {
                    tile.setValue("—", "");
                    tile.setAlert(false);
                }
                break;
            }
            case "numeric_list": {
                StringBuilder sb = new StringBuilder();
                boolean anyValid = false;
                double minVal = Double.MAX_VALUE;
                for (String sk : preset.sensors) {
                    CANDataItem item = byKey.get(sk);
                    if (item != null && !"---".equals(item.value)) {
                        anyValid = true;
                        if (sb.length() > 0) sb.append(" / ");
                        sb.append(item.value);
                        double v = DashboardLogic.parseDoubleSafe(item.value);
                        if (v < minVal) minVal = v;
                    }
                }
                if (anyValid) {
                    tile.setValue(sb.toString(), "");
                    tile.setSub("");
                    tile.setAlert(preset.state.thresholdOpValue > 0 && minVal < preset.state.thresholdOpValue);
                } else {
                    tile.setValue("—", "");
                    tile.setAlert(false);
                }
                break;
            }
            case "binary_any":
            case "binary_all": {
                boolean any = "binary_any".equals(preset.state.source);
                List<String> openNames = new ArrayList<>();
                boolean triggered = false;
                for (String sk : preset.sensors) {
                    CANDataItem item = byKey.get(sk);
                    if (item == null || "---".equals(item.value)) continue;
                    String translated = SignalTranslator.translateEnumValue(sk, item.value);
                    boolean isTruthy = false;
                    if ("numeric_gt_0".equals(preset.state.truthyMode)) {
                        isTruthy = DashboardLogic.parseDoubleSafe(item.value) > 0;
                    } else if ("numeric_eq_0".equals(preset.state.truthyMode)) {
                        isTruthy = DashboardLogic.parseDoubleSafe(item.value) == 0;
                    } else {
                        for (String t : preset.state.truthy) {
                            if (t.equalsIgnoreCase(translated)) { isTruthy = true; break; }
                        }
                    }
                    if (isTruthy) {
                        triggered = true;
                        openNames.add(item.name);
                    } else if (!any) {
                        triggered = false;
                        break;
                    }
                }
                boolean isAlert = triggered;
                String label, sub;
                if (triggered) {
                    int openN = openNames.size();
                    int total = preset.sensors.size();
                    label = DashboardPresetRegistry.pick(ctx, preset.state.display.alertLabel, preset.state.display.alertLabelRu)
                            .replace("{open_n}", String.valueOf(openN))
                            .replace("{total}", String.valueOf(total))
                            .replace("{open_list}", DashboardLogic.joinStrings(openNames, ", "));
                    String alertSub = DashboardPresetRegistry.pick(ctx, preset.state.display.alertSub, preset.state.display.alertSubRu);
                    sub = alertSub != null
                            ? alertSub
                                    .replace("{open_n}", String.valueOf(openN))
                                    .replace("{total}", String.valueOf(total))
                                    .replace("{open_list}", DashboardLogic.joinStrings(openNames, ", "))
                            : "";
                    isAlert = preset.state.display.alertAlert;
                } else {
                    label = DashboardPresetRegistry.pick(ctx, preset.state.display.okLabel, preset.state.display.okLabelRu);
                    String okSub = DashboardPresetRegistry.pick(ctx, preset.state.display.okSub, preset.state.display.okSubRu);
                    sub = okSub != null ? okSub : "";
                    isAlert = preset.state.display.okAlert;
                }
                if (!label.isEmpty()) tile.setValue(label, "");
                else tile.setValue(triggered ? ctx.getString(R.string.dash_open) : ctx.getString(R.string.dash_all_closed), "");
                tile.setSub(sub);
                tile.setAlert(isAlert);
                break;
            }
            case "ha_status": {
                boolean enabled = AppConfig.isHassEnabled(ctx);
                tile.setValue(enabled ? "ON" : "OFF");
                tile.setAlert(!enabled);
                break;
            }
            default: {
                // Fallback: toggle behavior display based on primary sensor.
                if (preset.state.primarySensor != null && !preset.sensors.isEmpty()) {
                    CANDataItem item = byKey.get(preset.state.primarySensor);
                    if (item != null && !"---".equals(item.value)) {
                        String translated = SignalTranslator.translateEnumValue(preset.state.primarySensor, item.value);
                        boolean isOn = DashboardLogic.isStateTruthy(translated, preset.state.truthy, preset.state.truthyMode);
                        String label = isOn ? preset.state.display.onLabel : preset.state.display.offLabel;
                        String sub = isOn ? preset.state.display.onSub : preset.state.display.offSub;
                        boolean alert = isOn ? preset.state.display.onAlert : preset.state.display.offAlert;
                        if (!label.isEmpty()) tile.setValue(label, "");
                        else tile.setValue(translated, "");
                        tile.setSub(sub != null ? sub : "");
                        tile.setAlert(alert);
                    } else {
                        CANDataItem first = byKey.get(preset.sensors.get(0));
                        if (first != null && !"---".equals(first.value)) {
                            tile.setValue(SignalTranslator.translateValue(first.value), first.unit != null ? first.unit : "");
                        } else {
                            tile.setValue("—", "");
                        }
                        tile.setSub("");
                        tile.setAlert(false);
                    }
                } else {
                    tile.setValue("", "");
                    tile.setSub("");
                    tile.setAlert(false);
                }
                break;
            }
        }
    }
}
