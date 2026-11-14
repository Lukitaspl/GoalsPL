package com.plgroup.goalspl.gui;

import com.plgroup.goalspl.GoalsPL;
import com.plgroup.goalspl.domain.Campaign;
import com.plgroup.goalspl.domain.CampaignValidator;
import com.plgroup.goalspl.domain.Problem;
import com.plgroup.goalspl.lang.Messages;
import com.plgroup.goalspl.runtime.CampaignManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Revisión antes de publicar: resumen de la campaña, reglas, errores (impiden guardar) y avisos. «Guardar» la
 * escribe en goals.yml; «Guardar e iniciar» además la publica (si no está en curso y hay permiso).
 */
public final class SummaryMenu extends Menu {

    private final Draft draft;

    public SummaryMenu(GoalsPL plugin, Player viewer, Draft draft) {
        super(plugin, viewer, 4, plugin.messages().text("gui.summary.title", Messages.ph("id", draft.campaign.id)));
        this.draft = draft;
    }

    @Override
    public Draft draft() {
        return draft;
    }

    @Override
    protected void draw() {
        Campaign c = draft.campaign;
        Messages m = plugin.messages();
        Gui.fillAll(this, 4);
        List<Problem> problems = CampaignValidator.validate(c, plugin.env());
        boolean errors = problems.stream().anyMatch(Problem::error);

        List<Component> info = new ArrayList<>(m.lore("gui.summary.info", Messages.ph("id", c.id),
                Messages.ph("type", m.raw("objective." + c.type.name())), Messages.ph("goal", c.goal),
                Messages.ph("milestones", c.milestones.size()), Messages.ph("personal", c.personal.size()),
                Messages.ph("top", c.topRewards.size()), Messages.ph("next", c.next.isEmpty() ? "-" : c.next)));
        set(11, Gui.item(Gui.material(c.icon), Messages.mini(c.name), info), (Runnable) null);
        set(13, Gui.item(Material.BOOK, m.item("gui.campaign.rules"), CampaignMenu.rules(plugin, c)), (Runnable) null);

        List<Component> lines = new ArrayList<>();
        for (Problem p : problems) {
            lines.add(m.text(p.error() ? "gui.summary.error" : "gui.summary.warning",
                    Messages.component("text", m.text("validation." + p.key(), Messages.ph("detail", p.detail())))));
        }
        if (lines.isEmpty()) {
            lines.add(m.text("gui.summary.ok"));
        }
        set(15, Gui.item(errors ? Material.RED_STAINED_GLASS : problems.isEmpty() ? Material.LIME_STAINED_GLASS : Material.YELLOW_STAINED_GLASS,
                m.item("gui.summary.problems", Messages.ph("count", problems.size())), lines), (Runnable) null);

        set(27, Gui.back(plugin), () -> new EditorMenu(plugin, viewer, draft).open());
        if (errors) {
            set(31, Gui.button(plugin, Material.BARRIER, "gui.summary.blocked"), (Runnable) null);
            return;
        }
        set(31, Gui.button(plugin, Material.WRITABLE_BOOK, "gui.summary.save"), () -> save(false));
        boolean canStart = plugin.manager().definition(c.id) == null || plugin.manager().state(c.id).state.canStart();
        if (canStart) {
            set(33, Gui.button(plugin, Material.LIME_CONCRETE, "gui.summary.save-start"), () -> save(true));
        }
    }

    private void save(boolean start) {
        if (!allowed("goalspl.manage")) {
            return;
        }
        Campaign c = draft.campaign;
        CampaignManager.Result r = plugin.manager().save(c, draft.creating, viewer.getName(), ok -> {
            if (ok && viewer.isOnline()) {
                plugin.messages().send(viewer, "gui.summary.saved", Messages.ph("id", c.id));
            }
        });
        if (r != CampaignManager.Result.OK) {
            plugin.messages().send(viewer, "result." + r.name(), Messages.ph("id", c.id));
            return;
        }
        draft.finish();
        if (start) {
            CampaignManager.Result s = plugin.manager().start(c.id, viewer.getName(), null);
            plugin.messages().send(viewer, s == CampaignManager.Result.OK ? "result.done" : "result." + s.name(), Messages.ph("id", c.id));
        }
        new CampaignAdminMenu(plugin, viewer, c.id).open();
    }
}
