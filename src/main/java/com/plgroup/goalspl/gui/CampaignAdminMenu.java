package com.plgroup.goalspl.gui;

import com.plgroup.goalspl.GoalsPL;
import com.plgroup.goalspl.domain.Campaign;
import com.plgroup.goalspl.domain.CampaignState;
import com.plgroup.goalspl.lang.Messages;
import com.plgroup.goalspl.runtime.CampaignManager;
import com.plgroup.goalspl.runtime.RunState;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.function.Supplier;

/**
 * Panel de una campaña: estado y progreso en vivo, iniciar / pausar / reanudar / completar / cancelar (con
 * confirmación las que no se pueden deshacer), editar, clonar, ajustar progreso, aportaciones, recompensas
 * entregadas o pendientes, historial y borrar. Cada botón comprueba su permiso al pulsarlo.
 */
public final class CampaignAdminMenu extends Menu {

    private final String id;
    private BukkitTask task;

    public CampaignAdminMenu(GoalsPL plugin, Player viewer, String id) {
        super(plugin, viewer, 5, plugin.messages().text("gui.campaign-admin.title", Messages.ph("id", id)));
        this.id = id;
    }

    @Override
    protected void draw() {
        Gui.fillAll(this, 5);
        set(36, Gui.back(plugin), () -> new AdminMenu(plugin, viewer, 0).open());
        Campaign c = plugin.manager().definition(id);
        if (c == null) {
            set(22, Gui.button(plugin, Material.BARRIER, "gui.common.gone"), (Runnable) null);
            return;
        }
        RunState s = plugin.manager().state(id);
        set(4, Gui.item(Gui.material(c.icon), Messages.mini(c.name), plugin.messages().lore("gui.admin.entry",
                Gui.campaign(plugin, c))), (Runnable) null);

        if (s.state.canStart()) {
            set(10, Gui.button(plugin, Material.LIME_CONCRETE, "gui.campaign-admin.start"), () -> act("goalspl.manage",
                    () -> plugin.manager().start(id, viewer.getName(), null)));
        } else if (s.state == CampaignState.ACTIVE) {
            set(10, Gui.button(plugin, Material.YELLOW_CONCRETE, "gui.campaign-admin.pause"), () -> act("goalspl.manage",
                    () -> plugin.manager().pause(id, viewer.getName())));
        } else {
            set(10, Gui.button(plugin, Material.LIME_CONCRETE, "gui.campaign-admin.resume"), () -> act("goalspl.manage",
                    () -> plugin.manager().resume(id, viewer.getName())));
        }
        if (s.state.running()) {
            set(11, Gui.button(plugin, Material.EMERALD_BLOCK, "gui.campaign-admin.complete"), () -> confirm("goalspl.manage",
                    "gui.campaign-admin.confirm-complete", () -> plugin.manager().complete(id, viewer.getName())));
            set(12, Gui.button(plugin, Material.RED_CONCRETE, "gui.campaign-admin.cancel"), () -> confirm("goalspl.cancel",
                    "gui.campaign-admin.confirm-cancel", () -> plugin.manager().cancel(id, viewer.getName())));
            set(14, Gui.button(plugin, Material.COMPARATOR, "gui.campaign-admin.adjust"), () -> {
                if (!allowed("goalspl.progress")) {
                    return;
                }
                ChatInput.ask(plugin, viewer, "gui.input.adjust", text -> {
                    String t = text.strip().replace(" ", "");
                    boolean set = t.startsWith("=");
                    try {
                        long v = Long.parseLong(set ? t.substring(1) : t.replace("+", ""));
                        report(plugin.manager().adjust(id, v, set, null, null, viewer.getName()));
                    } catch (NumberFormatException e) {
                        plugin.messages().send(viewer, "gui.input.bad-number");
                    }
                    open();
                }, this::open);
            });
        }
        set(16, Gui.button(plugin, Material.WRITABLE_BOOK, "gui.campaign-admin.edit"), () -> {
            if (!allowed("goalspl.manage")) {
                return;
            }
            if (s.state.running()) {
                plugin.messages().send(viewer, "gui.campaign-admin.editing-running");
            }
            new EditorMenu(plugin, viewer, new Draft(c.copy(), false)).open();
        });
        set(19, Gui.button(plugin, Material.PLAYER_HEAD, "gui.campaign-admin.contributions", Messages.ph("count", s.ranking().size())),
                () -> new ContributionsMenu(plugin, viewer, id, 0).open());
        set(21, Gui.button(plugin, Material.CHEST, "gui.campaign-admin.rewards"), () -> RewardLogMenu.open(plugin, viewer, id));
        set(23, Gui.button(plugin, Material.BOOK, "gui.campaign-admin.history"), () -> HistoryMenu.open(plugin, viewer, id,
                () -> new CampaignAdminMenu(plugin, viewer, id).open()));
        set(25, Gui.button(plugin, Material.PAPER, "gui.campaign-admin.clone"), () -> {
            if (!allowed("goalspl.manage")) {
                return;
            }
            ChatInput.ask(plugin, viewer, "gui.input.id", text -> {
                String newId = Campaign.normalizeId(text);
                if (!Campaign.validId(newId)) {
                    plugin.messages().send(viewer, "gui.input.bad-id");
                } else if (plugin.manager().definition(newId) != null) {
                    plugin.messages().send(viewer, "result.EXISTS");
                } else {
                    new EditorMenu(plugin, viewer, new Draft(c.copy(newId), true)).open();
                    return;
                }
                open();
            }, this::open);
        });
        set(44, Gui.button(plugin, Material.LAVA_BUCKET, "gui.campaign-admin.delete"), () -> confirm("goalspl.manage",
                "gui.campaign-admin.confirm-delete", () -> plugin.manager().delete(id, viewer.getName(), ok -> {
                })));
    }

    private void act(String permission, Supplier<CampaignManager.Result> action) {
        if (!allowed(permission)) {
            return;
        }
        report(action.get());
        refresh();
    }

    private void confirm(String permission, String key, Supplier<CampaignManager.Result> action) {
        if (!allowed(permission)) {
            return;
        }
        new ConfirmMenu(plugin, viewer, plugin.messages().text("gui.confirm.title"), plugin.messages().lore(key, Messages.ph("id", id)),
                null, () -> {
            CampaignManager.Result r = action.get();
            report(r);
            if (plugin.manager().definition(id) == null) {
                new AdminMenu(plugin, viewer, 0).open();
            } else {
                new CampaignAdminMenu(plugin, viewer, id).open();
            }
        }, () -> new CampaignAdminMenu(plugin, viewer, id).open()).open();
    }

    private void report(CampaignManager.Result r) {
        plugin.messages().send(viewer, r == CampaignManager.Result.OK ? "result.done" : "result." + r.name(), Messages.ph("id", id));
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
