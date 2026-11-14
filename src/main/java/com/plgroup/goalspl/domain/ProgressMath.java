package com.plgroup.goalspl.domain;

import java.util.ArrayList;
import java.util.List;

/** Cálculos de progreso sin servidor (probados con JUnit): cuánto se acepta y qué hitos se cruzan. */
public final class ProgressMath {

    private ProgressMath() {
    }

    /**
     * Cuánto de {@code amount} se acepta: nunca más de lo que falta para la meta ni de lo que le queda al jugador
     * hasta {@code max} (0 = sin límite). Nunca negativo.
     */
    public static long accepted(long progress, long goal, long playerContribution, long max, long amount) {
        if (amount <= 0) {
            return 0;
        }
        long left = Math.max(0, goal - progress);
        long accepted = Math.min(amount, left);
        if (max > 0) {
            accepted = Math.min(accepted, Math.max(0, max - playerContribution));
        }
        return Math.max(0, accepted);
    }

    /** Si {@code progress} llega al {@code percent} % de {@code goal} (sin redondeos de coma flotante). */
    public static boolean reached(long progress, long goal, int percent) {
        if (goal <= 0) {
            return false;
        }
        return progress * 100L >= (long) percent * goal;
    }

    /** Porcentaje entero (0-100) para mostrar. */
    public static int percent(long progress, long goal) {
        if (goal <= 0) {
            return 0;
        }
        return (int) Math.min(100, progress * 100L / goal);
    }

    /** Fracción 0-1 para la bossbar. */
    public static float fraction(long progress, long goal) {
        if (goal <= 0) {
            return 0f;
        }
        return (float) Math.max(0d, Math.min(1d, (double) progress / goal));
    }

    /** Porcentajes de {@code percents} alcanzados con {@code progress} que no están en {@code done}, en orden. */
    public static List<Integer> newlyReached(long progress, long goal, List<Integer> percents, java.util.Set<Integer> done) {
        List<Integer> out = new ArrayList<>();
        for (int p : percents.stream().sorted().toList()) {
            if (!done.contains(p) && reached(progress, goal, p)) {
                out.add(p);
            }
        }
        return out;
    }
}
