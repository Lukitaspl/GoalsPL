package com.plgroup.goalspl.gui;

import com.plgroup.goalspl.GoalsPL;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.logging.Level;

/**
 * Clics en los menús: se cancelan todos (también los del inventario del jugador, el doble clic que recoge y las
 * teclas numéricas), y solo se pasa la casilla al menú si el clic es en el menú y lo hace su dueño. Al cerrar un
 * menú de una edición sin abrir otro de la misma edición (ni estar escribiendo en el chat), se descarta el borrador.
 */
public final class MenuListener implements Listener {

    private final GoalsPL plugin;

    public MenuListener(GoalsPL plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder(false) instanceof Menu menu)) {
            return;
        }
        e.setCancelled(true);
        if (!e.getWhoClicked().getUniqueId().equals(menu.owner())) {
            return;
        }
        if (e.getClickedInventory() == e.getView().getTopInventory()) {
            try {
                menu.click(e.getSlot(), e.getClick());
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.SEVERE, "Error en un botón del menú " + menu.getClass().getSimpleName(), ex);
                plugin.messages().send(e.getWhoClicked(), "gui.error");
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder(false) instanceof Menu) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder(false) instanceof Menu menu) || !(e.getPlayer() instanceof Player p)) {
            return;
        }
        Draft draft = menu.draft();
        if (draft == null || draft.finished()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (draft.finished() || !p.isOnline() || ChatInput.waiting(p)) {
                return;
            }
            if (p.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu open && open.draft() == draft) {
                return;
            }
            draft.finish();
            plugin.messages().send(p, draft.creating ? "gui.draft.create-cancelled" : "gui.draft.edit-cancelled");
        });
    }
}
