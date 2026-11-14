package com.plgroup.goalspl.gui;

import com.plgroup.goalspl.GoalsPL;
import com.plgroup.goalspl.domain.HistoryAction;
import com.plgroup.goalspl.lang.Messages;
import com.plgroup.goalspl.storage.HistoryEntry;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/** Historial (las 450 entradas más recientes) de una campaña o de todas: quién hizo qué y cuándo. */
public final class HistoryMenu extends PagedMenu<HistoryEntry> {

    private final List<HistoryEntry> rows;
    private final Runnable onBack;

    private HistoryMenu(GoalsPL plugin, Player viewer, List<HistoryEntry> rows, Runnable onBack) {
        super(plugin, viewer, plugin.messages().text("gui.history.title"), 0);
        this.rows = rows;
        this.onBack = onBack;
    }

    public static void open(GoalsPL plugin, Player viewer, String campaignId, Runnable onBack) {
        plugin.database().flushHistory();
        plugin.database().query("leer historial", st -> st.history(campaignId, 450, 0), rows -> {
            if (viewer.isOnline()) {
                new HistoryMenu(plugin, viewer, rows == null ? List.of() : rows, onBack).open();
            }
        });
    }

    @Override
    protected List<HistoryEntry> entries() {
        return rows;
    }

    @Override
    protected ItemStack icon(HistoryEntry h) {
        String when = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(h.at()));
        return Gui.item(icon(h.action()), plugin.messages().item("history-action." + h.action().name()),
                plugin.messages().lore("gui.history.entry", Messages.ph("date", when), Messages.ph("campaign", h.campaignId()),
                        Messages.ph("actor", h.actor()), Messages.ph("details", h.details())));
    }

    private static Material icon(HistoryAction a) {
        return switch (a) {
            case CREATED, EDITED -> Material.WRITABLE_BOOK;
            case DELETED, CANCELLED, ERROR, REWARD_FAILED -> Material.RED_DYE;
            case STARTED, RESUMED, NEXT -> Material.LIME_DYE;
            case PAUSED -> Material.YELLOW_DYE;
            case COMPLETED, MILESTONE -> Material.EMERALD;
            case EXPIRED -> Material.GRAY_DYE;
            case PROGRESS -> Material.COMPARATOR;
            case DELIVERY, REFUND -> Material.HOPPER;
            case REWARD -> Material.CHEST;
        };
    }

    @Override
    protected void clicked(HistoryEntry entry, ClickType type) {
    }

    @Override
    protected void back() {
        onBack.run();
    }
}
