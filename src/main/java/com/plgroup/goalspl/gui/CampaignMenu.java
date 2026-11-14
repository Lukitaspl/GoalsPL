package com.plgroup.goalspl.gui;

import com.plgroup.goalspl.GoalsPL;
import com.plgroup.goalspl.domain.Campaign;
import com.plgroup.goalspl.domain.Milestone;
import com.plgroup.goalspl.domain.ObjectiveType;
import com.plgroup.goalspl.domain.PersonalMilestone;
import com.plgroup.goalspl.domain.Reward;
import com.plgroup.goalspl.lang.Messages;
import com.plgroup.goalspl.runtime.RunState;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Detalle de una campaña para jugadores: progreso, hitos (cuáles se han alcanzado y a quién se entregan), mis
 * hitos personales, clasificación, reglas (mínimo, máximo, mundos, objetivos, qué pasa si vence) y, si es de
 * entregar materiales, el botón para entregar. Se redibuja cada segundo.
 */
public final class CampaignMenu extends Menu {

    private final String id;
    private BukkitTask task;

    public CampaignMenu(GoalsPL plugin, Player viewer, String id) {
        super(plugin, viewer, 5, plugin.messages().text("gui.campaign.title"));
        this.id = id;
    }

    @Override
    protected void draw() {
        Campaign c = plugin.manager().definition(id);
        Gui.fillAll(this, 5);
        set(36, Gui.back(plugin), () -> new GoalsMenu(plugin, viewer, 0).open());
        if (c == null) {
            set(22, Gui.button(plugin, Material.BARRIER, "gui.common.gone"), (Runnable) null);
            return;
        }
        RunState s = plugin.manager().state(id);
        UUID me = viewer.getUniqueId();
        set(4, Gui.item(Gui.material(c.icon), Messages.mini(c.name), plugin.messages().lore("gui.campaign.info",
                Gui.campaign(plugin, c))), (Runnable) null);

        List<Component> ms = new ArrayList<>();
        for (Milestone m : c.milestones) {
            boolean done = s.grants.contains("m:" + m.percent);
            ms.add(plugin.messages().text(done ? "gui.campaign.milestone-done" : "gui.campaign.milestone-todo",
                    Messages.ph("percent", m.percent), Messages.ph("policy", plugin.messages().raw("policy." + m.policy.name()))));
            for (Reward r : m.rewards) {
                ms.add(Messages.mini("<gray>  • " + Gui.reward(plugin, r)));
            }
        }
        if (ms.isEmpty()) {
            ms.add(plugin.messages().text("gui.campaign.none"));
        }
        set(20, Gui.item(Material.BEACON, plugin.messages().item("gui.campaign.milestones"), ms), (Runnable) null);

        List<Component> ps = new ArrayList<>(plugin.messages().lore("gui.campaign.personal-head",
                Messages.ph("amount", s.contribution(me)), Messages.ph("rank", s.rank(me))));
        for (PersonalMilestone p : c.personal) {
            boolean done = s.grants.contains("p:" + me + ":" + p.amount);
            ps.add(plugin.messages().text(done ? "gui.campaign.personal-done" : "gui.campaign.personal-todo",
                    Messages.ph("amount", p.amount)));
            for (Reward r : p.rewards) {
                ps.add(Messages.mini("<gray>  • " + Gui.reward(plugin, r)));
            }
        }
        set(22, Gui.item(Material.PLAYER_HEAD, plugin.messages().item("gui.campaign.personal"), ps), (Runnable) null);

        List<Component> top = new ArrayList<>();
        List<RunState.Contribution> ranking = s.ranking();
        int size = Math.min(plugin.settings().topSize, ranking.size());
        for (int i = 0; i < size; i++) {
            RunState.Contribution con = ranking.get(i);
            top.add(plugin.messages().text("gui.campaign.top-line", Messages.ph("rank", i + 1), Messages.ph("player", con.name),
                    Messages.ph("amount", con.amount)));
        }
        if (top.isEmpty()) {
            top.add(plugin.messages().text("gui.campaign.none"));
        }
        for (Map.Entry<Integer, List<Reward>> e : c.topRewards.entrySet()) {
            top.add(plugin.messages().text("gui.campaign.top-reward", Messages.ph("rank", e.getKey())));
            for (Reward r : e.getValue()) {
                top.add(Messages.mini("<gray>  • " + Gui.reward(plugin, r)));
            }
        }
        set(24, Gui.item(Material.GOLDEN_HELMET, plugin.messages().item("gui.campaign.top"), top), (Runnable) null);

        set(31, Gui.item(Material.BOOK, plugin.messages().item("gui.campaign.rules"), rules(plugin, c)), (Runnable) null);

        if (c.type == ObjectiveType.DELIVER_ITEM) {
            set(40, Gui.button(plugin, Material.HOPPER, "gui.campaign.deliver"), () -> {
                if (allowed("goalspl.contribute")) {
                    new DeliverMenu(plugin, viewer, id).open();
                }
            });
        }
    }

    /** Reglas visibles antes de participar (también en el menú de entrega). */
    static List<Component> rules(GoalsPL plugin, Campaign c) {
        Messages m = plugin.messages();
        List<Component> out = new ArrayList<>();
        out.add(m.text("gui.rules.type", Messages.ph("type", m.raw("objective." + c.type.name()))));
        if (c.type.usesTargets()) {
            out.add(m.text("gui.rules.targets", Messages.ph("targets", c.targets.isEmpty() ? m.raw("gui.common.any")
                    : String.join(", ", c.targets))));
        }
        out.add(m.text("gui.rules.worlds", Messages.ph("worlds", c.worlds.isEmpty() ? m.raw("gui.common.all-worlds")
                : String.join(", ", c.worlds))));
        out.add(m.text("gui.rules.min", Messages.ph("amount", c.minContribution)));
        out.add(m.text("gui.rules.max", Messages.ph("amount", c.maxContribution == 0 ? m.raw("gui.common.no-limit")
                : String.valueOf(c.maxContribution))));
        if (c.type == ObjectiveType.DELIVER_ITEM) {
            out.add(m.text(c.endPolicy == com.plgroup.goalspl.domain.EndPolicy.REFUND ? "gui.rules.refund" : "gui.rules.keep"));
            out.add(m.text("gui.rules.plain"));
        }
        return out;
    }

    @Override
    public void open() {
        super.open();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!viewer.isOnline() || viewer.getOpenInventory().getTopInventory().getHolder(false) != this) {
                task.cancel();
                return;
            }
            refresh();
        }, 20L, 20L);
    }
}
