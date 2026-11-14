package com.plgroup.goalspl.api.event;

import com.plgroup.goalspl.api.CampaignInfo;
import org.bukkit.event.HandlerList;

/** El progreso total de una campaña ha llegado a un hito configurado ({@link #percent()} %, 100 = completada). */
public final class CampaignMilestoneEvent extends CampaignEvent {

    private static final HandlerList HANDLERS = new HandlerList();
    private final int percent;

    public CampaignMilestoneEvent(CampaignInfo campaign, int percent) {
        super(campaign);
        this.percent = percent;
    }

    public int percent() {
        return percent;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
