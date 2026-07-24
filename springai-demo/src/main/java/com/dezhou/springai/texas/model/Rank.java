package com.dezhou.springai.texas.model;

public enum Rank {
    TWO('2', "2", 2),
    THREE('3', "3", 3),
    FOUR('4', "4", 4),
    FIVE('5', "5", 5),
    SIX('6', "6", 6),
    SEVEN('7', "7", 7),
    EIGHT('8', "8", 8),
    NINE('9', "9", 9),
    TEN('T', "10", 10),
    JACK('J', "J", 11),
    QUEEN('Q', "Q", 12),
    KING('K', "K", 13),
    ACE('A', "A", 14);

    private final char code;
    private final String chinese;
    private final int value;

    Rank(char code, String chinese, int value) {
        this.code = code;
        this.chinese = chinese;
        this.value = value;
    }

    public char getCode() {
        return code;
    }

    public String getChinese() {
        return chinese;
    }

    public int getValue() {
        return value;
    }

    public static Rank fromCode(char code) {
        char upper = Character.toUpperCase(code);
        if (upper == 'T') {
            return TEN;
        }
        for (Rank rank : values()) {
            if (rank.code == upper) {
                return rank;
            }
        }
        return null;
    }

    public static Rank fromChinese(String chinese) {
        if ("10".equals(chinese)) {
            return TEN;
        }
        for (Rank rank : values()) {
            if (rank.chinese.equals(chinese)) {
                return rank;
            }
        }
        return null;
    }

    public static Rank fromValue(int value) {
        for (Rank rank : values()) {
            if (rank.value == value) {
                return rank;
            }
        }
        return null;
    }
}