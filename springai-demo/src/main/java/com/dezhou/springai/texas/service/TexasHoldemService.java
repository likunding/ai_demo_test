package com.dezhou.springai.texas.service;

import com.dezhou.springai.texas.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TexasHoldemService {

    private final HandEvaluator handEvaluator;
    private final Map<String, GameState> games = new HashMap<>();

    public GameState createGame(String gameId, int initialChips, int bigBlind) {
        GameState game = new GameState(gameId, initialChips, bigBlind);
        games.put(gameId, game);
        log.info("[createGame] gameId={}, initialChips={}, bigBlind={}", gameId, initialChips, bigBlind);
        return game;
    }

    public GameState getGame(String gameId) {
        return games.get(gameId);
    }

    public void deleteGame(String gameId) {
        games.remove(gameId);
    }

    public GameState addPlayer(String gameId, String playerId, String playerName, int chips) {
        GameState game = getGame(gameId);
        if (game != null) {
            game.addPlayer(playerId, playerName, chips);
            log.info("[addPlayer] gameId={}, playerId={}, playerName={}, chips={}", gameId, playerId, playerName, chips);
        }
        return game;
    }

    public GameState startGame(String gameId) {
        GameState game = getGame(gameId);
        if (game == null || game.getPlayers().size() < 2) {
            return game;
        }

        List<Card> deck = handEvaluator.createDeck();
        handEvaluator.shuffleDeck(deck);

        for (PlayerState player : game.getPlayers()) {
            player.clearHoleCards();
            player.setFolded(false);
            player.setAllIn(false);
            player.resetBet();
        }

        game.setCommunityCards(new ArrayList<>());
        game.setPot(0);
        game.setRound(0);
        game.setCurrentBet(0);
        game.setGameOver(false);

        int cardIndex = 0;
        for (PlayerState player : game.getPlayers()) {
            player.addHoleCard(deck.get(cardIndex++));
            player.addHoleCard(deck.get(cardIndex++));
        }

        PlayerState smallBlindPlayer = game.getPlayers().get((game.getDealerIndex() + 1) % game.getPlayers().size());
        PlayerState bigBlindPlayer = game.getPlayers().get((game.getDealerIndex() + 2) % game.getPlayers().size());

        smallBlindPlayer.call(game.getSmallBlind());
        bigBlindPlayer.call(game.getBigBlind());
        game.setCurrentBet(game.getBigBlind());
        game.collectBets();

        game.setCurrentPlayerIndex((game.getDealerIndex() + 3) % game.getPlayers().size());

        log.info("[startGame] gameId={}, players={}, dealerIndex={}", gameId, game.getPlayers().size(), game.getDealerIndex());
        return game;
    }

    public GameState dealFlop(String gameId) {
        GameState game = getGame(gameId);
        if (game == null || game.getRound() != 0) {
            return game;
        }

        List<Card> deck = handEvaluator.createDeck();
        Set<String> usedCards = new HashSet<>();
        for (PlayerState player : game.getPlayers()) {
            for (Card card : player.getHoleCards()) {
                usedCards.add(card.toCode());
            }
        }
        for (Card card : game.getCommunityCards()) {
            usedCards.add(card.toCode());
        }
        deck.removeIf(c -> usedCards.contains(c.toCode()));
        handEvaluator.shuffleDeck(deck);

        game.getCommunityCards().add(deck.get(0));
        game.getCommunityCards().add(deck.get(1));
        game.getCommunityCards().add(deck.get(2));

        game.advanceRound();
        game.setCurrentPlayerIndex((game.getDealerIndex() + 1) % game.getPlayers().size());

        log.info("[dealFlop] gameId={}, communityCards={}", gameId, game.getCommunityCards());
        return game;
    }

    public GameState dealTurn(String gameId) {
        GameState game = getGame(gameId);
        if (game == null || game.getRound() != 1) {
            return game;
        }

        List<Card> deck = handEvaluator.createDeck();
        Set<String> usedCards = new HashSet<>();
        for (PlayerState player : game.getPlayers()) {
            for (Card card : player.getHoleCards()) {
                usedCards.add(card.toCode());
            }
        }
        for (Card card : game.getCommunityCards()) {
            usedCards.add(card.toCode());
        }
        deck.removeIf(c -> usedCards.contains(c.toCode()));
        handEvaluator.shuffleDeck(deck);

        game.getCommunityCards().add(deck.get(0));
        game.advanceRound();

        log.info("[dealTurn] gameId={}, communityCards={}", gameId, game.getCommunityCards());
        return game;
    }

    public GameState dealRiver(String gameId) {
        GameState game = getGame(gameId);
        if (game == null || game.getRound() != 2) {
            return game;
        }

        List<Card> deck = handEvaluator.createDeck();
        Set<String> usedCards = new HashSet<>();
        for (PlayerState player : game.getPlayers()) {
            for (Card card : player.getHoleCards()) {
                usedCards.add(card.toCode());
            }
        }
        for (Card card : game.getCommunityCards()) {
            usedCards.add(card.toCode());
        }
        deck.removeIf(c -> usedCards.contains(c.toCode()));
        handEvaluator.shuffleDeck(deck);

        game.getCommunityCards().add(deck.get(0));
        game.advanceRound();

        log.info("[dealRiver] gameId={}, communityCards={}", gameId, game.getCommunityCards());
        return game;
    }

    public GameState playerAction(String gameId, String playerId, String action, Integer amount) {
        GameState game = getGame(gameId);
        if (game == null || game.isGameOver()) {
            return game;
        }

        PlayerState player = game.getPlayers().stream()
                .filter(p -> p.getPlayerId().equals(playerId))
                .findFirst()
                .orElse(null);

        if (player == null || player.isFolded()) {
            return game;
        }

        log.info("[playerAction] gameId={}, playerId={}, action={}, amount={}", gameId, playerId, action, amount);

        switch (action.toLowerCase()) {
            case "fold":
                player.fold();
                break;
            case "call":
                player.call(game.getCurrentBet());
                game.collectBets();
                break;
            case "raise":
                if (amount != null && amount > game.getCurrentBet()) {
                    player.raise(amount);
                    game.setCurrentBet(amount);
                    game.collectBets();
                }
                break;
            case "check":
                if (player.getBet() == game.getCurrentBet()) {
                    break;
                }
                return game;
            default:
                return game;
        }

        game.nextPlayer();

        if (game.getActivePlayers().size() == 1) {
            game.setGameOver(true);
            log.info("[gameOver] gameId={}, winner={}", gameId, game.getActivePlayers().get(0).getPlayerName());
        }

        return game;
    }

    public List<PlayerState> getWinners(String gameId) {
        GameState game = getGame(gameId);
        if (game == null) {
            return List.of();
        }

        List<PlayerState> activePlayers = game.getActivePlayers();
        if (activePlayers.size() == 1) {
            return activePlayers;
        }

        List<PlayerState> winners = new ArrayList<>();
        HandEvaluationResult bestResult = null;

        for (PlayerState player : activePlayers) {
            List<Card> allCards = new ArrayList<>(player.getHoleCards());
            allCards.addAll(game.getCommunityCards());
            HandEvaluationResult result = handEvaluator.evaluateHand(allCards);

            if (bestResult == null || compareResults(result, bestResult) > 0) {
                bestResult = result;
                winners.clear();
                winners.add(player);
            } else if (compareResults(result, bestResult) == 0) {
                winners.add(player);
            }
        }

        return winners;
    }

    private int compareResults(HandEvaluationResult r1, HandEvaluationResult r2) {
        int rankCompare = Integer.compare(r1.getHandRank().getRank(), r2.getHandRank().getRank());
        if (rankCompare != 0) {
            return rankCompare;
        }
        List<Integer> k1 = r1.getKickers();
        List<Integer> k2 = r2.getKickers();
        for (int i = 0; i < Math.min(k1.size(), k2.size()); i++) {
            int kickerCompare = Integer.compare(k1.get(i), k2.get(i));
            if (kickerCompare != 0) {
                return kickerCompare;
            }
        }
        return 0;
    }

    public HandEvaluationResult evaluatePlayerHand(String gameId, String playerId) {
        GameState game = getGame(gameId);
        if (game == null) {
            return null;
        }

        PlayerState player = game.getPlayers().stream()
                .filter(p -> p.getPlayerId().equals(playerId))
                .findFirst()
                .orElse(null);

        if (player == null) {
            return null;
        }

        List<Card> allCards = new ArrayList<>(player.getHoleCards());
        allCards.addAll(game.getCommunityCards());
        return handEvaluator.evaluateHand(allCards);
    }

    public String getRoundName(int round) {
        return switch (round) {
            case 0 -> "翻牌前";
            case 1 -> "翻牌";
            case 2 -> "转牌";
            case 3 -> "河牌";
            case 4 -> "摊牌";
            default -> "未知";
        };
    }
}