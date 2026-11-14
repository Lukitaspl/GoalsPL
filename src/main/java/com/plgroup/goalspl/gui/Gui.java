package com.plgroup.goalspl.gui;

import com.plgroup.goalspl.GoalsPL;
import com.plgroup.goalspl.domain.Campaign;
import com.plgroup.goalspl.domain.CampaignState;
import com.plgroup.goalspl.domain.ProgressMath;
import com.plgroup.goalspl.domain.Reward;
import com.plgroup.goalspl.lang.Messages;
import com.plgroup.goalspl.runtime.RunState;
import com.plgroup.goalspl.util.Durations;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Piezas comunes de los menús: botones con texto de messages.yml, relleno, volver, iconos de estado y barras. */
public final class Gui {

    private Gui() {
    }

    public static ItemStack item(Material material, Component name, List<Component> lore) {
        Material m = material == null || material == Material.AIR || !material.isItem() ? Material.PAPER : material;
        ItemStack item = new ItemStack(m);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Messages.noItalic(name));
            List<Component> l = new ArrayList<>();
            for (Component c : lore) {
                l.add(Messages.noItalic(c));
            }
            meta.lore(l);
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Botón con nombre {@code <key>.name} y descripción {@code <key>.lore}. */
    public static ItemStack button(GoalsPL plugin, Material material, String key, TagResolver... ph) {
        Messages m = plugin.messages();
        return item(material, m.item(key + ".name", ph), m.lore(key + ".lore", ph));
    }

    public static ItemStack filler() {
        return item(Material.GRAY_STAINED_GLASS_PANE, Component.empty(), List.of());
    }

    public static ItemStack back(GoalsPL plugin) {
        return button(plugin, Material.ARROW, "gui.common.back");
    }

    public static String onOff(GoalsPL plugin, boolean value) {
        return plugin.messages().raw(value ? "gui.common.enabled" : "gui.common.disabled");
    }

    public static Material stateIcon(CampaignState s) {
        return switch (s) {
            case DRAFT -> Material.PAPER;
            case ACTIVE -> Material.LIME_DYE;
            case PAUSED -> Material.YELLOW_DYE;
            case COMPLETED -> Material.EMERALD;
            case CANCELLED -> Material.RED_DYE;
            case EXPIRED -> Material.GRAY_DYE;
        };
    }

    /** Material del icono de una campaña (si el nombre no existe en esta versión, papel). */
    public static Material material(String name) {
        Material m = name == null ? null : Material.matchMaterial(name);
        return m == null || !m.isItem() || m == Material.AIR ? Material.PAPER : m;
    }

    /** Barra de progreso en texto MiniMessage: 20 rayas verdes y grises. */
    public static String bar(long progress, long goal) {
        int filled = Math.round(ProgressMath.fraction(progress, goal) * 20);
        return "<green>" + "|".repeat(filled) + "<dark_gray>" + "|".repeat(20 - filled);
    }

    /** Variables comunes de una campaña para los textos de los menús. */
    public static TagResolver[] campaign(GoalsPL plugin, Campaign c) {
        RunState s = plugin.manager().state(c.id);
        return new TagResolver[]{
                Messages.parsed("name", c.name),
                Messages.ph("id", c.id),
                Messages.parsed("description", c.description),
                Messages.ph("type", plugin.messages().raw("objective." + c.type.name())),
                Messages.ph("state", plugin.messages().raw("state." + s.state.name())),
                Messages.ph("progress", s.progress),
                Messages.ph("goal", c.goal),
                Messages.ph("percent", ProgressMath.percent(s.progress, c.goal)),
                Messages.parsed("bar", bar(s.progress, c.goal)),
                Messages.ph("time", plugin.manager().timeText(c)),
                Messages.ph("participants", s.ranking().size()),
                Messages.ph("run", s.run)
        };
    }

    /** Texto corto de una recompensa para lores. */
    public static String reward(GoalsPL plugin, Reward r) {
        return plugin.messages().raw("reward-type." + r.type().name()) + ": " + Messages.escape(r.value());
    }

    /** Rellena la fila de abajo de un menú con cristal. */
    public static void fillBottom(Menu menu, int rows) {
        for (int i = (rows - 1) * 9; i < rows * 9; i++) {
            menu.set(i, filler(), (Runnable) null);
        }
    }

    public static void fillAll(Menu menu, int rows) {
        for (int i = 0; i < rows * 9; i++) {
            menu.set(i, filler(), (Runnable) null);
        }
    }

    /** Convierte líneas MiniMessage en lore. */
    public static List<Component> lines(List<String> mini, TagResolver... ph) {
        List<Component> out = new ArrayList<>();
        for (String s : mini) {
            out.add(Messages.mini(s, ph));
        }
        return out;
    }
}
