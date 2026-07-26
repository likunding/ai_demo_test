package com.dezhou.springai.texas.model;

/**
 * 德州位置（实战辅助用）。
 */
public enum Position {
    EP("前位"),
    MP("中位"),
    CO("关位"),
    BTN("庄家"),
    SB("小盲"),
    BB("大盲");

    private final String label;

    Position(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static Position fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Position.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
