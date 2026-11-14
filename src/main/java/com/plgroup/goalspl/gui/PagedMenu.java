package com.plgroup.goalspl.gui;

import com.plgroup.goalspl.GoalsPL;
import com.plgroup.goalspl.lang.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.Consumer;

/**
 * Menú de 6 filas con una lista paginada (45 por página) y una fila de botones abajo: volver, anterior,
 * siguiente y los extra que añada cada menú ({@link #drawControls()}).
 */
public abstract class PagedMenu<T> extends Menu {

    protected static final int PER_PAGE = 45;
    protected int page;

    protected PagedMenu(GoalsPL plugin, Player viewer, Component title, int page) {
        super(plugin, viewer, 6, title);
        this.page = Math.max(0, page);
    }

    protected abstract List<T> entries();

    protected abstract ItemStack icon(T entry);

    protected abstract void clicked(T entry, ClickType type);

    /** Botones de la fila de abajo además de volver y las flechas (casillas 46, 47, 51, 52 y 53). */
    protected void drawControls() {
    }

    /** Qué hace «volver» (por defecto cerrar). */
    protected void back() {
        viewer.closeInventory();
    }

    @Override
    protected void draw() {
        List<T> list = entries();
        int pages = Math.max(1, (list.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.min(page, pages - 1);
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= list.size()) {
                break;
            }
            T entry = list.get(idx);
            Consumer<ClickType> action = c -> clicked(entry, c);
            set(i, icon(entry), action);
        }
        Gui.fillBottom(this, 6);
        if (list.isEmpty()) {
            set(22, Gui.button(plugin, Material.STRUCTURE_VOID, "gui.common.empty"), (Runnable) null);
        }
        set(45, Gui.back(plugin), this::back);
        if (page > 0) {
            set(48, Gui.button(plugin, Material.SPECTRAL_ARROW, "gui.common.previous", Messages.ph("page", page),
                    Messages.ph("pages", pages)), () -> {
                page--;
                refresh();
            });
        }
        set(49, Gui.item(Material.PAPER, plugin.messages().item("gui.common.page", Messages.ph("page", page + 1),
                Messages.ph("pages", pages), Messages.ph("count", list.size())), List.of()), (Runnable) null);
        if (page < pages - 1) {
            set(50, Gui.button(plugin, Material.SPECTRAL_ARROW, "gui.common.next", Messages.ph("page", page + 2),
                    Messages.ph("pages", pages)), () -> {
                page++;
                refresh();
            });
        }
        drawControls();
    }
}
