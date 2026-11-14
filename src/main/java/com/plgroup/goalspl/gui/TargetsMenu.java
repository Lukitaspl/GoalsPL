package com.plgroup.goalspl.gui;

import com.plgroup.goalspl.GoalsPL;
import com.plgroup.goalspl.domain.Campaign;
import com.plgroup.goalspl.domain.ObjectiveType;
import com.plgroup.goalspl.lang.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Objetivos de la campaña (entidades, bloques u objetos). Se añaden escribiéndolos en el chat (varios separados
 * por espacios o comas) o con el objeto de la mano; los que no existen se rechazan con un aviso. Mayúsculas + clic
 * derecho quita uno.
 */
public final class TargetsMenu extends PagedMenu<String> {

    private final Draft draft;

    public TargetsMenu(GoalsPL plugin, Player viewer, Draft draft, int page) {
        super(plugin, viewer, plugin.messages().text("gui.targets.title"), page);
        this.draft = draft;
    }

    @Override
    public Draft draft() {
        return draft;
    }

    @Override
    protected List<String> entries() {
        return draft.campaign.targets;
    }

    @Override
    protected ItemStack icon(String t) {
        Material m = Material.matchMaterial(t);
        Material icon = m != null && m.isItem() ? m : Material.matchMaterial(t + "_SPAWN_EGG");
        return Gui.item(icon == null ? Material.NAME_TAG : icon, Messages.mini("<yellow>" + Messages.escape(t)),
                plugin.messages().lore("gui.targets.entry"));
    }

    @Override
    protected void clicked(String t, ClickType type) {
        if (type == ClickType.SHIFT_RIGHT) {
            draft.campaign.targets.remove(t);
            refresh();
        }
    }

    @Override
    protected void drawControls() {
        Campaign c = draft.campaign;
        set(47, Gui.button(plugin, Material.OAK_SIGN, "gui.targets.add"), () -> ChatInput.ask(plugin, viewer, "gui.input.targets", text -> {
            add(EditorMenu.split(text));
            open();
        }, this::open));
        if (c.type != ObjectiveType.KILL_ENTITY) {
            set(51, Gui.button(plugin, Material.HOPPER, "gui.targets.hand"), () -> {
                ItemStack hand = viewer.getInventory().getItemInMainHand();
                if (hand.getType().isAir()) {
                    plugin.messages().send(viewer, "gui.targets.empty-hand");
                    return;
                }
                add(List.of(hand.getType().name()));
                refresh();
            });
        }
        set(52, Gui.button(plugin, Material.LAVA_BUCKET, "gui.targets.clear"), () -> {
            c.targets.clear();
            refresh();
        });
    }

    private void add(List<String> names) {
        Campaign c = draft.campaign;
        List<String> rejected = new ArrayList<>();
        for (String n : names) {
            boolean ok = switch (c.type) {
                case KILL_ENTITY -> plugin.env().entity(n);
                case BREAK_BLOCK -> plugin.env().block(n);
                case DELIVER_ITEM -> plugin.env().item(n);
                case ADMIN -> false;
            };
            if (!ok) {
                rejected.add(n);
            } else if (!c.targets.contains(n)) {
                c.targets.add(n);
            }
        }
        if (!rejected.isEmpty()) {
            plugin.messages().send(viewer, "gui.targets.rejected", Messages.ph("value", String.join(", ", rejected)));
        }
    }

    @Override
    protected void back() {
        new EditorMenu(plugin, viewer, draft).open();
    }
}
