package com.dezhou.springai.texas.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlayerState {

    private String playerId;
    private String playerName;
    private int chips;
    private int bet;
    private int fold;
    private boolean isFolded;
    private boolean isAllIn;
    private List<Card> holeCards;

    public PlayerState(String playerId, String playerName, int chips) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.chips = chips;
        this.bet = 0;
        this.fold = 0;
        this.isFolded = false;
        this.isAllIn = false;
        this.holeCards = new ArrayList<>();
    }

    public void resetBet() {
        this.bet = 0;
    }

    public void fold() {
        this.isFolded = true;
        this.fold++;
    }

    public void call(int amount) {
        int actualCall = Math.min(amount - this.bet, this.chips);
        this.chips -= actualCall;
        this.bet += actualCall;
        if (this.chips == 0) {
            this.isAllIn = true;
        }
    }

    public void raise(int amount) {
        int raiseAmount = amount - this.bet;
        this.chips -= raiseAmount;
        this.bet = amount;
        if (this.chips == 0) {
            this.isAllIn = true;
        }
    }

    public void addHoleCard(Card card) {
        if (holeCards.size() < 2) {
            holeCards.add(card);
        }
    }

    public void clearHoleCards() {
        holeCards.clear();
    }
}