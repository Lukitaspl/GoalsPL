package com.plgroup.goalspl.domain;

import java.util.Locale;

/**
 * Una recompensa: tipo y valor (el comando, el texto, {@code MATERIAL cantidad}, {@code permiso duración} o el ID
 * del evento). En YAML se escribe {@code "TIPO: valor"}.
 */
public record Reward(RewardType type, String value) {

    public Reward {
        value = value == null ? "" : value.strip();
    }

    /** Lee {@code "TIPO: valor"}; {@code null} si el tipo no existe o falta el separador. */
    public static Reward parse(String line) {
        if (line == null) {
            return null;
        }
        int i = line.indexOf(':');
        if (i <= 0) {
            return null;
        }
        try {
            RewardType t = RewardType.valueOf(line.substring(0, i).strip().toUpperCase(Locale.ROOT));
            return new Reward(t, line.substring(i + 1));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public String serialize() {
        return type.name() + ": " + value;
    }
}
