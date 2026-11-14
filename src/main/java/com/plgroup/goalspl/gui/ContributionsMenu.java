package com.plgroup.goalspl.gui;

import com.plgroup.goalspl.GoalsPL;
import com.plgroup.goalspl.lang.Messages;
import com.plgroup.goalspl.runtime.RunState;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;

/**
 * Aportaciones de la ejecución actual, de mayor a menor. Con el permiso de ajustar progreso, clic en un jugador
 * para sumar o restar a su aportación (y al total), por ejemplo para corregir un error.
 */
public final class ContributionsMenu extends PagedMenu<RunState.Contribution> {

    private final String id;

    public ContributionsMenu(GoalsPL plugin, Player viewer, String id, int page) {
        super(plugin, viewer, plugin.messages().text("gui.contributions.title", Messages.ph("id", id)), page);
        this.id = id;
    }

    @Override
    protected List<RunState.Contribution> entries() {
        return plugin.manager().state(id).ranking();
    }

    @Override
    protected ItemStack icon(RunState.Contribution c) {
        RunState s = plugin.manager().state(id);
        ItemStack head = Gui.item(Material.PLAYER_HEAD, Messages.mini("<yellow>" + Messages.escape(c.name)),
                plugin.messages().lore("gui.contributions.entry", Messages.ph("amount", c.amount),
                        Messages.ph("rank", s.rank(c.player))));
        if (head.getItemMeta() instanceof SkullMeta meta) {
            meta.setPlayerProfile(Bukkit.createProfile(c.player, c.name));
            head.setItemMeta(meta);
        }
        return head;
    }

    @Override
    protected void clicked(RunState.Contribution c, ClickType type) {
        if (!allowed("goalspl.progress")) {
            return;
        }
        ChatInput.ask(plugin, viewer, "gui.input.adjust-player", text -> {
            try {
                long v = Long.parseLong(text.strip().replace("+", ""));
                var r = plugin.manager().adjust(id, v, false, c.player, c.name, viewer.getName());
                plugin.messages().send(viewer, r == com.plgroup.goalspl.runtime.CampaignManager.Result.OK ? "result.done"
                        : "result." + r.name(), Messages.ph("id", id));
            } catch (NumberFormatException e) {
                plugin.messages().send(viewer, "gui.input.bad-number");
            }
            open();
        }, this::open, Messages.ph("player", c.name));
    }

    @Override
    protected void back() {
        new CampaignAdminMenu(plugin, viewer, id).open();
    }
}
