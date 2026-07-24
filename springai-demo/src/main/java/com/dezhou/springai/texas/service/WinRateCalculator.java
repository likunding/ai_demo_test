package com.dezhou.springai.texas.service;

import codes.derive.foldem.Deck;
import codes.derive.foldem.Hand;
import codes.derive.foldem.board.Board;
import codes.derive.foldem.board.Boards;
import codes.derive.foldem.board.Street;
import codes.derive.foldem.eval.Evaluator;
import com.dezhou.springai.texas.model.GameState;
import com.dezhou.springai.texas.model.PlayerState;
import com.dezhou.springai.texas.model.WinRateResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static codes.derive.foldem.Poker.*;

/**
 * 基于 foldem 评估器的德州扑克胜率计算器。
 * 使用自研蒙特卡洛发牌，避免 foldem Range 抽样在公牌场景下的 "Card already dealt" 缺陷。
 */
@Slf4j
@Service
public class WinRateCalculator {

    private static final int DEFAULT_SAMPLE_SIZE = 10000;

    /**
     * 计算胜率（使用默认模拟次数）
     */
    public WinRateResult calculateWinRate(List<com.dezhou.springai.texas.model.Card> holeCards,
                                          List<com.dezhou.springai.texas.model.Card> communityCards,
                                          int numOpponents) {
        return calculateWinRate(holeCards, communityCards, numOpponents, DEFAULT_SAMPLE_SIZE);
    }

    /**
     * 计算胜率（指定模拟次数）
     */
    public WinRateResult calculateWinRate(List<com.dezhou.springai.texas.model.Card> holeCards,
                                          List<com.dezhou.springai.texas.model.Card> communityCards,
                                          int numOpponents, int sampleSize) {
        if (holeCards == null || holeCards.size() != 2) {
            return new WinRateResult(0.0, 0.0, 0.0, 0, 0, 0);
        }
        if (numOpponents < 1 || numOpponents > 8) {
            numOpponents = Math.max(1, Math.min(8, numOpponents));
        }
        if (sampleSize < 1) {
            sampleSize = DEFAULT_SAMPLE_SIZE;
        }

        try {
            String handNotation = toFoldemNotation(holeCards.get(0)) + toFoldemNotation(holeCards.get(1));
            Hand myHand = hand(handNotation);

            List<codes.derive.foldem.Card> knownBoardCards = new ArrayList<>();
            if (communityCards != null) {
                for (com.dezhou.springai.texas.model.Card card : communityCards) {
                    knownBoardCards.add(card(toFoldemNotation(card)));
                }
            }
            int boardSize = knownBoardCards.size();
            if (boardSize != 0 && boardSize != 3 && boardSize != 4 && boardSize != 5) {
                log.warn("[calculateWinRate] 非法公牌数量: {}", boardSize);
                return new WinRateResult(0.0, 0.0, 0.0, 0, 0, 0);
            }

            Evaluator evaluator = evaluator();
            int wins = 0;
            int ties = 0;
            int losses = 0;

            for (int i = 0; i < sampleSize; i++) {
                Deck deck = shuffledDeck();
                for (codes.derive.foldem.Card c : myHand.cards()) {
                    deck.pop(c);
                }
                for (codes.derive.foldem.Card c : knownBoardCards) {
                    deck.pop(c);
                }

                Hand[] players = new Hand[numOpponents + 1];
                players[0] = myHand;
                for (int o = 1; o <= numOpponents; o++) {
                    players[o] = hand(deck);
                }

                Board board = completeBoard(knownBoardCards, deck);
                int outcome = compareHero(evaluator, players, board);
                if (outcome > 0) {
                    wins++;
                } else if (outcome == 0) {
                    ties++;
                } else {
                    losses++;
                }
            }

            double winRate = wins * 100.0 / sampleSize;
            double tieRate = ties * 100.0 / sampleSize;
            double loseRate = losses * 100.0 / sampleSize;

            log.info("[calculateWinRate] hand={}, community={}, opponents={}, win={}%, tie={}%, lose={}%",
                    handNotation, communityCards, numOpponents,
                    String.format("%.2f", winRate),
                    String.format("%.2f", tieRate),
                    String.format("%.2f", loseRate));

            return new WinRateResult(winRate, tieRate, loseRate, wins, ties, losses);

        } catch (Exception e) {
            log.error("[calculateWinRate] 计算失败: {}", e.getMessage(), e);
            return new WinRateResult(0.0, 0.0, 0.0, 0, 0, 0);
        }
    }

    /**
     * 从游戏状态计算指定玩家的胜率
     */
    public WinRateResult calculateWinRateFromGame(String gameId, String playerId, GameState game) {
        if (game == null) {
            return new WinRateResult(0.0, 0.0, 0.0, 0, 0, 0);
        }

        PlayerState player = game.getPlayers().stream()
                .filter(p -> p.getPlayerId().equals(playerId))
                .findFirst()
                .orElse(null);

        if (player == null || player.isFolded()) {
            return new WinRateResult(0.0, 0.0, 0.0, 0, 0, 0);
        }

        int activeOpponents = (int) game.getPlayers().stream()
                .filter(p -> !p.getPlayerId().equals(playerId) && !p.isFolded())
                .count();

        if (activeOpponents == 0) {
            return new WinRateResult(100.0, 0.0, 0.0, 0, 0, 0);
        }

        return calculateWinRate(player.getHoleCards(), game.getCommunityCards(), activeOpponents);
    }

    /**
     * 将已有公牌补全到河牌。
     */
    private Board completeBoard(List<codes.derive.foldem.Card> knownBoardCards, Deck deck) {
        if (knownBoardCards.isEmpty()) {
            return Boards.board(deck, Street.RIVER);
        }
        Board partial = Boards.board(knownBoardCards.toArray(new codes.derive.foldem.Card[0]));
        return Boards.convert(partial, Street.RIVER, deck);
    }

    /**
     * 比较英雄相对所有对手的结果。
     * foldem 的 rank 越小越好。
     *
     * @return 1=胜, 0=平, -1=负
     */
    private int compareHero(Evaluator evaluator, Hand[] players, Board board) {
        int heroRank = evaluator.rank(players[0], board);
        int bestRank = heroRank;
        int bestCount = 1;

        for (int i = 1; i < players.length; i++) {
            int rank = evaluator.rank(players[i], board);
            if (rank < bestRank) {
                bestRank = rank;
                bestCount = 1;
            } else if (rank == bestRank) {
                bestCount++;
            }
        }

        if (heroRank > bestRank) {
            return -1;
        }
        if (bestCount > 1) {
            return 0;
        }
        return 1;
    }

    /**
     * 将项目 Card 转为 foldem 简写格式
     * 例如：黑桃A → "As"，红桃K → "Kh"，梅花10 → "Tc"
     */
    private String toFoldemNotation(com.dezhou.springai.texas.model.Card card) {
        return String.valueOf(card.getRank().getCode()) +
               Character.toLowerCase(card.getSuit().getCode());
    }
}
