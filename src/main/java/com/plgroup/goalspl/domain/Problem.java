package com.plgroup.goalspl.domain;

/**
 * Resultado de una validación: error (impide guardar) o aviso (se puede guardar). {@code key} es la clave
 * {@code validation.<key>} de messages.yml y {@code detail} el dato concreto (el valor, la recompensa…).
 */
public record Problem(boolean error, String key, String detail) {

    public static Problem error(String key, String detail) {
        return new Problem(true, key, detail);
    }

    public static Problem warning(String key, String detail) {
        return new Problem(false, key, detail);
    }
}
