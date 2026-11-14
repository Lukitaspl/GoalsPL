package com.plgroup.goalspl.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cuánto se acepta de cada aporte y cuándo se alcanza cada hito. */
class ProgressMathTest {

    @Test
    void acceptedIsCappedByRemainingGoal() {
        assertEquals(5, ProgressMath.accepted(95, 100, 0, 0, 20));
        assertEquals(0, ProgressMath.accepted(100, 100, 0, 0, 1));
        assertEquals(10, ProgressMath.accepted(0, 100, 0, 0, 10));
    }

    @Test
    void acceptedIsCappedByPlayerMaximum() {
        assertEquals(3, ProgressMath.accepted(0, 1000, 47, 50, 10));
        assertEquals(0, ProgressMath.accepted(0, 1000, 50, 50, 10));
        assertEquals(0, ProgressMath.accepted(0, 1000, 70, 50, 10));
    }

    @Test
    void nonPositiveAmountsAreIgnored() {
        assertEquals(0, ProgressMath.accepted(0, 100, 0, 0, 0));
        assertEquals(0, ProgressMath.accepted(0, 100, 0, 0, -5));
    }

    @Test
    void reachedUsesExactIntegerMath() {
        assertTrue(ProgressMath.reached(25, 100, 25));
        assertFalse(ProgressMath.reached(24, 100, 25));
        // 1/3 de 7: 33 % se alcanza con 3 (300 >= 231), no con 2 (200 < 231)
        assertTrue(ProgressMath.reached(3, 7, 33));
        assertFalse(ProgressMath.reached(2, 7, 33));
        assertTrue(ProgressMath.reached(7, 7, 100));
        assertFalse(ProgressMath.reached(6, 7, 100));
    }

    @Test
    void newlyReachedSkipsDoneAndKeepsOrder() {
        List<Integer> out = ProgressMath.newlyReached(80, 100, List.of(100, 75, 25, 50), Set.of(25));
        assertEquals(List.of(50, 75), out);
    }

    @Test
    void percentAndFractionStayInRange() {
        assertEquals(100, ProgressMath.percent(150, 100));
        assertEquals(0, ProgressMath.percent(5, 0));
        assertEquals(1f, ProgressMath.fraction(150, 100));
        assertEquals(0.5f, ProgressMath.fraction(50, 100));
    }

    @Test
    void endUsesWhicheverComesFirst() {
        Campaign c = new Campaign("test");
        assertEquals(0, c.endFor(1000));
        c.durationSeconds = 60;
        assertEquals(61_000, c.endFor(1000));
        c.deadline = 30_000;
        assertEquals(30_000, c.endFor(1000));
        c.durationSeconds = 0;
        assertEquals(30_000, c.endFor(1000));
    }
}
