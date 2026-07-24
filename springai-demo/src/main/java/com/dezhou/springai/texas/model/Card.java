package com.dezhou.springai.texas.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Card implements Comparable<Card> {

    private Suit suit;
    private Rank rank;

    public static Card fromCode(String code) {
        if (code == null || code.length() != 2) {
            return null;
        }
        char rankChar = code.charAt(0);
        char suitChar = code.charAt(1);
        return new Card(Suit.fromCode(suitChar), Rank.fromCode(rankChar));
    }

    public String toCode() {
        return String.valueOf(rank.getCode()) + String.valueOf(suit.getCode());
    }

    public String toChinese() {
        return suit.getChinese() + rank.getChinese();
    }

    @Override
    public int compareTo(Card other) {
        int rankCompare = this.rank.compareTo(other.rank);
        if (rankCompare != 0) {
            return rankCompare;
        }
        return this.suit.compareTo(other.suit);
    }

    @Override
    public String toString() {
        return toChinese();
    }
}