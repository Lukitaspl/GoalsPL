package com.plgroup.goalspl.gui;

import com.plgroup.goalspl.GoalsPL;
import com.plgroup.goalspl.lang.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Menú principal de {@code /goals}: metas activas, mis recompensas y, con permiso, el panel de administración. */
public final class MainMenu extends Menu {

    public MainMenu(GoalsPL plugin, Player viewer) {
        super(plugin, viewer, 3, plugin.messages().text("gui.main.title"));
    }

    @Override
    protected void draw() {
        Gui.fillAll(this, 3);
        int running = plugin.manager().running().size();
        set(11, Gui.button(plugin, Material.NETHER_STAR, "gui.main.goals", Messages.ph("count", running)),
                () -> new GoalsMenu(plugin, viewer, 0).open());
        set(13, Gui.button(plugin, Material.CHEST, "gui.main.rewards"), () -> RewardsMenu.open(plugin, viewer));
        if (viewer.hasPermission("goalspl.view")) {
            set(15, Gui.button(plugin, Material.COMMAND_BLOCK, "gui.main.admin"), () -> new AdminMenu(plugin, viewer, 0).open());
        }
        set(22, Gui.button(plugin, Material.BARRIER, "gui.common.close"), () -> viewer.closeInventory());
    }
}
