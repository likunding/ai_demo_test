package com.dezhou.springai.texas.service;

import com.dezhou.springai.texas.model.*;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class HandEvaluator {

    public List<Card> createDeck() {
        List<Card> deck = new ArrayList<>();
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                deck.add(new Card(suit, rank));
            }
        }
        return deck;
    }

    public void shuffleDeck(List<Card> deck) {
        Collections.shuffle(deck, new Random());
    }

    public HandEvaluationResult evaluateHand(List<Card> cards) {
        if (cards == null || cards.size() < 5) {
            return new HandEvaluationResult(HandRank.HIGH_CARD, null, cards != null ? cards : List.of(), List.of());
        }

        List<Card> bestFive = findBestFive(cards);
        return evaluateFiveCards(bestFive);
    }

    private List<Card> findBestFive(List<Card> cards) {
        if (cards.size() == 5) {
            return cards;
        }

        HandEvaluationResult bestResult = null;
        List<Card> bestFive = null;

        List<List<Card>> combinations = generateCombinations(cards, 5);
        for (List<Card> combo : combinations) {
            HandEvaluationResult result = evaluateFiveCards(combo);
            if (bestResult == null || compareResults(result, bestResult) > 0) {
                bestResult = result;
                bestFive = combo;
            }
        }

        return bestFive != null ? bestFive : cards.subList(0, 5);
    }

    private List<List<Card>> generateCombinations(List<Card> cards, int k) {
        List<List<Card>> combinations = new ArrayList<>();
        generateCombinationsHelper(cards, k, 0, new ArrayList<>(), combinations);
        return combinations;
    }

    private void generateCombinationsHelper(List<Card> cards, int k, int start, List<Card> current, List<List<Card>> combinations) {
        if (current.size() == k) {
            combinations.add(new ArrayList<>(current));
            return;
        }
        for (int i = start; i < cards.size(); i++) {
            current.add(cards.get(i));
            generateCombinationsHelper(cards, k, i + 1, current, combinations);
            current.remove(current.size() - 1);
        }
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
        return Integer.compare(k1.size(), k2.size());
    }

    private HandEvaluationResult evaluateFiveCards(List<Card> cards) {
        List<Card> sorted = new ArrayList<>(cards);
        sorted.sort(Comparator.comparing(Card::getRank).reversed());

        Map<Suit, List<Card>> suitGroups = groupBySuit(sorted);
        Map<Rank, List<Card>> rankGroups = groupByRank(sorted);

        boolean isFlush = isFlush(suitGroups);
        boolean isStraight = isStraight(sorted);
        boolean isRoyal = isRoyal(sorted);

        if (isFlush && isRoyal) {
            return new HandEvaluationResult(HandRank.ROYAL_FLUSH, "皇家同花顺", sorted, List.of());
        }

        if (isFlush && isStraight) {
            int highCard = sorted.get(0).getRank().getValue();
            return new HandEvaluationResult(HandRank.STRAIGHT_FLUSH, "同花顺 " + sorted.get(0).getRank().getChinese(), sorted, List.of(highCard));
        }

        int fourOfAKindRank = findNOfAKind(rankGroups, 4);
        if (fourOfAKindRank != 0) {
            List<Integer> kickers = getKickers(rankGroups, List.of(fourOfAKindRank));
            return new HandEvaluationResult(HandRank.FOUR_OF_A_KIND, "四条 " + Rank.fromValue(fourOfAKindRank).getChinese(), sorted, kickers);
        }

        int threeOfAKindRank = findNOfAKind(rankGroups, 3);
        int pairRank = findNOfAKind(rankGroups, 2);
        if (threeOfAKindRank != 0 && pairRank != 0) {
            List<Integer> kickers = getKickers(rankGroups, List.of(threeOfAKindRank, pairRank));
            return new HandEvaluationResult(HandRank.FULL_HOUSE,
                    "葫芦 " + Rank.fromValue(threeOfAKindRank).getChinese() + "带" + Rank.fromValue(pairRank).getChinese(),
                    sorted, kickers);
        }

        if (isFlush) {
            List<Integer> kickers = sorted.stream().map(c -> c.getRank().getValue()).toList();
            return new HandEvaluationResult(HandRank.FLUSH, "同花", sorted, kickers);
        }

        if (isStraight) {
            int highCard = sorted.get(0).getRank().getValue();
            return new HandEvaluationResult(HandRank.STRAIGHT, "顺子 " + sorted.get(0).getRank().getChinese(), sorted, List.of(highCard));
        }

        if (threeOfAKindRank != 0) {
            List<Integer> kickers = getKickers(rankGroups, List.of(threeOfAKindRank));
            return new HandEvaluationResult(HandRank.THREE_OF_A_KIND, "三条 " + Rank.fromValue(threeOfAKindRank).getChinese(), sorted, kickers);
        }

        List<Integer> pairRanks = findPairs(rankGroups);
        if (pairRanks.size() >= 2) {
            List<Integer> kickers = getKickers(rankGroups, pairRanks);
            return new HandEvaluationResult(HandRank.TWO_PAIR,
                    "两对 " + Rank.fromValue(pairRanks.get(0)).getChinese() + "和" + Rank.fromValue(pairRanks.get(1)).getChinese(),
                    sorted, kickers);
        }

        if (pairRank != 0) {
            List<Integer> kickers = getKickers(rankGroups, List.of(pairRank));
            return new HandEvaluationResult(HandRank.ONE_PAIR, "一对 " + Rank.fromValue(pairRank).getChinese(), sorted, kickers);
        }

        List<Integer> kickers = sorted.stream().map(c -> c.getRank().getValue()).toList();
        return new HandEvaluationResult(HandRank.HIGH_CARD, "高牌 " + sorted.get(0).getRank().getChinese(), sorted, kickers);
    }

    private Map<Suit, List<Card>> groupBySuit(List<Card> cards) {
        Map<Suit, List<Card>> groups = new HashMap<>();
        for (Card card : cards) {
            groups.computeIfAbsent(card.getSuit(), k -> new ArrayList<>()).add(card);
        }
        return groups;
    }

    private Map<Rank, List<Card>> groupByRank(List<Card> cards) {
        Map<Rank, List<Card>> groups = new HashMap<>();
        for (Card card : cards) {
            groups.computeIfAbsent(card.getRank(), k -> new ArrayList<>()).add(card);
        }
        return groups;
    }

    private boolean isFlush(Map<Suit, List<Card>> suitGroups) {
        return suitGroups.values().stream().anyMatch(list -> list.size() >= 5);
    }

    private boolean isStraight(List<Card> sortedCards) {
        List<Integer> ranks = sortedCards.stream().map(c -> c.getRank().getValue()).distinct().sorted(Comparator.reverseOrder()).toList();
        if (ranks.size() < 5) return false;

        for (int i = 0; i <= ranks.size() - 5; i++) {
            boolean straight = true;
            for (int j = 0; j < 4; j++) {
                if (ranks.get(i + j) - ranks.get(i + j + 1) != 1) {
                    straight = false;
                    break;
                }
            }
            if (straight) return true;
        }

        if (ranks.contains(14) && ranks.contains(5) && ranks.contains(4) && ranks.contains(3) && ranks.contains(2)) {
            return true;
        }

        return false;
    }

    private boolean isRoyal(List<Card> sortedCards) {
        Set<Rank> required = Set.of(Rank.TEN, Rank.JACK, Rank.QUEEN, Rank.KING, Rank.ACE);
        return sortedCards.stream().map(Card::getRank).collect(java.util.stream.Collectors.toSet()).containsAll(required);
    }

    private int findNOfAKind(Map<Rank, List<Card>> rankGroups, int n) {
        return rankGroups.entrySet().stream()
                .filter(entry -> entry.getValue().size() >= n)
                .map(entry -> entry.getKey().getValue())
                .max(Integer::compare)
                .orElse(0);
    }

    private List<Integer> findPairs(Map<Rank, List<Card>> rankGroups) {
        return rankGroups.entrySet().stream()
                .filter(entry -> entry.getValue().size() >= 2)
                .map(entry -> entry.getKey().getValue())
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    private List<Integer> getKickers(Map<Rank, List<Card>> rankGroups, List<Integer> excludeRanks) {
        return rankGroups.entrySet().stream()
                .filter(entry -> !excludeRanks.contains(entry.getKey().getValue()))
                .map(entry -> entry.getKey().getValue())
                .sorted(Comparator.reverseOrder())
                .toList();
    }
}