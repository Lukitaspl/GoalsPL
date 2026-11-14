package com.plgroup.goalspl.gui;

import com.plgroup.goalspl.GoalsPL;
import com.plgroup.goalspl.domain.CampaignValidator;
import com.plgroup.goalspl.domain.PersonalMilestone;
import com.plgroup.goalspl.domain.Reward;
import com.plgroup.goalspl.lang.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Hitos personales: recompensas que recibe cada jugador al llegar su aportación a una cantidad. Clic: sus
 * recompensas; mayúsculas + derecho: borrar.
 */
public final class PersonalMenu extends PagedMenu<PersonalMilestone> {

    private final Draft draft;

    public PersonalMenu(GoalsPL plugin, Player viewer, Draft draft) {
        super(plugin, viewer, plugin.messages().text("gui.personal.title"), 0);
        this.draft = draft;
    }

    @Override
    public Draft draft() {
        return draft;
    }

    @Override
    protected List<PersonalMilestone> entries() {
        return draft.campaign.personal;
    }

    @Override
    protected ItemStack icon(PersonalMilestone p) {
        var lore = new ArrayList<>(plugin.messages().lore("gui.personal.entry", Messages.ph("count", p.rewards.size())));
        List<String> lines = new ArrayList<>();
        for (Reward r : p.rewards) {
            lines.add("<gray>• " + Gui.reward(plugin, r));
        }
        lore.addAll(Gui.lines(lines));
        return Gui.item(Material.PLAYER_HEAD, plugin.messages().item("gui.personal.name", Messages.ph("amount", p.amount)), lore);
    }

    @Override
    protected void clicked(PersonalMilestone p, ClickType type) {
        if (type == ClickType.SHIFT_RIGHT) {
            draft.campaign.personal.remove(p);
            refresh();
            return;
        }
        new RewardListMenu(plugin, viewer, draft, p.rewards,
                plugin.messages().text("gui.personal.name", Messages.ph("amount", p.amount)),
                () -> new PersonalMenu(plugin, viewer, draft).open()).open();
    }

    @Override
    protected void drawControls() {
        set(47, Gui.button(plugin, Material.OAK_SIGN, "gui.personal.add"), () -> ChatInput.ask(plugin, viewer, "gui.input.personal", text -> {
            try {
                long a = Long.parseLong(text.strip());
                if (a < 1 || a > CampaignValidator.MAX_GOAL) {
                    throw new NumberFormatException();
                }
                if (draft.campaign.personal(a) == null) {
                    draft.campaign.personal.add(new PersonalMilestone(a));
                    draft.campaign.sort();
                }
            } catch (NumberFormatException e) {
                plugin.messages().send(viewer, "gui.input.bad-range", Messages.ph("min", 1), Messages.ph("max", CampaignValidator.MAX_GOAL));
            }
            open();
        }, this::open));
    }

    @Override
    protected void back() {
        new EditorMenu(plugin, viewer, draft).open();
    }
}
