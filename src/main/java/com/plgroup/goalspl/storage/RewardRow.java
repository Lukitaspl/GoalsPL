package com.plgroup.goalspl.storage;

import java.util.UUID;

/**
 * Una recompensa de un jugador guardada en la base de datos. {@code payload} son las recompensas ya con las
 * variables sustituidas, una por línea ({@code TIPO: valor}); si una parte falla, en el payload queda solo lo que
 * falta por entregar. Estados: {@code PENDING} (por entregar o reintentar) y {@code DELIVERED}.
 */
public record RewardRow(long id, String grantKey, String campaignId, int run, UUID player, String name, String payload,
                        String status, int attempts, String lastError, long createdAt, long deliveredAt) {

    public static final String PENDING = "PENDING";
    public static final String DELIVERED = "DELIVERED";

    public RewardRow withResult(String newStatus, String newPayload, String error, long at) {
        return new RewardRow(id, grantKey, campaignId, run, player, name, newPayload, newStatus, attempts + 1,
                error == null ? "" : error, createdAt, DELIVERED.equals(newStatus) ? at : deliveredAt);
    }
}
