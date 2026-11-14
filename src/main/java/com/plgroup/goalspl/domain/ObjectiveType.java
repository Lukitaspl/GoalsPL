package com.plgroup.goalspl.domain;

/**
 * Qué hace avanzar una campaña. Los objetivos se nombran en {@code targets}: tipos de entidad para
 * {@link #KILL_ENTITY}, bloques para {@link #BREAK_BLOCK} y objetos para {@link #DELIVER_ITEM}. {@link #ADMIN} solo
 * avanza con {@code /goals progress} o la API.
 */
public enum ObjectiveType {
    KILL_ENTITY,
    BREAK_BLOCK,
    DELIVER_ITEM,
    ADMIN;

    /** Si la lista de objetivos tiene que tener al menos uno. */
    public boolean needsTargets() {
        return this == DELIVER_ITEM;
    }

    /** Si usa la lista de objetivos. */
    public boolean usesTargets() {
        return this != ADMIN;
    }
}
