package com.plgroup.goalspl.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * API pública de GoalsPL. Se obtiene con el ServicesManager de Bukkit:
 * <pre>{@code
 * GoalsAPI api = Bukkit.getServicesManager().load(GoalsAPI.class);
 * }</pre>
 * Para reaccionar a los cambios, escuchar los eventos de Bukkit de {@code com.plgroup.goalspl.api.event}:
 * {@code CampaignStartedEvent}, {@code CampaignMilestoneEvent}, {@code CampaignEndedEvent} (completada, cancelada,
 * vencida o pausada) y {@code CampaignContributionEvent} (cancelable). Llamar solo desde el hilo principal.
 */
public interface GoalsAPI {

    /** Todas las campañas definidas. */
    List<CampaignInfo> campaigns();

    Optional<CampaignInfo> campaign(String id);

    /** Campañas activas o pausadas. */
    List<CampaignInfo> running();

    /** Aportación de un jugador en la ejecución actual de una campaña (0 si no ha aportado). */
    long contribution(String campaignId, UUID player);

    /**
     * Suma progreso a una campaña activa en nombre de un jugador (cuenta para su aportación, con los mismos límites
     * que el resto) o de nadie ({@code player} nulo). Devuelve la cantidad aceptada (0 si la campaña no está activa,
     * ya está completa o el jugador llegó a su máximo). {@code source} es el nombre del plugin, para el historial.
     */
    long addProgress(String campaignId, UUID player, String playerName, long amount, String source);
}
