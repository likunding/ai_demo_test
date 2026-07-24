package com.dezhou.springai.texas.model;

public enum Suit {
    CLUBS('C', "梅花", '\u2633'),
    DIAMONDS('D', "方块", '\u2662'),
    HEARTS('H', "红桃", '\u2661'),
    SPADES('S', "黑桃", '\u2660');

    private final char code;
    private final String chinese;
    private final char symbol;

    Suit(char code, String chinese, char symbol) {
        this.code = code;
        this.chinese = chinese;
        this.symbol = symbol;
    }

    public char getCode() {
        return code;
    }

    public String getChinese() {
        return chinese;
    }

    public char getSymbol() {
        return symbol;
    }

    public static Suit fromCode(char code) {
        for (Suit suit : values()) {
            if (suit.code == Character.toUpperCase(code)) {
                return suit;
            }
        }
        return null;
    }

    public static Suit fromChinese(String chinese) {
        for (Suit suit : values()) {
            if (suit.chinese.equals(chinese)) {
                return suit;
            }
        }
        return null;
    }
}