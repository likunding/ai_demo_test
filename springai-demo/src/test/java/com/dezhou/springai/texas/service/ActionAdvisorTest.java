package com.dezhou.springai.texas.service;

import com.dezhou.springai.texas.model.ActionAdvice;
import com.dezhou.springai.texas.model.ActionSpot;
import com.dezhou.springai.texas.model.Position;
import com.dezhou.springai.texas.model.SuggestedAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ActionAdvisorTest {

    private ActionAdvisor advisor;

    @BeforeEach
    void setUp() {
        advisor = new ActionAdvisor();
    }

    @Test
    void facingRaise_shouldFoldWhenEquityBelowPotOdds() {
        // pot=100, toCall=100 → potOdds=50%
        ActionAdvice advice = advisor.advise(30, 100, 100, Position.BTN, ActionSpot.FACING_RAISE, 10.0);
        assertEquals(SuggestedAction.FOLD, advice.getAction());
        assertEquals(0.5, advice.getPotOdds(), 0.001);
        assertTrue(advice.getReason().contains("弃牌"));
    }

    @Test
    void facingRaise_shouldCallWhenEquityJustAbovePotOdds() {
        ActionAdvice advice = advisor.advise(55, 100, 100, Position.BTN, ActionSpot.FACING_RAISE, 10.0);
        assertEquals(SuggestedAction.CALL, advice.getAction());
    }

    @Test
    void facingRaise_shouldRaiseWhenStrongEdge() {
        ActionAdvice advice = advisor.advise(80, 100, 100, Position.BTN, ActionSpot.FACING_RAISE, 10.0);
        assertEquals(SuggestedAction.RAISE, advice.getAction());
        assertNotNull(advice.getRaiseTo());
        assertTrue(advice.getRaiseTo() > 100);
    }

    @Test
    void everyoneChecked_shouldCheckWhenWeak() {
        ActionAdvice advice = advisor.advise(25, 60, 0, Position.BTN, ActionSpot.EVERYONE_CHECKED, 10.0);
        assertEquals(SuggestedAction.CHECK, advice.getAction());
    }

    @Test
    void everyoneChecked_shouldBetWhenDecent() {
        ActionAdvice advice = advisor.advise(45, 60, 0, Position.BTN, ActionSpot.EVERYONE_CHECKED, 10.0);
        assertEquals(SuggestedAction.RAISE, advice.getAction());
        assertNotNull(advice.getRaiseTo());
        assertEquals(30.0, advice.getRaiseTo(), 0.1); // half pot
    }

    @Test
    void openUnopened_btnWide_shouldRaise() {
        ActionAdvice advice = advisor.advise(40, 15, 0, Position.BTN, ActionSpot.OPEN_UNOPENED, 10.0);
        assertEquals(SuggestedAction.RAISE, advice.getAction());
        assertNotNull(advice.getRaiseTo());
    }

    @Test
    void openUnopened_epWeak_shouldFold() {
        ActionAdvice advice = advisor.advise(40, 15, 0, Position.EP, ActionSpot.OPEN_UNOPENED, 10.0);
        assertEquals(SuggestedAction.FOLD, advice.getAction());
    }

    @Test
    void openUnopened_bbCanCheck() {
        ActionAdvice advice = advisor.advise(30, 15, 0, Position.BB, ActionSpot.OPEN_UNOPENED, 10.0);
        assertEquals(SuggestedAction.CHECK, advice.getAction());
    }
}
