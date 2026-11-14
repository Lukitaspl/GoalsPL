package com.plgroup.goalspl.runtime;

import com.plgroup.goalspl.GoalsPL;
import com.plgroup.goalspl.domain.Campaign;
import com.plgroup.goalspl.domain.CampaignState;
import com.plgroup.goalspl.domain.ProgressMath;
import com.plgroup.goalspl.lang.Messages;
import com.plgroup.goalspl.util.Durations;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Bossbar de cada campaña activa con su progreso y el tiempo que queda. Se enseña a quien está en un mundo donde
 * cuenta la campaña y se quita al pausar, terminar, desactivarla o cambiar de mundo. Se actualiza cada segundo.
 */
public final class BossbarService {

    private final GoalsPL plugin;
    private final Map<String, BossBar> bars = new HashMap<>();
    private final Map<String, Set<UUID>> viewers = new HashMap<>();

    public BossbarService(GoalsPL plugin) {
        this.plugin = plugin;
    }

    public Set<String> ids() {
        return bars.keySet();
    }

    public void update(Campaign c, RunState s) {
        if (!plugin.settings().bossbars || !c.bossbar || s.state != CampaignState.ACTIVE) {
            remove(c.id);
            return;
        }
        long left = plugin.manager().secondsLeft(c);
        var title = plugin.messages().text(left < 0 ? "bossbar.title" : "bossbar.title-timed", Messages.parsed("name", c.name),
                Messages.ph("progress", s.progress), Messages.ph("goal", c.goal),
                Messages.ph("percent", ProgressMath.percent(s.progress, c.goal)), Messages.ph("time", Durations.clock(left)));
        BossBar bar = bars.get(c.id);
        BossBar.Color color = enumOr(BossBar.Color.class, c.bossbarColor, BossBar.Color.GREEN);
        BossBar.Overlay overlay = enumOr(BossBar.Overlay.class, c.bossbarOverlay, BossBar.Overlay.PROGRESS);
        float fraction = ProgressMath.fraction(s.progress, c.goal);
        if (bar == null) {
            bar = BossBar.bossBar(title, fraction, color, overlay);
            bars.put(c.id, bar);
        } else {
            bar.name(title);
            bar.progress(fraction);
            bar.color(color);
            bar.overlay(overlay);
        }
        Set<UUID> now = new HashSet<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (c.countsInWorld(p.getWorld().getName())) {
                now.add(p.getUniqueId());
                p.showBossBar(bar);
            }
        }
        Set<UUID> before = viewers.put(c.id, now);
        if (before != null) {
            for (UUID id : before) {
                if (!now.contains(id)) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null) {
                        p.hideBossBar(bar);
                    }
                }
            }
        }
    }

    public void remove(String id) {
        BossBar bar = bars.remove(id);
        Set<UUID> seen = viewers.remove(id);
        if (bar == null || seen == null) {
            return;
        }
        for (UUID u : seen) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                p.hideBossBar(bar);
            }
        }
    }

    public void forget(UUID player) {
        viewers.values().forEach(set -> set.remove(player));
    }

    public void removeAll() {
        for (String id : Set.copyOf(bars.keySet())) {
            remove(id);
        }
    }

    private static <E extends Enum<E>> E enumOr(Class<E> type, String name, E def) {
        try {
            return Enum.valueOf(type, name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return def;
        }
    }
}
