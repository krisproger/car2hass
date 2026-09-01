package com.car2hass;

/**
 * One quick-action button shown on the Commands tab.
 */
public class QuickCommand {
    public final String commandId;
    public final String icon;
    public final String label;

    public QuickCommand(String commandId, String icon, String label) {
        this.commandId = commandId;
        this.icon = icon;
        this.label = label;
    }
}
