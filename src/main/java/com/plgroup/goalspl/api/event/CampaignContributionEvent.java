package com.plgroup.goalspl.api.event;

import com.plgroup.goalspl.api.CampaignInfo;
import com.plgroup.goalspl.domain.Source;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * Un jugador va a aportar a una campaña (antes de sumarlo). Si se cancela, no cuenta (en una entrega de objetos,
 * no se le quitan). {@link #amount()} ya está recortado a lo que falta y a su máximo. {@code player} es nulo
 * cuando el progreso no es de nadie (administración o API sin jugador).
 */
public final class CampaignContributionEvent extends CampaignEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID player;
    private final String playerName;
    private final long amount;
    private final Source source;
    private boolean cancelled;

    public CampaignContributionEvent(CampaignInfo campaign, UUID player, String playerName, long amount, Source source) {
        super(campaign);
        this.player = player;
        this.playerName = playerName;
        this.amount = amount;
        this.source = source;
    }

    public UUID player() {
        return player;
    }

    public String playerName() {
        return playerName;
    }

    public long amount() {
        return amount;
    }

    public Source source() {
        return source;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
