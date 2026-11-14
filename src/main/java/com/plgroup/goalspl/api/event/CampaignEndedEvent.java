package com.plgroup.goalspl.api.event;

import com.plgroup.goalspl.api.CampaignInfo;
import com.plgroup.goalspl.domain.CampaignState;
import org.bukkit.event.HandlerList;

/**
 * Una ejecución ha terminado o se ha detenido: {@link #outcome()} es {@code COMPLETED}, {@code CANCELLED},
 * {@code EXPIRED} o {@code PAUSED} (deja de contar hasta que se reanude).
 */
public final class CampaignEndedEvent extends CampaignEvent {

    private static final HandlerList HANDLERS = new HandlerList();
    private final CampaignState outcome;
    private final String actor;

    public CampaignEndedEvent(CampaignInfo campaign, CampaignState outcome, String actor) {
        super(campaign);
        this.outcome = outcome;
        this.actor = actor;
    }

    public CampaignState outcome() {
        return outcome;
    }

    public String actor() {
        return actor;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
