package com.plgroup.goalspl.gui;

import com.plgroup.goalspl.GoalsPL;
import com.plgroup.goalspl.domain.Campaign;
import com.plgroup.goalspl.lang.Messages;
import com.plgroup.goalspl.runtime.RunState;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

/** Campañas activas y pausadas, con su progreso y mi aportación. Se redibuja cada segundo mientras está abierto. */
public final class GoalsMenu extends PagedMenu<Campaign> {

    private BukkitTask task;

    public GoalsMenu(GoalsPL plugin, Player viewer, int page) {
        super(plugin, viewer, plugin.messages().text("gui.goals.title"), page);
    }

    @Override
    protected List<Campaign> entries() {
        return plugin.manager().running();
    }

    @Override
    protected ItemStack icon(Campaign c) {
        RunState s = plugin.manager().state(c.id);
        return Gui.item(Gui.material(c.icon), Messages.mini(c.name), withMine(c, s));
    }

    private List<net.kyori.adventure.text.Component> withMine(Campaign c, RunState s) {
        var lore = new java.util.ArrayList<>(plugin.messages().lore("gui.goals.entry", Gui.campaign(plugin, c)));
        lore.addAll(plugin.messages().lore("gui.goals.mine", Messages.ph("amount", s.contribution(viewer.getUniqueId())),
                Messages.ph("rank", s.rank(viewer.getUniqueId()))));
        return lore;
    }

    @Override
    protected void clicked(Campaign c, ClickType type) {
        new CampaignMenu(plugin, viewer, c.id).open();
    }

    @Override
    protected void back() {
        new MainMenu(plugin, viewer).open();
    }

    @Override
    public void open() {
        super.open();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!viewer.isOnline() || viewer.getOpenInventory().getTopInventory().getHolder(false) != this) {
                task.cancel();
                return;
            }
            refresh();
        }, 20L, 20L);
    }
}
