package com.plgroup.goalspl.domain;

/** Tipos de entrada del historial (acciones de la administración, entregas, hitos y recompensas). */
public enum HistoryAction {
    CREATED,
    EDITED,
    DELETED,
    STARTED,
    PAUSED,
    RESUMED,
    COMPLETED,
    CANCELLED,
    EXPIRED,
    MILESTONE,
    PROGRESS,
    DELIVERY,
    REFUND,
    REWARD,
    REWARD_FAILED,
    NEXT,
    ERROR
}
