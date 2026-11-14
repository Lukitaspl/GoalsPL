package com.plgroup.goalspl.gui;

import com.plgroup.goalspl.GoalsPL;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Menú de inventario de GoalsPL. Se reconoce por su {@link InventoryHolder} (esta clase), nunca por el
 * título. {@link MenuListener} cancela todos los clics y arrastres (nadie mete, saca ni duplica iconos) y solo
 * pasa la casilla al menú si quien hace clic es su dueño.
 */
public abstract class Menu implements InventoryHolder {

    protected final GoalsPL plugin;
    protected final Player viewer;
    private final Inventory inventory;
    private final Map<Integer, Consumer<ClickType>> actions = new HashMap<>();

    protected Menu(GoalsPL plugin, Player viewer, int rows, Component title) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, rows * 9, title);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public UUID owner() {
        return viewer.getUniqueId();
    }

    /** Borrador al que pertenece el menú ({@code null} si no forma parte de una edición). */
    public Draft draft() {
        return null;
    }

    /** Dibuja el contenido (se llama al abrir y al refrescar). */
    protected abstract void draw();

    protected void set(int slot, ItemStack item, Consumer<ClickType> action) {
        inventory.setItem(slot, item);
        if (action == null) {
            actions.remove(slot);
        } else {
            actions.put(slot, action);
        }
    }

    protected void set(int slot, ItemStack item, Runnable action) {
        set(slot, item, action == null ? null : c -> action.run());
    }

    protected void clear() {
        inventory.clear();
        actions.clear();
    }

    public void refresh() {
        clear();
        draw();
    }

    void click(int slot, ClickType type) {
        Consumer<ClickType> action = actions.get(slot);
        if (action != null) {
            action.accept(type);
        }
    }

    public void open() {
        refresh();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (viewer.isOnline()) {
                viewer.openInventory(inventory);
            }
        });
    }

    /** Comprueba un permiso al pulsar un botón; si no lo tiene, avisa y devuelve {@code false}. */
    protected boolean allowed(String permission) {
        if (viewer.hasPermission(permission)) {
            return true;
        }
        plugin.messages().send(viewer, "no-permission");
        return false;
    }
}
