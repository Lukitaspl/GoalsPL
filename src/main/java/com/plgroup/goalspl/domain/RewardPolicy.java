package com.plgroup.goalspl.domain;

/**
 * A quién se entregan las recompensas por jugador de un hito. {@code COMMUNITY}: a todos los que han aportado algo
 * y a quien esté conectado en ese momento. {@code PARTICIPANTS}: solo a quien llegó al mínimo de aportación de la
 * campaña ({@code min-contribution}).
 */
public enum RewardPolicy {
    COMMUNITY,
    PARTICIPANTS
}
