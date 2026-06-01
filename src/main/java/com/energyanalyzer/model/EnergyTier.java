package com.energyanalyzer.model;

/**
 * Energy efficiency tier classification for a method.
 *
 * Derived from combination of:
 *  - Complexity class
 *  - Number of anti-patterns detected
 *  - Final EEI score
 *
 * Used for color-coding in the dashboard and build gate decisions.
 */
public enum EnergyTier {

    /**
     * LOW impact - efficient code, minimal anti-patterns.
     * EEI: 70–100. Dashboard color: green.
     */
    LOW("Low Impact", "#22c55e", "✓ Efficient"),

    /**
     * MEDIUM impact - some inefficiencies, worth reviewing.
     * EEI: 50–69. Dashboard color: yellow.
     */
    MEDIUM("Medium Impact", "#eab308", "⚠ Review Recommended"),

    /**
     * HIGH impact - significant inefficiencies detected.
     * EEI: 30–49. Dashboard color: orange.
     */
    HIGH("High Impact", "#f97316", "✗ Optimization Required"),

    /**
     * CRITICAL impact - severely inefficient, likely to degrade performance.
     * EEI: 0–29. Dashboard color: red. Triggers build failure.
     */
    CRITICAL("Critical Impact", "#ef4444", "✗✗ Critical - Immediate Action");

    private final String label;
    private final String hexColor;
    private final String actionMessage;

    EnergyTier(String label, String hexColor, String actionMessage) {
        this.label = label;
        this.hexColor = hexColor;
        this.actionMessage = actionMessage;
    }

    public String getLabel() { return label; }
    public String getHexColor() { return hexColor; }
    public String getActionMessage() { return actionMessage; }

    /** Determine tier from EEI score */
    public static EnergyTier fromEei(double eei) {
        if (eei >= 70) return LOW;
        if (eei >= 50) return MEDIUM;
        if (eei >= 30) return HIGH;
        return CRITICAL;
    }
}
