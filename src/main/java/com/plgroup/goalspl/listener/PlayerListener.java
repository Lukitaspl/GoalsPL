package com.plgroup.goalspl.listener;

import com.plgroup.goalspl.GoalsPL;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Al entrar: permisos temporales vigentes y, si está activado, entrega de lo pendiente. Al salir: limpieza. */
public final class PlayerListener implements Listener {

    private final GoalsPL plugin;

    public PlayerListener(GoalsPL plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        var p = e.getPlayer();
        plugin.permissions().onJoin(p);
        if (plugin.settings().deliverOnJoin) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) {
                    plugin.rewards().claim(p, true, null);
                }
            }, 60L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.permissions().onQuit(e.getPlayer());
        plugin.bossbars().forget(e.getPlayer().getUniqueId());
    }
}
