package com.plgroup.goalspl.gui;

import com.plgroup.goalspl.GoalsPL;
import com.plgroup.goalspl.lang.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Mundos donde cuenta el progreso: clic para marcar o desmarcar. Sin ninguno marcado cuenta en todos. Los mundos
 * guardados que ahora no están cargados también salen, para poder quitarlos.
 */
public final class WorldsMenu extends PagedMenu<String> {

    private final Draft draft;

    public WorldsMenu(GoalsPL plugin, Player viewer, Draft draft) {
        super(plugin, viewer, plugin.messages().text("gui.worlds.title"), 0);
        this.draft = draft;
    }

    @Override
    public Draft draft() {
        return draft;
    }

    @Override
    protected List<String> entries() {
        List<String> out = new ArrayList<>();
        for (World w : Bukkit.getWorlds()) {
            out.add(w.getName());
        }
        for (String w : draft.campaign.worlds) {
            if (!out.contains(w)) {
                out.add(w);
            }
        }
        return out;
    }

    @Override
    protected ItemStack icon(String w) {
        boolean on = draft.campaign.worlds.contains(w);
        World world = Bukkit.getWorld(w);
        Material m = world == null ? Material.BARRIER : switch (world.getEnvironment()) {
            case NETHER -> Material.NETHERRACK;
            case THE_END -> Material.END_STONE;
            default -> Material.GRASS_BLOCK;
        };
        return Gui.item(m, Messages.mini((on ? "<green>✔ " : "<gray>") + Messages.escape(w)),
                plugin.messages().lore(on ? "gui.worlds.selected" : "gui.worlds.unselected"));
    }

    @Override
    protected void clicked(String w, ClickType type) {
        if (!draft.campaign.worlds.remove(w)) {
            draft.campaign.worlds.add(w);
        }
        refresh();
    }

    @Override
    protected void drawControls() {
        set(52, Gui.button(plugin, Material.MAP, "gui.worlds.all"), () -> {
            draft.campaign.worlds.clear();
            refresh();
        });
    }

    @Override
    protected void back() {
        new EditorMenu(plugin, viewer, draft).open();
    }
}
