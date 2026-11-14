package com.plgroup.goalspl.runtime;

import com.plgroup.goalspl.GoalsPL;
import com.plgroup.goalspl.domain.CampaignValidator;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.EntityType;

import java.util.Locale;

/** Lo que el validador pregunta al servidor: si existen entidades, bloques, objetos, mundos y campañas. */
public final class ServerEnv implements CampaignValidator.Env {

    private final GoalsPL plugin;

    public ServerEnv(GoalsPL plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean entity(String name) {
        NamespacedKey key = NamespacedKey.fromString(name.toLowerCase(Locale.ROOT));
        EntityType t = key == null ? null : Registry.ENTITY_TYPE.get(key);
        return t != null && t.isAlive();
    }

    @Override
    public boolean block(String name) {
        Material m = Material.matchMaterial(name);
        return m != null && m.isBlock() && !m.isAir();
    }

    @Override
    public boolean item(String name) {
        Material m = Material.matchMaterial(name);
        return m != null && m.isItem() && !m.isAir();
    }

    @Override
    public boolean world(String name) {
        return Bukkit.getWorld(name) != null;
    }

    @Override
    public boolean campaign(String id) {
        return plugin.manager().definition(id) != null;
    }

    @Override
    public boolean eventsPl() {
        return plugin.eventsPl().available();
    }

    @Override
    public boolean blockedCommand(String command) {
        return plugin.settings().blocked(command);
    }

    @Override
    public long now() {
        return System.currentTimeMillis();
    }
}
