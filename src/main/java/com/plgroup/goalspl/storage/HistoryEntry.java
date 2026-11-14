package com.plgroup.goalspl.storage;

import com.plgroup.goalspl.domain.HistoryAction;

/** Una línea del historial: cuándo, campaña, quién (jugador, CONSOLE, SYSTEM o un plugin), qué y detalles. */
public record HistoryEntry(long at, String campaignId, String actor, HistoryAction action, String details) {
}
