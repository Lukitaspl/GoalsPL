package com.plgroup.goalspl.api;

import com.plgroup.goalspl.domain.CampaignState;
import com.plgroup.goalspl.domain.ObjectiveType;

/**
 * Foto de solo lectura de una campaña en un momento dado: definición básica, estado, ejecución, progreso, fechas
 * (milisegundos; {@code endsAt} 0 = sin fin) y número de participantes de la ejecución actual.
 */
public record CampaignInfo(String id, String name, ObjectiveType type, CampaignState state, int run, long progress,
                           long goal, int percent, long startedAt, long endsAt, int participants) {
}
