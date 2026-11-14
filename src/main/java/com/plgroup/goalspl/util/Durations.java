package com.plgroup.goalspl.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Duraciones escritas por la administración: {@code 90} (segundos), {@code 30s}, {@code 15m}, {@code 2h},
 * {@code 1d}, o combinadas ({@code 1h30m}, {@code 1d 12h}). Y su formato corto para mostrarlas
 * ({@code 1h 30m 5s}).
 */
public final class Durations {

    private static final Pattern PART = Pattern.compile("(\\d+)\\s*([smhd])");

    private Durations() {
    }

    /** Segundos, o -1 si el texto no es una duración válida. */
    public static long parse(String text) {
        if (text == null) {
            return -1;
        }
        String t = text.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        if (t.isEmpty()) {
            return -1;
        }
        if (t.chars().allMatch(Character::isDigit)) {
            try {
                return Long.parseLong(t);
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        Matcher m = PART.matcher(t);
        long total = 0;
        int end = 0;
        while (m.find()) {
            if (m.start() != end) {
                return -1;
            }
            end = m.end();
            long n;
            try {
                n = Long.parseLong(m.group(1));
            } catch (NumberFormatException e) {
                return -1;
            }
            total += switch (m.group(2)) {
                case "s" -> n;
                case "m" -> n * 60;
                case "h" -> n * 3600;
                default -> n * 86400;
            };
        }
        return end == t.length() ? total : -1;
    }

    /** Formato corto: {@code 2d 3h}, {@code 1h 30m}, {@code 45s}. Muestra como mucho las dos unidades mayores. */
    public static String format(long seconds) {
        if (seconds < 0) {
            seconds = 0;
        }
        long d = seconds / 86400;
        long h = seconds % 86400 / 3600;
        long m = seconds % 3600 / 60;
        long s = seconds % 60;
        StringBuilder sb = new StringBuilder();
        int parts = 0;
        long[] values = {d, h, m, s};
        String[] units = {"d", "h", "m", "s"};
        for (int i = 0; i < values.length && parts < 2; i++) {
            if (values[i] > 0 || (parts == 0 && i == values.length - 1)) {
                if (!sb.isEmpty()) {
                    sb.append(' ');
                }
                sb.append(values[i]).append(units[i]);
                parts++;
            } else if (parts > 0) {
                parts++;
            }
        }
        return sb.toString();
    }

    /** Formato de reloj para la bossbar: {@code 1:05:09} o {@code 05:09}. */
    public static String clock(long seconds) {
        if (seconds < 0) {
            seconds = 0;
        }
        long h = seconds / 3600;
        long m = seconds % 3600 / 60;
        long s = seconds % 60;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) : String.format(Locale.ROOT, "%02d:%02d", m, s);
    }
}
