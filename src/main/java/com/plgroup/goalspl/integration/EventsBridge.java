package com.plgroup.goalspl.integration;

import com.plgroup.goalspl.domain.Placeholders;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Integración opcional con EventsPL: las recompensas {@code EVENT} inician uno de sus eventos por su ID con
 * su propio comando ({@code /eventspl start <id>}), así GoalsPL no necesita sus clases y funciona
 * igual sin él (la recompensa queda registrada como fallida y se avisa en el registro).
 */
public final class EventsBridge {

    public boolean available() {
        Plugin p = Bukkit.getPluginManager().getPlugin("EventsPL");
        return p != null && p.isEnabled();
    }

    /** Inicia el evento; {@code false} si EventsPL no está o el comando falla. */
    public boolean start(String eventId) {
        String id = Placeholders.forCommand(eventId).replace(" ", "");
        if (!available() || id.isEmpty()) {
            return false;
        }
        return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "eventspl start " + id);
    }
}
