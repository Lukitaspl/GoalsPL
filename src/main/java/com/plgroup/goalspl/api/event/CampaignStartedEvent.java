package com.plgroup.goalspl.api.event;

import com.plgroup.goalspl.api.CampaignInfo;
import org.bukkit.event.HandlerList;

/**
 * Una campaña se ha iniciado (publicado) o reanudado tras una pausa ({@link #resumed()}). {@link #previous()} es la
 * campaña cuya finalización la inició como etapa siguiente ({@code null} si no).
 */
public final class CampaignStartedEvent extends CampaignEvent {

    private static final HandlerList HANDLERS = new HandlerList();
    private final String actor;
    private final String previous;
    private final boolean resumed;

    public CampaignStartedEvent(CampaignInfo campaign, String actor, String previous, boolean resumed) {
        super(campaign);
        this.actor = actor;
        this.previous = previous;
        this.resumed = resumed;
    }

    /** Quién la inició: nombre del jugador, CONSOLE, SYSTEM o el nombre de un plugin. */
    public String actor() {
        return actor;
    }

    public String previous() {
        return previous;
    }

    public boolean resumed() {
        return resumed;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
