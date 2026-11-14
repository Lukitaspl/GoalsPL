package com.plgroup.goalspl.gui;

import com.plgroup.goalspl.GoalsPL;
import com.plgroup.goalspl.domain.Milestone;
import com.plgroup.goalspl.domain.Reward;
import com.plgroup.goalspl.domain.RewardPolicy;
import com.plgroup.goalspl.lang.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Hitos comunitarios (porcentajes del total). Clic izquierdo: recompensas; derecho: cambia a quién se entregan
 * (comunidad / participantes); con la rueda (clic central) o Q: activa o quita el anuncio; mayúsculas + derecho:
 * borra. Botones para añadir un porcentaje o los cuatro habituales (25, 50, 75 y 100 %).
 */
public final class MilestonesMenu extends PagedMenu<Milestone> {

    private final Draft draft;

    public MilestonesMenu(GoalsPL plugin, Player viewer, Draft draft) {
        super(plugin, viewer, plugin.messages().text("gui.milestones.title"), 0);
        this.draft = draft;
    }

    @Override
    public Draft draft() {
        return draft;
    }

    @Override
    protected List<Milestone> entries() {
        return draft.campaign.milestones;
    }

    @Override
    protected ItemStack icon(Milestone m) {
        var lore = new ArrayList<>(plugin.messages().lore("gui.milestones.entry",
                Messages.ph("policy", plugin.messages().raw("policy." + m.policy.name())),
                Messages.ph("announce", Gui.onOff(plugin, m.announce)), Messages.ph("count", m.rewards.size())));
        List<String> lines = new ArrayList<>();
        for (Reward r : m.rewards) {
            lines.add("<gray>• " + Gui.reward(plugin, r));
        }
        lore.addAll(Gui.lines(lines));
        return Gui.item(m.percent == 100 ? Material.NETHER_STAR : Material.BEACON,
                plugin.messages().item("gui.milestones.name", Messages.ph("percent", m.percent)), lore);
    }

    @Override
    protected void clicked(Milestone m, ClickType type) {
        switch (type) {
            case SHIFT_RIGHT -> {
                draft.campaign.milestones.remove(m);
                refresh();
            }
            case RIGHT -> {
                m.policy = m.policy == RewardPolicy.PARTICIPANTS ? RewardPolicy.COMMUNITY : RewardPolicy.PARTICIPANTS;
                refresh();
            }
            case MIDDLE, DROP -> {
                m.announce = !m.announce;
                refresh();
            }
            default -> new RewardListMenu(plugin, viewer, draft, m.rewards,
                    plugin.messages().text("gui.milestones.name", Messages.ph("percent", m.percent)),
                    () -> new MilestonesMenu(plugin, viewer, draft).open()).open();
        }
    }

    @Override
    protected void drawControls() {
        set(47, Gui.button(plugin, Material.OAK_SIGN, "gui.milestones.add"), () -> ChatInput.ask(plugin, viewer, "gui.input.percent", text -> {
            try {
                int p = Integer.parseInt(text.strip().replace("%", ""));
                if (p < 1 || p > 100) {
                    throw new NumberFormatException();
                }
                addPercent(p);
            } catch (NumberFormatException e) {
                plugin.messages().send(viewer, "gui.input.bad-range", Messages.ph("min", 1), Messages.ph("max", 100));
            }
            open();
        }, this::open));
        set(51, Gui.button(plugin, Material.BEACON, "gui.milestones.defaults"), () -> {
            for (int p : new int[]{25, 50, 75, 100}) {
                addPercent(p);
            }
            refresh();
        });
    }

    private void addPercent(int p) {
        if (draft.campaign.milestone(p) == null) {
            draft.campaign.milestones.add(new Milestone(p));
            draft.campaign.sort();
        }
    }

    @Override
    protected void back() {
        new EditorMenu(plugin, viewer, draft).open();
    }
}
