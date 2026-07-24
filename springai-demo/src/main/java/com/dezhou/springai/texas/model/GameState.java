package com.dezhou.springai.texas.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameState {

    private String gameId;
    private List<PlayerState> players;
    private List<Card> communityCards;
    private int currentPlayerIndex;
    private int round;
    private int pot;
    private int bigBlind;
    private int smallBlind;
    private int currentBet;
    private int dealerIndex;
    private boolean isGameOver;

    public enum Round {
        PRE_FLOP,
        FLOP,
        TURN,
        RIVER,
        SHOWDOWN
    }

    public GameState(String gameId, int initialChips, int bigBlind) {
        this.gameId = gameId;
        this.players = new ArrayList<>();
        this.communityCards = new ArrayList<>();
        this.currentPlayerIndex = 0;
        this.round = 0;
        this.pot = 0;
        this.bigBlind = bigBlind;
        this.smallBlind = bigBlind / 2;
        this.currentBet = 0;
        this.dealerIndex = 0;
        this.isGameOver = false;
    }

    public void addPlayer(String playerId, String playerName, int chips) {
        players.add(new PlayerState(playerId, playerName, chips));
    }

    public PlayerState getCurrentPlayer() {
        return players.get(currentPlayerIndex);
    }

    public void nextPlayer() {
        int numPlayers = players.size();
        int startIndex = currentPlayerIndex;
        do {
            currentPlayerIndex = (currentPlayerIndex + 1) % numPlayers;
            PlayerState player = players.get(currentPlayerIndex);
            if (!player.isFolded() && !player.isAllIn()) {
                return;
            }
        } while (currentPlayerIndex != startIndex);
    }

    public void advanceRound() {
        round++;
        for (PlayerState player : players) {
            player.resetBet();
        }
        currentBet = 0;
        currentPlayerIndex = (dealerIndex + 1) % players.size();
    }

    public void collectBets() {
        int totalBets = 0;
        for (PlayerState player : players) {
            totalBets += player.getBet();
            player.setBet(0);
        }
        pot += totalBets;
        currentBet = 0;
    }

    public List<PlayerState> getActivePlayers() {
        return players.stream()
                .filter(p -> !p.isFolded())
                .toList();
    }

    public boolean allPlayersCheckedOrFolded() {
        return players.stream()
                .filter(p -> !p.isFolded())
                .allMatch(p -> p.getBet() == currentBet || p.isAllIn());
    }
}