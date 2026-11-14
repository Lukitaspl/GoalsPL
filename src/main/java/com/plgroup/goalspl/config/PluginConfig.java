package com.plgroup.goalspl.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Ajustes globales de config.yml, ya leídos y comprobados. Cada cambio de formato sube {@code config-version} y
 * trae su migración en {@link #migrate}; las opciones nuevas se añaden solas con sus valores por defecto.
 */
public final class PluginConfig {

    public static final int VERSION = 1;

    public String language = "es";
    public ZoneId zone = ZoneId.systemDefault();
    public String databaseFile = "data/goalspl.db";
    public int saveIntervalSeconds = 2;
    public int historyRetentionDays = 90;
    public boolean countSpawnerMobs;
    public boolean countCreative;
    public boolean ignorePlacedBlocks = true;
    public boolean onlyGrownCrops = true;
    public boolean deliverOnJoin = true;
    public int retryMinutes = 5;
    public int topSize = 10;
    public boolean bossbars = true;
    public List<String> blockedCommands = new ArrayList<>();

    /** Aplica las migraciones pendientes; devuelve {@code true} si hay que guardar config.yml. */
    public static boolean migrate(FileConfiguration c, Logger log) {
        int v = c.getInt("config-version", 0);
        if (v >= VERSION) {
            return false;
        }
        // Versión 1: la primera; solo se fija el número.
        c.set("config-version", VERSION);
        log.info("config.yml actualizado a la versión " + VERSION + ".");
        return true;
    }

    public static PluginConfig load(FileConfiguration c, Logger log) {
        PluginConfig p = new PluginConfig();
        p.language = c.getString("language", "es").toLowerCase(Locale.ROOT);
        String zone = c.getString("timezone", "");
        if (!zone.isBlank()) {
            try {
                p.zone = ZoneId.of(zone.strip());
            } catch (DateTimeException e) {
                log.warning("timezone '" + zone + "' no existe; se usa la del sistema (" + p.zone + ").");
            }
        }
        p.databaseFile = c.getString("storage.file", p.databaseFile);
        p.saveIntervalSeconds = clamp(c.getInt("storage.save-interval-seconds", 2), 1, 60, "storage.save-interval-seconds", log);
        p.historyRetentionDays = clamp(c.getInt("history.retention-days", 90), 1, 3650, "history.retention-days", log);
        p.countSpawnerMobs = c.getBoolean("counting.spawner-mobs", false);
        p.countCreative = c.getBoolean("counting.creative", false);
        p.ignorePlacedBlocks = c.getBoolean("counting.ignore-placed-blocks", true);
        p.onlyGrownCrops = c.getBoolean("counting.only-grown-crops", true);
        p.deliverOnJoin = c.getBoolean("rewards.deliver-on-join", true);
        p.retryMinutes = clamp(c.getInt("rewards.retry-minutes", 5), 0, 1440, "rewards.retry-minutes", log);
        p.topSize = clamp(c.getInt("leaderboard.size", 10), 1, 45, "leaderboard.size", log);
        p.bossbars = c.getBoolean("bossbar.enabled", true);
        for (String s : c.getStringList("console-commands.blocked")) {
            p.blockedCommands.add(s.strip().toLowerCase(Locale.ROOT));
        }
        return p;
    }

    private static int clamp(int value, int min, int max, String key, Logger log) {
        if (value < min || value > max) {
            int fixed = Math.max(min, Math.min(max, value));
            log.warning(key + " = " + value + " fuera de rango (" + min + "-" + max + "); se usa " + fixed + ".");
            return fixed;
        }
        return value;
    }

    /** Si un comando (sin barra) empieza por uno bloqueado, con o sin espacio de nombres ({@code minecraft:op}). */
    public boolean blocked(String command) {
        return blocked(command, blockedCommands);
    }

    public static boolean blocked(String command, List<String> blockedList) {
        String first = command.strip().toLowerCase(Locale.ROOT).split("\\s+", 2)[0];
        if (first.startsWith("/")) {
            first = first.substring(1);
        }
        int colon = first.indexOf(':');
        String bare = colon >= 0 ? first.substring(colon + 1) : first;
        return blockedList.contains(first) || blockedList.contains(bare);
    }
}
