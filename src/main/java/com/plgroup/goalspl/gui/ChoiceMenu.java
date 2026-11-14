package com.plgroup.goalspl.gui;

import com.plgroup.goalspl.GoalsPL;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/** Elegir una opción de una lista corta (tipo de objetivo, tipo de recompensa…). */
public final class ChoiceMenu<T> extends Menu {

    private final List<T> options;
    private final Function<T, ItemStack> icon;
    private final Consumer<T> onPick;
    private final Runnable onBack;
    private final Draft draft;

    public ChoiceMenu(GoalsPL plugin, Player viewer, Component title, Draft draft, List<T> options,
                      Function<T, ItemStack> icon, Consumer<T> onPick, Runnable onBack) {
        super(plugin, viewer, Math.max(2, (options.size() + 8) / 9 + 1), title);
        this.options = options;
        this.icon = icon;
        this.onPick = onPick;
        this.onBack = onBack;
        this.draft = draft;
    }

    @Override
    public Draft draft() {
        return draft;
    }

    @Override
    protected void draw() {
        int rows = getInventory().getSize() / 9;
        Gui.fillBottom(this, rows);
        for (int i = 0; i < options.size() && i < (rows - 1) * 9; i++) {
            T option = options.get(i);
            set(i, icon.apply(option), () -> onPick.accept(option));
        }
        set((rows - 1) * 9, Gui.back(plugin), onBack);
    }
}
