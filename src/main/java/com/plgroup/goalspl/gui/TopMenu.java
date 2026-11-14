package com.plgroup.goalspl.gui;

import com.plgroup.goalspl.GoalsPL;
import com.plgroup.goalspl.domain.Reward;
import com.plgroup.goalspl.lang.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Reconocimiento: recompensas por puesto en la clasificación al completar la campaña (1.º, 2.º…). Clic: sus
 * recompensas; mayúsculas + derecho: borrar el puesto.
 */
public final class TopMenu extends PagedMenu<Integer> {

    private final Draft draft;

    public TopMenu(GoalsPL plugin, Player viewer, Draft draft) {
        super(plugin, viewer, plugin.messages().text("gui.top.title"), 0);
        this.draft = draft;
    }

    @Override
    public Draft draft() {
        return draft;
    }

    @Override
    protected List<Integer> entries() {
        return new ArrayList<>(draft.campaign.topRewards.keySet());
    }

    @Override
    protected ItemStack icon(Integer rank) {
        List<Reward> rewards = draft.campaign.topRewards.get(rank);
        var lore = new ArrayList<>(plugin.messages().lore("gui.top.entry", Messages.ph("count", rewards.size())));
        List<String> lines = new ArrayList<>();
        for (Reward r : rewards) {
            lines.add("<gray>• " + Gui.reward(plugin, r));
        }
        lore.addAll(Gui.lines(lines));
        Material m = switch (rank) {
            case 1 -> Material.GOLD_BLOCK;
            case 2 -> Material.IRON_BLOCK;
            case 3 -> Material.COPPER_BLOCK;
            default -> Material.STONE;
        };
        return Gui.item(m, plugin.messages().item("gui.top.name", Messages.ph("rank", rank)), lore);
    }

    @Override
    protected void clicked(Integer rank, ClickType type) {
        if (type == ClickType.SHIFT_RIGHT) {
            draft.campaign.topRewards.remove(rank);
            refresh();
            return;
        }
        new RewardListMenu(plugin, viewer, draft, draft.campaign.topRewards.get(rank),
                plugin.messages().text("gui.top.name", Messages.ph("rank", rank)),
                () -> new TopMenu(plugin, viewer, draft).open()).open();
    }

    @Override
    protected void drawControls() {
        set(47, Gui.button(plugin, Material.OAK_SIGN, "gui.top.add"), () -> ChatInput.ask(plugin, viewer, "gui.input.rank", text -> {
            try {
                int r = Integer.parseInt(text.strip());
                if (r < 1 || r > 45) {
                    throw new NumberFormatException();
                }
                draft.campaign.topRewards.putIfAbsent(r, new ArrayList<>());
            } catch (NumberFormatException e) {
                plugin.messages().send(viewer, "gui.input.bad-range", Messages.ph("min", 1), Messages.ph("max", 45));
            }
            open();
        }, this::open));
    }

    @Override
    protected void back() {
        new EditorMenu(plugin, viewer, draft).open();
    }
}
