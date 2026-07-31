package com.diplustohass;

import java.util.List;

/**
 * Pure formatting/parsing helpers extracted from MainActivity so they can be
 * unit-tested without the Android runtime (see DashboardLogicTest).
 */
public final class DashboardLogic {

    private DashboardLogic() {
    }

    /** Format a double without a trailing ".0" for whole numbers. */
    public static String formatNumber(double value) {
        return value == (int) value ? String.valueOf((int) value) : String.valueOf(value);
    }

    /** Parse a double tolerating a comma decimal separator; 0 on garbage. */
    public static double parseDoubleSafe(String s) {
        if (s == null || s.isEmpty()) return 0;
        try {
            return Double.parseDouble(s.replace(",", "."));
        } catch (Exception e) {
            return 0;
        }
    }

    /** Join strings with a separator; empty string for null/empty input. */
    public static String joinStrings(List<String> list, String separator) {
        if (list == null || list.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(separator);
            sb.append(list.get(i));
        }
        return sb.toString();
    }

    /** Case-insensitive membership check used by dashboard preset states. */
    public static boolean isStateTruthy(String state, List<String> truthy) {
        if (truthy == null || truthy.isEmpty()) return false;
        if (state == null || state.isEmpty()) return false;
        for (String t : truthy) {
            if (t != null && t.equalsIgnoreCase(state)) return true;
        }
        return false;
    }

    /**
     * Truthiness check that also honours numeric modes:
     * "numeric_gt_0" (value > 0), "numeric_eq_0" (value == 0), otherwise list
     * membership via {@link #isStateTruthy}.
     */
    public static boolean isStateTruthy(String state, List<String> truthy, String truthyMode) {
        if ("numeric_gt_0".equals(truthyMode)) {
            if (state == null || state.isEmpty()) return false;
            try {
                return Double.parseDouble(state.replace(",", ".")) > 0;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        if ("numeric_eq_0".equals(truthyMode)) {
            if (state == null || state.isEmpty()) return false;
            try {
                return Double.parseDouble(state.replace(",", ".")) == 0;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return isStateTruthy(state, truthy);
    }
}
