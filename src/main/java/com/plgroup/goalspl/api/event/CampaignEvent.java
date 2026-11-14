package com.plgroup.goalspl.api.event;

import com.plgroup.goalspl.api.CampaignInfo;
import org.bukkit.event.Event;

/** Base de los eventos de GoalsPL: todos llevan la foto de la campaña. Se lanzan en el hilo principal. */
public abstract class CampaignEvent extends Event {

    private final CampaignInfo campaign;

    protected CampaignEvent(CampaignInfo campaign) {
        this.campaign = campaign;
    }

    public CampaignInfo campaign() {
        return campaign;
    }
}
