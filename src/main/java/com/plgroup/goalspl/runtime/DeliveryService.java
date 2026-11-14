package com.plgroup.goalspl.runtime;

import com.plgroup.goalspl.GoalsPL;
import com.plgroup.goalspl.domain.Campaign;
import com.plgroup.goalspl.domain.CampaignState;
import com.plgroup.goalspl.domain.HistoryAction;
import com.plgroup.goalspl.domain.ObjectiveType;
import com.plgroup.goalspl.domain.ProgressMath;
import com.plgroup.goalspl.domain.Source;
import com.plgroup.goalspl.storage.DeliveryRow;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Entrega segura de materiales. Los objetos nunca se ponen en el menú: el jugador elige cuántos entregar, lo
 * confirma, y entonces se vuelven a contar en su inventario y se quitan solo los que se aceptan. Solo valen objetos
 * simples (sin nombre, encantamientos, daño ni otros datos: {@link ItemStack#isSimilar} con uno nuevo), y solo de
 * las 36 casillas del inventario (nunca la armadura ni la segunda mano). Cada entrega se guarda al momento.
 */
public final class DeliveryService {

    private final GoalsPL plugin;

    public DeliveryService(GoalsPL plugin) {
        this.plugin = plugin;
    }

    /** Objetos simples de ese material en las 36 casillas del inventario. */
    public int count(Player p, Material m) {
        ItemStack plain = new ItemStack(m);
        int total = 0;
        ItemStack[] storage = p.getInventory().getStorageContents();
        for (ItemStack it : storage) {
            if (it != null && it.isSimilar(plain)) {
                total += it.getAmount();
            }
        }
        return total;
    }

    /** Cuánto puede entregar ahora: lo que tiene, lo que falta y lo que le queda hasta su máximo. */
    public long deliverable(Player p, Campaign c, Material m) {
        RunState s = plugin.manager().state(c.id);
        long room = ProgressMath.accepted(s.progress, c.goal, s.contribution(p.getUniqueId()), c.maxContribution, Long.MAX_VALUE);
        return Math.min(count(p, m), room);
    }

    /** Motivo por el que no puede entregar ({@code null} si puede): clave de messages.yml. */
    public String blocker(Player p, Campaign c, Material m) {
        RunState s = plugin.manager().state(c.id);
        if (c.type != ObjectiveType.DELIVER_ITEM) {
            return "deliver.not-delivery";
        }
        if (s.state != CampaignState.ACTIVE) {
            return "deliver.not-active";
        }
        if (!c.counts(m.name())) {
            return "deliver.not-accepted";
        }
        if (!c.countsInWorld(p.getWorld().getName())) {
            return "deliver.wrong-world";
        }
        if (!p.hasPermission("goalspl.contribute")) {
            return "no-permission";
        }
        return null;
    }

    /** Entrega hasta {@code requested} objetos; devuelve los entregados (0 si nada). */
    public long deliver(Player p, Campaign c, Material m, long requested) {
        if (blocker(p, c, m) != null || requested <= 0) {
            return 0;
        }
        long n = Math.min(requested, deliverable(p, c, m));
        if (n <= 0) {
            return 0;
        }
        long accepted = plugin.manager().contribute(c, p.getUniqueId(), p.getName(), n, Source.DELIVERY);
        if (accepted <= 0) {
            return 0;
        }
        long removed = remove(p.getInventory(), m, accepted);
        if (removed < accepted) {
            plugin.getLogger().severe("Entrega de " + p.getName() + " a " + c.id + ": se aceptaron " + accepted + " " + m
                    + " pero solo se pudieron quitar " + removed + "; se corrige el progreso.");
            plugin.manager().adjust(c.id, removed - accepted, false, p.getUniqueId(), p.getName(), CampaignManager.SYSTEM);
        }
        RunState s = plugin.manager().state(c.id);
        DeliveryRow row = new DeliveryRow(System.currentTimeMillis(), c.id, s.run, p.getUniqueId(), p.getName(), m.name(),
                removed, "DELIVERED");
        plugin.database().write("guardar una entrega de " + p.getName(), st -> st.insertDelivery(row));
        plugin.manager().saveNow(s);
        plugin.manager().history(c.id, p.getName(), HistoryAction.DELIVERY, removed + " " + m.name());
        return removed;
    }

    private static long remove(PlayerInventory inv, Material m, long amount) {
        ItemStack plain = new ItemStack(m);
        long left = amount;
        for (int slot = 0; slot < 36 && left > 0; slot++) {
            ItemStack it = inv.getItem(slot);
            if (it == null || !it.isSimilar(plain)) {
                continue;
            }
            int take = (int) Math.min(left, it.getAmount());
            if (take == it.getAmount()) {
                inv.setItem(slot, null);
            } else {
                it.setAmount(it.getAmount() - take);
                inv.setItem(slot, it);
            }
            left -= take;
        }
        return amount - left;
    }
}
