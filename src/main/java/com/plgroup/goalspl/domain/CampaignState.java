package com.plgroup.goalspl.domain;

/**
 * Estado de una campaña. {@code DRAFT}: creada y sin publicar (o terminada y lista para volver a empezar).
 * {@code ACTIVE} y {@code PAUSED} son una ejecución en curso; {@code COMPLETED}, {@code CANCELLED} y
 * {@code EXPIRED} son finales hasta que se vuelva a iniciar (nueva ejecución, progreso a cero).
 */
public enum CampaignState {
    DRAFT,
    ACTIVE,
    PAUSED,
    COMPLETED,
    CANCELLED,
    EXPIRED;

    public boolean running() {
        return this == ACTIVE || this == PAUSED;
    }

    public boolean finished() {
        return this == COMPLETED || this == CANCELLED || this == EXPIRED;
    }

    /** Si desde este estado se puede iniciar una ejecución nueva. */
    public boolean canStart() {
        return !running();
    }
}
