package com.plgroup.goalspl.runtime;

import com.plgroup.goalspl.GoalsPL;
import com.plgroup.goalspl.api.CampaignInfo;
import com.plgroup.goalspl.api.GoalsAPI;
import com.plgroup.goalspl.domain.Campaign;
import com.plgroup.goalspl.domain.HistoryAction;
import com.plgroup.goalspl.domain.Source;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Implementación de {@link GoalsAPI} sobre el gestor de campañas. */
public final class ApiImpl implements GoalsAPI {

    private final GoalsPL plugin;

    public ApiImpl(GoalsPL plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<CampaignInfo> campaigns() {
        return plugin.manager().definitions().values().stream().map(plugin.manager()::info).toList();
    }

    @Override
    public Optional<CampaignInfo> campaign(String id) {
        Campaign c = plugin.manager().definition(id);
        return c == null ? Optional.empty() : Optional.of(plugin.manager().info(c));
    }

    @Override
    public List<CampaignInfo> running() {
        return plugin.manager().running().stream().map(plugin.manager()::info).toList();
    }

    @Override
    public long contribution(String campaignId, UUID player) {
        return plugin.manager().definition(campaignId) == null ? 0 : plugin.manager().state(campaignId).contribution(player);
    }

    @Override
    public long addProgress(String campaignId, UUID player, String playerName, long amount, String source) {
        Campaign c = plugin.manager().definition(campaignId);
        if (c == null) {
            return 0;
        }
        long accepted = plugin.manager().contribute(c, player, playerName == null ? "?" : playerName, amount, Source.API);
        if (accepted > 0) {
            plugin.manager().history(campaignId, source == null ? "API" : source, HistoryAction.PROGRESS,
                    "+" + accepted + (playerName == null ? "" : " (" + playerName + ")"));
        }
        return accepted;
    }
}
