package com.plgroup.goalspl.domain;

/**
 * Qué pasa con los objetos entregados si la campaña se cancela o vence sin completarse. {@code KEEP}: se quedan
 * en la campaña. {@code REFUND}: se devuelven a quien los entregó (como recompensa pendiente si no cabe o no está
 * conectado).
 */
public enum EndPolicy {
    KEEP,
    REFUND
}
