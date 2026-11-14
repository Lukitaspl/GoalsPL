package com.plgroup.goalspl.gui;

import com.plgroup.goalspl.GoalsPL;
import com.plgroup.goalspl.lang.Messages;
import com.plgroup.goalspl.storage.RewardRow;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Recompensas de una campaña (las 450 más recientes): a quién, qué, si se entregó o sigue pendiente y por qué. */
public final class RewardLogMenu extends PagedMenu<RewardRow> {

    private final String id;
    private final List<RewardRow> rows;

    private RewardLogMenu(GoalsPL plugin, Player viewer, String id, List<RewardRow> rows) {
        super(plugin, viewer, plugin.messages().text("gui.reward-log.title", Messages.ph("id", id)), 0);
        this.id = id;
        this.rows = rows;
    }

    public static void open(GoalsPL plugin, Player viewer, String id) {
        plugin.rewards().forCampaign(id, 450, 0, rows -> {
            if (viewer.isOnline()) {
                new RewardLogMenu(plugin, viewer, id, rows == null ? List.of() : rows).open();
            }
        });
    }

    @Override
    protected List<RewardRow> entries() {
        return rows;
    }

    @Override
    protected ItemStack icon(RewardRow r) {
        boolean done = RewardRow.DELIVERED.equals(r.status());
        String when = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(r.createdAt()));
        var lore = new ArrayList<>(plugin.messages().lore("gui.reward-log.entry", Messages.ph("key", r.grantKey()),
                Messages.ph("date", when), Messages.ph("attempts", r.attempts()),
                Messages.ph("status", plugin.messages().raw(done ? "gui.reward-log.delivered" : "gui.reward-log.pending")),
                Messages.ph("error", r.lastError().isEmpty() ? "-" : r.lastError())));
        List<String> lines = new ArrayList<>();
        for (String line : r.payload().split("\n")) {
            lines.add("<gray>• " + Messages.escape(line));
        }
        lore.addAll(Gui.lines(lines));
        return Gui.item(done ? Material.LIME_SHULKER_BOX : Material.ORANGE_SHULKER_BOX,
                Messages.mini("<yellow>" + Messages.escape(r.name())), lore);
    }

    @Override
    protected void clicked(RewardRow entry, ClickType type) {
    }

    @Override
    protected void back() {
        new CampaignAdminMenu(plugin, viewer, id).open();
    }
}
