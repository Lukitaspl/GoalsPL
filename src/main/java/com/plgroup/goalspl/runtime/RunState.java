package com.plgroup.goalspl.runtime;

import com.plgroup.goalspl.domain.CampaignState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Estado en marcha de una campaña (se guarda en SQLite): estado, número de ejecución, progreso, fechas, las
 * aportaciones de cada jugador en esta ejecución y las claves de lo ya entregado ({@code grants}), que evitan
 * repetir hitos o recompensas tras un reinicio. Solo se toca desde el hilo principal.
 */
public final class RunState {

    /** Aportación de un jugador en la ejecución actual. */
    public static final class Contribution {
        public final UUID player;
        public String name;
        public long amount;
        public boolean dirty;

        public Contribution(UUID player, String name, long amount) {
            this.player = player;
            this.name = name;
            this.amount = amount;
        }
    }

    public final String campaignId;
    public CampaignState state = CampaignState.DRAFT;
    public int run;
    public long progress;
    public long startedAt;
    /** Fin de la ejecución (0 = sin fin). */
    public long endsAt;
    /** Tiempo que quedaba al pausar (-1 si no está pausada o no tiene fin). */
    public long pausedRemaining = -1;
    public long updatedAt;
    public final Map<UUID, Contribution> contributions = new HashMap<>();
    public final Set<String> grants = new HashSet<>();
    /** Hay cambios sin guardar (progreso o estado). */
    public boolean dirty;

    public RunState(String campaignId) {
        this.campaignId = campaignId;
    }

    public long contribution(UUID player) {
        Contribution c = contributions.get(player);
        return c == null ? 0 : c.amount;
    }

    /** Clasificación de mayor a menor aportación (empates por nombre). */
    public List<Contribution> ranking() {
        List<Contribution> list = new ArrayList<>(contributions.values());
        list.removeIf(c -> c.amount <= 0);
        list.sort(Comparator.comparingLong((Contribution c) -> -c.amount).thenComparing(c -> c.name));
        return list;
    }

    /** Puesto de un jugador (1 = primero, 0 = sin aportación). */
    public int rank(UUID player) {
        List<Contribution> r = ranking();
        for (int i = 0; i < r.size(); i++) {
            if (r.get(i).player.equals(player)) {
                return i + 1;
            }
        }
        return 0;
    }

    /** Copia de las columnas de campaign_state para guardarla desde el hilo de la base de datos. */
    public Row row() {
        return new Row(campaignId, state, run, progress, startedAt, endsAt, pausedRemaining, updatedAt);
    }

    /** Fila de campaign_state. */
    public record Row(String campaignId, CampaignState state, int run, long progress, long startedAt, long endsAt,
                      long pausedRemaining, long updatedAt) {
    }

    /** Fila de contributions. */
    public record ContributionRow(String campaignId, int run, UUID player, String name, long amount, long at) {
    }

    /** Aportaciones con cambios sin guardar; las marca como guardadas. */
    public List<ContributionRow> drainDirty(long now) {
        List<ContributionRow> out = new ArrayList<>();
        for (Contribution c : contributions.values()) {
            if (c.dirty) {
                c.dirty = false;
                out.add(new ContributionRow(campaignId, run, c.player, c.name, c.amount, now));
            }
        }
        return out;
    }
}
