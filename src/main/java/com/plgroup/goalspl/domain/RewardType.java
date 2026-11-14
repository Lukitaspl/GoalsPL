package com.plgroup.goalspl.domain;

/**
 * Tipos de recompensa. Las de un jugador ({@link #perPlayer()}) se entregan a cada jugador elegible y quedan
 * pendientes si no se pueden entregar; las globales se ejecutan una sola vez al alcanzar el hito.
 * <ul>
 *   <li>{@code COMMAND}: comando como consola por cada jugador ({@code eco give {player} 100}).</li>
 *   <li>{@code MESSAGE}: mensaje MiniMessage al jugador.</li>
 *   <li>{@code ITEM}: objetos simples, {@code MATERIAL cantidad}; lo que no cabe queda pendiente.</li>
 *   <li>{@code PERMISSION}: permiso temporal, {@code permiso duración} ({@code essentials.fly 1h}).</li>
 *   <li>{@code BROADCAST}: anuncio a todo el servidor (una vez).</li>
 *   <li>{@code GLOBAL_COMMAND}: comando como consola una vez, sin jugador.</li>
 *   <li>{@code EVENT}: inicia un evento de EventsPL por su ID (si está instalado).</li>
 * </ul>
 */
public enum RewardType {
    COMMAND(true),
    MESSAGE(true),
    ITEM(true),
    PERMISSION(true),
    BROADCAST(false),
    GLOBAL_COMMAND(false),
    EVENT(false);

    private final boolean perPlayer;

    RewardType(boolean perPlayer) {
        this.perPlayer = perPlayer;
    }

    public boolean perPlayer() {
        return perPlayer;
    }

    /** Si ejecuta comandos de consola (necesita el permiso {@code goalspl.commands} para editarla). */
    public boolean console() {
        return this == COMMAND || this == GLOBAL_COMMAND;
    }
}
