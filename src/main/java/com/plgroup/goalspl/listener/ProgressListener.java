package com.plgroup.goalspl.listener;

import com.plgroup.goalspl.GoalsPL;
import com.plgroup.goalspl.domain.Campaign;
import com.plgroup.goalspl.domain.ObjectiveType;
import com.plgroup.goalspl.domain.Source;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cuenta el progreso de las campañas de criaturas y bloques. Protecciones contra progreso falso o duplicado:
 * <ul>
 *   <li>Criaturas: solo si las mata un jugador; cada criatura cuenta una vez (se recuerdan las últimas 2048); por
 *   defecto no cuentan las de generadores; con «cualquiera» nunca cuentan jugadores ni soportes de armadura.</li>
 *   <li>Bloques: solo roturas no canceladas por otro plugin (prioridad MONITOR); no cuentan los colocados por
 *   jugadores (marcados en el chunk) ni los cultivos sin crecer.</li>
 *   <li>Por defecto no cuenta el modo creativo ni el espectador.</li>
 * </ul>
 */
public final class ProgressListener implements Listener {

    private final GoalsPL plugin;
    private final PlacedBlocks placed;
    private final Map<UUID, Boolean> recentDeaths = new LinkedHashMap<>(256, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Boolean> eldest) {
            return size() > 2048;
        }
    };

    public ProgressListener(GoalsPL plugin) {
        this.plugin = plugin;
        this.placed = new PlacedBlocks(plugin);
    }

    private boolean playerCounts(Player p) {
        GameMode g = p.getGameMode();
        return g != GameMode.SPECTATOR && (plugin.settings().countCreative || g != GameMode.CREATIVE);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();
        Player killer = dead.getKiller();
        if (killer == null || killer.equals(dead) || !playerCounts(killer)) {
            return;
        }
        List<Campaign> list = plugin.manager().active(ObjectiveType.KILL_ENTITY);
        if (list.isEmpty() || recentDeaths.put(dead.getUniqueId(), Boolean.TRUE) != null) {
            return;
        }
        if (!plugin.settings().countSpawnerMobs && dead.fromMobSpawner()) {
            return;
        }
        EntityType type = dead.getType();
        String world = dead.getWorld().getName();
        for (Campaign c : list) {
            boolean any = c.targets.isEmpty();
            if (any && (type == EntityType.PLAYER || type == EntityType.ARMOR_STAND)) {
                continue;
            }
            if (c.counts(type.name()) && c.countsInWorld(world)) {
                plugin.manager().contribute(c, killer.getUniqueId(), killer.getName(), 1, Source.KILL);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        boolean wasPlaced = plugin.settings().ignorePlacedBlocks && placed.take(b);
        Player p = e.getPlayer();
        List<Campaign> list = plugin.manager().active(ObjectiveType.BREAK_BLOCK);
        if (list.isEmpty() || wasPlaced || !playerCounts(p)) {
            return;
        }
        if (plugin.settings().onlyGrownCrops && b.getBlockData() instanceof Ageable age && age.getAge() < age.getMaximumAge()) {
            return;
        }
        String type = b.getType().name();
        String world = b.getWorld().getName();
        for (Campaign c : list) {
            if (c.counts(type) && c.countsInWorld(world)) {
                plugin.manager().contribute(c, p.getUniqueId(), p.getName(), 1, Source.BREAK);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!plugin.settings().ignorePlacedBlocks) {
            return;
        }
        Block b = e.getBlockPlaced();
        // Los cultivos (Ageable) no se marcan: se protegen comprobando que estén crecidos.
        if (b.getBlockData() instanceof Ageable) {
            return;
        }
        String type = b.getType().name();
        for (Campaign c : plugin.manager().active(ObjectiveType.BREAK_BLOCK)) {
            if (c.counts(type) && c.countsInWorld(b.getWorld().getName())) {
                placed.mark(b);
                return;
            }
        }
    }
}
