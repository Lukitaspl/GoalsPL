package com.plgroup.goalspl.gui;

import com.plgroup.goalspl.GoalsPL;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/** Confirmación antes de una acción destructiva: botón verde para confirmar y rojo para volver. */
public final class ConfirmMenu extends Menu {

    private final List<Component> description;
    private final Runnable onYes;
    private final Runnable onNo;
    private final Draft draft;

    public ConfirmMenu(GoalsPL plugin, Player viewer, Component title, List<Component> description, Draft draft,
                       Runnable onYes, Runnable onNo) {
        super(plugin, viewer, 3, title);
        this.description = description;
        this.onYes = onYes;
        this.onNo = onNo;
        this.draft = draft;
    }

    @Override
    public Draft draft() {
        return draft;
    }

    @Override
    protected void draw() {
        Gui.fillAll(this, 3);
        set(13, Gui.item(Material.PAPER, plugin.messages().item("gui.confirm.question"), description), (Runnable) null);
        set(11, Gui.button(plugin, Material.LIME_CONCRETE, "gui.confirm.accept"), onYes);
        set(15, Gui.button(plugin, Material.RED_CONCRETE, "gui.confirm.cancel"), onNo);
    }
}
