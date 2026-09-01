package com.car2hass.rules;

import android.content.Context;

import java.util.Objects;

public enum RuleOperator {

    EQ {
        @Override public boolean apply(String actual, String expected) {
            if (actual == null || expected == null) return false;
            return actual.equalsIgnoreCase(expected);
        }
    },

    NEQ {
        @Override public boolean apply(String actual, String expected) {
            if (actual == null || expected == null) return false;
            return !actual.equalsIgnoreCase(expected);
        }
    },

    GT {
        @Override public boolean apply(String actual, String expected) {
            Double a = tryParse(actual);
            Double e = tryParse(expected);
            return a != null && e != null && a > e;
        }
    },

    LT {
        @Override public boolean apply(String actual, String expected) {
            Double a = tryParse(actual);
            Double e = tryParse(expected);
            return a != null && e != null && a < e;
        }
    },

    GTE {
        @Override public boolean apply(String actual, String expected) {
            Double a = tryParse(actual);
            Double e = tryParse(expected);
            return a != null && e != null && a >= e;
        }
    },

    LTE {
        @Override public boolean apply(String actual, String expected) {
            Double a = tryParse(actual);
            Double e = tryParse(expected);
            return a != null && e != null && a <= e;
        }
    };

    public abstract boolean apply(String actual, String expected);

    public String getLabel(Context ctx) {
        String[] names = {"rules_operator_eq", "rules_operator_neq", "rules_operator_gt",
                "rules_operator_lt", "rules_operator_gte", "rules_operator_lte"};
        int idx = ordinal();
        if (idx >= 0 && idx < names.length) {
            int resId = ctx.getResources().getIdentifier(names[idx], "string", ctx.getPackageName());
            if (resId != 0) {
                try { return ctx.getString(resId); } catch (Exception ignored) {}
            }
        }
        switch (this) {
            case EQ: return "= equals";
            case NEQ: return "\u2260 not equal";
            case GT: return "> greater than";
            case LT: return "< less than";
            case GTE: return "\u2265 greater or equal";
            case LTE: return "\u2264 less or equal";
            default: return name();
        }
    }

    private static Double tryParse(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return Double.parseDouble(s.replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
