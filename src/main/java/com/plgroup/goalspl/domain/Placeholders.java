package com.plgroup.goalspl.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Variables de los textos de recompensas y mensajes: {@code {player}}, {@code {goal_id}}, {@code {goal_name}},
 * {@code {progress}}, {@code {goal}}, {@code {percent}}, {@code {contribution}} y {@code {rank}}.
 * <p>
 * Seguridad: en los comandos de consola cada valor se limpia con {@link #forCommand(String)} (solo letras, números,
 * {@code _ . -} y espacios), así un nombre o un texto nunca añade otro comando, saltos de línea ni selectores.
 */
public final class Placeholders {

    public static final List<String> NAMES = List.of("player", "goal_id", "goal_name", "progress", "goal", "percent",
            "contribution", "rank");

    private static final Pattern UNSAFE = Pattern.compile("[^A-Za-z0-9_.\\- ]");
    private static final Pattern UNKNOWN = Pattern.compile("\\{([a-z_]+)}");

    private final Map<String, String> values = new LinkedHashMap<>();

    public Placeholders set(String name, Object value) {
        values.put(name, value == null ? "" : String.valueOf(value));
        return this;
    }

    public Map<String, String> values() {
        return values;
    }

    /** Sustituye las variables tal cual (para mensajes; MiniMessage escapa aparte lo que haga falta). */
    public String apply(String text) {
        String out = text;
        for (Map.Entry<String, String> e : values.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue());
        }
        return out;
    }

    /** Sustituye las variables con valores limpios, para comandos de consola. */
    public String applyForCommand(String text) {
        String out = text;
        for (Map.Entry<String, String> e : values.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", forCommand(e.getValue()));
        }
        return out.replace('\n', ' ').replace('\r', ' ');
    }

    public static String forCommand(String value) {
        return UNSAFE.matcher(value == null ? "" : value).replaceAll("").strip();
    }

    /** Variables escritas en un texto que no existen ({@code {jugador}}…), para avisar al validar. */
    public static List<String> unknown(String text) {
        return UNKNOWN.matcher(text == null ? "" : text).results().map(m -> m.group(1))
                .filter(n -> !NAMES.contains(n)).distinct().toList();
    }
}
