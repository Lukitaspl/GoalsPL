package com.plgroup.goalspl.gui;

import com.plgroup.goalspl.GoalsPL;
import com.plgroup.goalspl.domain.Campaign;
import com.plgroup.goalspl.lang.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Panel de administración: todas las campañas con su estado, crear una nueva, historial general y recargar. */
public final class AdminMenu extends PagedMenu<Campaign> {

    public AdminMenu(GoalsPL plugin, Player viewer, int page) {
        super(plugin, viewer, plugin.messages().text("gui.admin.title"), page);
    }

    @Override
    protected List<Campaign> entries() {
        return new ArrayList<>(plugin.manager().definitions().values());
    }

    @Override
    protected ItemStack icon(Campaign c) {
        return Gui.item(Gui.material(c.icon), Messages.mini(c.name), plugin.messages().lore("gui.admin.entry", Gui.campaign(plugin, c)));
    }

    @Override
    protected void clicked(Campaign c, ClickType type) {
        new CampaignAdminMenu(plugin, viewer, c.id).open();
    }

    @Override
    protected void drawControls() {
        set(47, Gui.button(plugin, Material.EMERALD, "gui.admin.create"), () -> {
            if (!allowed("goalspl.manage")) {
                return;
            }
            ChatInput.ask(plugin, viewer, "gui.input.id", text -> {
                String id = Campaign.normalizeId(text);
                if (!Campaign.validId(id)) {
                    plugin.messages().send(viewer, "gui.input.bad-id");
                    open();
                    return;
                }
                if (plugin.manager().definition(id) != null) {
                    plugin.messages().send(viewer, "result.EXISTS");
                    open();
                    return;
                }
                new EditorMenu(plugin, viewer, new Draft(new Campaign(id), true)).open();
            }, this::open);
        });
        set(51, Gui.button(plugin, Material.WRITABLE_BOOK, "gui.admin.history"), () -> HistoryMenu.open(plugin, viewer, null,
                () -> new AdminMenu(plugin, viewer, page).open()));
        set(52, Gui.button(plugin, Material.REPEATER, "gui.admin.reload"), () -> {
            if (!allowed("goalspl.reload")) {
                return;
            }
            viewer.closeInventory();
            plugin.reloadAll(() -> plugin.messages().send(viewer, "command.reloaded"));
        });
    }

    @Override
    protected void back() {
        new MainMenu(plugin, viewer).open();
    }
}
