package com.car2hass.rules;

public class RuleDescribeTest {
    private static void require(String haystack, String needle) {
        if (!haystack.contains(needle))
            throw new AssertionError("expected config to contain '" + needle + "' but was: " + haystack);
    }

    public static void main(String[] args) {
        Rule r = new Rule();
        r.name = "Seat vent";
        r.enabled = true;
        r.fireOnRisingEdge = true;
        r.fireOncePerSession = false;
        r.minIntervalSec = 30;
        r.holdSeconds = 5;

        RuleCondition c = new RuleCondition("cabin_temp", RuleOperator.GT, "25");
        c.negated = true;
        c.triggerOnChange = true;
        r.conditions.add(c);

        r.actions.add(new RuleAction("driver_seat_vent", "high"));
        r.actionsOnFalse.add(new RuleAction("driver_seat_vent", "off"));

        String s = RuleDescribe.config(r);
        require(s, "name='Seat vent'");
        require(s, "enabled=true");
        require(s, "rising=true");
        require(s, "hold=5s");
        require(s, "min=30s");
        require(s, "once=false");
        require(s, "cabin_temp GT '25'");
        require(s, "conn=AND");
        require(s, "neg=true");
        require(s, "trig=true");
        require(s, "actions=[driver_seat_vent:high]");
        require(s, "else=[driver_seat_vent:off]");

        System.out.println("All RuleDescribe tests passed.");
    }
}
