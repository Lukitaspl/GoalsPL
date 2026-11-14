package com.plgroup.goalspl.storage;

import java.util.UUID;

/** Entrega de objetos a una campaña. Estados: {@code DELIVERED} y {@code REFUNDED} (devuelta al cancelar o vencer). */
public record DeliveryRow(long at, String campaignId, int run, UUID player, String name, String material, long amount,
                          String status) {
}
