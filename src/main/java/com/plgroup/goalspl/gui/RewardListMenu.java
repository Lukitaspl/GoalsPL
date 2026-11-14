package com.plgroup.goalspl.gui;

import com.plgroup.goalspl.GoalsPL;
import com.plgroup.goalspl.config.RewardPresets;
import com.plgroup.goalspl.domain.Reward;
import com.plgroup.goalspl.domain.RewardType;
import com.plgroup.goalspl.lang.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;

/**
 * Lista de recompensas de un hito, hito personal o puesto. Añadir: se elige el tipo y se escribe el valor en el
 * chat, o se añade una plantilla de rewards.yml. Clic: cambiar el valor; mayúsculas + derecho: quitar. Las de
 * comandos de consola necesitan además {@code goalspl.commands}.
 */
public final class RewardListMenu extends PagedMenu<Reward> {

    private final Draft draft;
    private final List<Reward> rewards;
    private final Component label;
    private final Runnable onBack;

    public RewardListMenu(GoalsPL plugin, Player viewer, Draft draft, List<Reward> rewards, Component label, Runnable onBack) {
        super(plugin, viewer, plugin.messages().text("gui.reward-list.title", Messages.component("label", label)), 0);
        this.draft = draft;
        this.rewards = rewards;
        this.label = label;
        this.onBack = onBack;
    }

    private RewardListMenu again() {
        return new RewardListMenu(plugin, viewer, draft, rewards, label, onBack);
    }

    @Override
    public Draft draft() {
        return draft;
    }

    @Override
    protected List<Reward> entries() {
        return rewards;
    }

    @Override
    protected ItemStack icon(Reward r) {
        return Gui.item(icon(r.type()), plugin.messages().item("reward-type." + r.type().name()),
                plugin.messages().lore("gui.reward-list.entry", Messages.ph("value", r.value())));
    }

    static Material icon(RewardType t) {
        return switch (t) {
            case COMMAND, GLOBAL_COMMAND -> Material.COMMAND_BLOCK;
            case MESSAGE -> Material.PAPER;
            case ITEM -> Material.DIAMOND;
            case PERMISSION -> Material.TRIPWIRE_HOOK;
            case BROADCAST -> Material.BELL;
            case EVENT -> Material.FIREWORK_ROCKET;
        };
    }

    @Override
    protected void clicked(Reward r, ClickType type) {
        if (r.type().console() && !allowed("goalspl.commands")) {
            return;
        }
        int index = rewards.indexOf(r);
        if (type == ClickType.SHIFT_RIGHT) {
            rewards.remove(index);
            refresh();
            return;
        }
        ask(r.type(), value -> rewards.set(index, new Reward(r.type(), value)));
    }

    private void ask(RewardType type, java.util.function.Consumer<String> apply) {
        ChatInput.ask(plugin, viewer, "gui.input.reward", text -> {
            apply.accept(text);
            again().open();
        }, () -> again().open(), Messages.ph("type", plugin.messages().raw("reward-type." + type.name())),
                Messages.ph("help", plugin.messages().raw("reward-help." + type.name())));
    }

    @Override
    protected void drawControls() {
        set(47, Gui.button(plugin, Material.EMERALD, "gui.reward-list.add"), () -> new ChoiceMenu<>(plugin, viewer,
                plugin.messages().text("gui.reward-list.choose"), draft, Arrays.asList(RewardType.values()),
                t -> Gui.item(icon(t), plugin.messages().item("reward-type." + t.name()),
                        plugin.messages().lore("reward-lore." + t.name())),
                t -> {
                    if (t.console() && !allowed("goalspl.commands")) {
                        again().open();
                        return;
                    }
                    ask(t, value -> rewards.add(new Reward(t, value)));
                }, () -> again().open()).open());
        List<RewardPresets.Preset> presets = plugin.presets().all();
        if (!presets.isEmpty()) {
            set(51, Gui.button(plugin, Material.CHEST, "gui.reward-list.preset"), () -> new ChoiceMenu<>(plugin, viewer,
                    plugin.messages().text("gui.reward-list.choose"), draft, presets.subList(0, Math.min(45, presets.size())),
                    p -> Gui.item(Gui.material(p.icon()), Messages.mini(p.name()), Gui.lines(p.rewards().stream()
                            .map(r -> "<gray>• " + Gui.reward(plugin, r)).toList())),
                    p -> {
                        if (p.rewards().stream().anyMatch(r -> r.type().console()) && !allowed("goalspl.commands")) {
                            again().open();
                            return;
                        }
                        rewards.addAll(p.rewards());
                        again().open();
                    }, () -> again().open()).open());
        }
    }

    @Override
    protected void back() {
        onBack.run();
    }
}
