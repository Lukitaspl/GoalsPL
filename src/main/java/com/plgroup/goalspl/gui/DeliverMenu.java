package com.plgroup.goalspl.gui;

import com.plgroup.goalspl.GoalsPL;
import com.plgroup.goalspl.domain.Campaign;
import com.plgroup.goalspl.lang.Messages;
import com.plgroup.goalspl.runtime.RunState;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.List;

/**
 * Entrega de materiales: un icono por material pedido con cuántos tiene y cuántos puede entregar. Clic izquierdo
 * = 1, derecho = 16, con mayúsculas = todos los que pueda. Antes de quitar nada se pide confirmación, y al
 * confirmar se vuelve a contar (si algo cambió, se entrega lo que haya). Los objetos nunca entran en el menú.
 */
public final class DeliverMenu extends Menu {

    private final String id;

    public DeliverMenu(GoalsPL plugin, Player viewer, String id) {
        super(plugin, viewer, 6, plugin.messages().text("gui.deliver.title"));
        this.id = id;
    }

    @Override
    protected void draw() {
        Gui.fillBottom(this, 6);
        set(45, Gui.back(plugin), () -> new CampaignMenu(plugin, viewer, id).open());
        Campaign c = plugin.manager().definition(id);
        if (c == null) {
            set(22, Gui.button(plugin, Material.BARRIER, "gui.common.gone"), (Runnable) null);
            return;
        }
        RunState s = plugin.manager().state(id);
        set(49, Gui.item(Material.BOOK, plugin.messages().item("gui.campaign.rules"), CampaignMenu.rules(plugin, c)), (Runnable) null);
        set(53, Gui.item(Gui.material(c.icon), Messages.mini(c.name), plugin.messages().lore("gui.deliver.status",
                Gui.campaign(plugin, c)[0], Messages.ph("progress", s.progress), Messages.ph("goal", c.goal),
                Messages.ph("amount", s.contribution(viewer.getUniqueId())))), (Runnable) null);
        List<Material> materials = new ArrayList<>();
        for (String t : c.targets) {
            Material m = Material.matchMaterial(t);
            if (m != null && m.isItem()) {
                materials.add(m);
            }
        }
        for (int i = 0; i < materials.size() && i < 45; i++) {
            Material m = materials.get(i);
            int have = plugin.deliveries().count(viewer, m);
            long can = plugin.deliveries().deliverable(viewer, c, m);
            String blocker = plugin.deliveries().blocker(viewer, c, m);
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>(plugin.messages().lore("gui.deliver.entry",
                    Messages.ph("have", have), Messages.ph("can", can)));
            if (blocker != null) {
                lore.add(plugin.messages().text(blocker));
            }
            set(i, Gui.item(m, net.kyori.adventure.text.Component.translatable(m.translationKey()), lore),
                    type -> ask(c, m, type));
        }
    }

    private void ask(Campaign c, Material m, ClickType type) {
        String blocker = plugin.deliveries().blocker(viewer, c, m);
        if (blocker != null) {
            plugin.messages().send(viewer, blocker);
            return;
        }
        long can = plugin.deliveries().deliverable(viewer, c, m);
        long amount = type.isShiftClick() ? can : Math.min(can, type.isRightClick() ? 16 : 1);
        if (amount <= 0) {
            plugin.messages().send(viewer, "deliver.nothing");
            return;
        }
        long chosen = amount;
        new ConfirmMenu(plugin, viewer, plugin.messages().text("gui.deliver.confirm-title"),
                plugin.messages().lore("gui.deliver.confirm", Messages.ph("amount", chosen),
                        Messages.component("item", net.kyori.adventure.text.Component.translatable(m.translationKey())),
                        Messages.parsed("name", c.name)), null,
                () -> {
                    long done = plugin.deliveries().deliver(viewer, c, m, chosen);
                    if (done > 0) {
                        plugin.messages().send(viewer, "deliver.done", Messages.ph("amount", done),
                                Messages.component("item", net.kyori.adventure.text.Component.translatable(m.translationKey())));
                    } else {
                        plugin.messages().send(viewer, "deliver.nothing");
                    }
                    new DeliverMenu(plugin, viewer, id).open();
                },
                () -> new DeliverMenu(plugin, viewer, id).open()).open();
    }
}
