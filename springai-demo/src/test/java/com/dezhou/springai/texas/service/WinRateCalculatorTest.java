package com.dezhou.springai.texas.service;

import com.dezhou.springai.texas.model.Card;
import com.dezhou.springai.texas.model.Rank;
import com.dezhou.springai.texas.model.Suit;
import com.dezhou.springai.texas.model.WinRateResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WinRateCalculatorTest {

    @Test
    void ahKsOnQhJsThShouldHavePositiveEquity() {
        WinRateCalculator calculator = new WinRateCalculator();
        List<Card> hole = List.of(
                new Card(Suit.HEARTS, Rank.ACE),
                new Card(Suit.SPADES, Rank.KING)
        );
        List<Card> board = List.of(
                new Card(Suit.HEARTS, Rank.QUEEN),
                new Card(Suit.SPADES, Rank.JACK),
                new Card(Suit.HEARTS, Rank.TEN)
        );

        long t0 = System.currentTimeMillis();
        WinRateResult result = calculator.calculateWinRate(hole, board, 1, 1000);
        long elapsed = System.currentTimeMillis() - t0;

        System.out.printf("win=%.2f tie=%.2f lose=%.2f elapsed=%dms%n",
                result.getWinRate(), result.getTieRate(), result.getLoseRate(), elapsed);

        assertTrue(elapsed < 30_000, "calculation too slow: " + elapsed + "ms");
        double total = result.getWinRate() + result.getTieRate() + result.getLoseRate();
        assertTrue(total > 90.0, "rates should sum near 100, got " + total);
        assertTrue(result.getWinRate() > 0.0, "win rate should be > 0");
    }
}
