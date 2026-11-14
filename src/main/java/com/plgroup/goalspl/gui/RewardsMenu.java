package com.plgroup.goalspl.gui;

import com.plgroup.goalspl.GoalsPL;
import com.plgroup.goalspl.lang.Messages;
import com.plgroup.goalspl.storage.RewardRow;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Mis recompensas pendientes (no entregadas aún) con el botón para reclamarlas todas. */
public final class RewardsMenu extends PagedMenu<RewardRow> {

    private final List<RewardRow> rows;

    private RewardsMenu(GoalsPL plugin, Player viewer, List<RewardRow> rows) {
        super(plugin, viewer, plugin.messages().text("gui.rewards.title"), 0);
        this.rows = rows;
    }

    /** Lee las pendientes en segundo plano y abre el menú. */
    public static void open(GoalsPL plugin, Player viewer) {
        plugin.rewards().pending(viewer.getUniqueId(), rows -> {
            if (viewer.isOnline()) {
                new RewardsMenu(plugin, viewer, rows == null ? List.of() : rows).open();
            }
        });
    }

    @Override
    protected List<RewardRow> entries() {
        return rows;
    }

    @Override
    protected ItemStack icon(RewardRow r) {
        List<String> lines = new ArrayList<>();
        for (String line : r.payload().split("\n")) {
            lines.add("<gray>• " + Messages.escape(line));
        }
        var lore = new ArrayList<>(plugin.messages().lore("gui.rewards.entry", Messages.ph("campaign", r.campaignId())));
        lore.addAll(Gui.lines(lines));
        return Gui.item(Material.CHEST_MINECART, plugin.messages().item("gui.rewards.entry-name", Messages.ph("campaign", r.campaignId())), lore);
    }

    @Override
    protected void clicked(RewardRow entry, ClickType type) {
        claim();
    }

    private void claim() {
        viewer.closeInventory();
        plugin.rewards().claim(viewer, false, () -> open(plugin, viewer));
    }

    @Override
    protected void drawControls() {
        if (!rows.isEmpty()) {
            set(53, Gui.button(plugin, Material.EMERALD, "gui.rewards.claim", Messages.ph("count", rows.size())), this::claim);
        }
    }

    @Override
    protected void back() {
        new MainMenu(plugin, viewer).open();
    }
}
