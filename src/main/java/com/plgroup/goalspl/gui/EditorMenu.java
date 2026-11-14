package com.plgroup.goalspl.gui;

import com.plgroup.goalspl.GoalsPL;
import com.plgroup.goalspl.domain.Campaign;
import com.plgroup.goalspl.domain.CampaignCodec;
import com.plgroup.goalspl.domain.CampaignValidator;
import com.plgroup.goalspl.domain.EndPolicy;
import com.plgroup.goalspl.domain.ObjectiveType;
import com.plgroup.goalspl.lang.Messages;
import com.plgroup.goalspl.util.Durations;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Editor de una campaña sobre un borrador ({@link Draft}): nombre, descripción, icono, tipo de objetivo, objetivos,
 * cantidad, mundos, duración, fecha límite, mínimo y máximo por jugador, qué pasa con lo entregado si vence,
 * etapa siguiente, hitos, hitos personales, clasificación, anuncios, mensajes y bossbar. Nada se guarda hasta
 * pulsar «Revisar» y confirmar en {@link SummaryMenu}; cerrar sin guardar descarta el borrador.
 */
public final class EditorMenu extends Menu {

    private static final List<String> COLORS = List.of("GREEN", "BLUE", "RED", "YELLOW", "PURPLE", "PINK", "WHITE");
    private static final List<String> OVERLAYS = List.of("PROGRESS", "NOTCHED_6", "NOTCHED_10", "NOTCHED_12", "NOTCHED_20");

    private final Draft draft;

    public EditorMenu(GoalsPL plugin, Player viewer, Draft draft) {
        super(plugin, viewer, 6, plugin.messages().text("gui.editor.title", Messages.ph("id", draft.campaign.id)));
        this.draft = draft;
    }

    @Override
    public Draft draft() {
        return draft;
    }

    private Campaign c() {
        return draft.campaign;
    }

    @Override
    protected void draw() {
        Campaign c = c();
        Messages m = plugin.messages();
        Gui.fillAll(this, 6);
        set(0, Gui.button(plugin, Material.NAME_TAG, "gui.editor.name", Messages.parsed("value", c.name)),
                () -> text("gui.input.name", t -> c.name = t));
        set(1, Gui.button(plugin, Material.BOOK, "gui.editor.description", Messages.parsed("value", c.description.isEmpty() ? "-" : c.description)),
                () -> text("gui.input.description", t -> c.description = t.equals("-") ? "" : t));
        set(2, Gui.button(plugin, Gui.material(c.icon), "gui.editor.icon", Messages.ph("value", c.icon)), () -> {
            ItemStack hand = viewer.getInventory().getItemInMainHand();
            if (!hand.getType().isAir()) {
                c.icon = hand.getType().name();
                refresh();
            } else {
                text("gui.input.icon", t -> {
                    Material mat = Material.matchMaterial(t.toUpperCase(Locale.ROOT));
                    if (mat == null || !mat.isItem()) {
                        plugin.messages().send(viewer, "gui.input.bad-material", Messages.ph("value", t));
                    } else {
                        c.icon = mat.name();
                    }
                });
            }
        });
        set(3, Gui.button(plugin, Material.TARGET, "gui.editor.type", Messages.ph("value", m.raw("objective." + c.type.name()))),
                () -> new ChoiceMenu<>(plugin, viewer, m.text("gui.editor.type.name", Messages.ph("value", "")), draft,
                        Arrays.asList(ObjectiveType.values()),
                        t -> Gui.item(Material.TARGET, m.item("objective." + t.name()), m.lore("objective-help." + t.name())),
                        t -> {
                            if (t != c.type) {
                                c.targets.clear();
                            }
                            c.type = t;
                            open();
                        }, this::open).open());
        if (c.type.usesTargets()) {
            set(4, Gui.button(plugin, Material.CHEST, "gui.editor.targets", Messages.ph("count", c.targets.size()),
                    Messages.ph("value", c.targets.isEmpty() ? m.raw("gui.common.any") : summary(c.targets))),
                    () -> new TargetsMenu(plugin, viewer, draft, 0).open());
        }
        set(5, Gui.button(plugin, Material.EXPERIENCE_BOTTLE, "gui.editor.goal", Messages.ph("value", c.goal)),
                () -> number("gui.input.goal", 1, CampaignValidator.MAX_GOAL, v -> c.goal = v));
        set(6, Gui.button(plugin, Material.GRASS_BLOCK, "gui.editor.worlds",
                Messages.ph("value", c.worlds.isEmpty() ? m.raw("gui.common.all-worlds") : summary(c.worlds))),
                () -> new WorldsMenu(plugin, viewer, draft).open());

        set(9, Gui.button(plugin, Material.CLOCK, "gui.editor.duration",
                Messages.ph("value", c.durationSeconds == 0 ? m.raw("gui.common.no-limit") : Durations.format(c.durationSeconds))),
                () -> text("gui.input.duration", t -> {
                    long secs = t.equals("-") ? 0 : Durations.parse(t);
                    if (secs < 0) {
                        plugin.messages().send(viewer, "gui.input.bad-duration", Messages.ph("value", t));
                    } else {
                        c.durationSeconds = secs;
                    }
                }));
        set(10, Gui.button(plugin, Material.COMPASS, "gui.editor.deadline",
                Messages.ph("value", c.deadline == 0 ? m.raw("gui.common.none") : CampaignCodec.formatDate(c.deadline, plugin.settings().zone))),
                () -> text("gui.input.deadline", t -> {
                    if (t.equals("-")) {
                        c.deadline = 0;
                        return;
                    }
                    long at = CampaignCodec.parseDate(t, plugin.settings().zone);
                    if (at < 0) {
                        plugin.messages().send(viewer, "gui.input.bad-date", Messages.ph("value", t));
                    } else {
                        c.deadline = at;
                    }
                }));
        set(11, Gui.button(plugin, Material.IRON_NUGGET, "gui.editor.min", Messages.ph("value", c.minContribution)),
                () -> number("gui.input.min", 0, CampaignValidator.MAX_GOAL, v -> c.minContribution = v));
        set(12, Gui.button(plugin, Material.GOLD_INGOT, "gui.editor.max",
                Messages.ph("value", c.maxContribution == 0 ? m.raw("gui.common.no-limit") : String.valueOf(c.maxContribution))),
                () -> number("gui.input.max", 0, CampaignValidator.MAX_GOAL, v -> c.maxContribution = v));
        set(13, Gui.button(plugin, c.endPolicy == EndPolicy.REFUND ? Material.HOPPER : Material.ENDER_CHEST, "gui.editor.end-policy",
                Messages.ph("value", m.raw("end-policy." + c.endPolicy.name()))), () -> {
            c.endPolicy = c.endPolicy == EndPolicy.KEEP ? EndPolicy.REFUND : EndPolicy.KEEP;
            refresh();
        });
        set(14, Gui.button(plugin, Material.ARROW, "gui.editor.next", Messages.ph("value", c.next.isEmpty() ? m.raw("gui.common.none") : c.next)),
                () -> text("gui.input.next", t -> {
                    String id = t.equals("-") ? "" : Campaign.normalizeId(t);
                    if (!id.isEmpty() && !Campaign.validId(id)) {
                        plugin.messages().send(viewer, "gui.input.bad-id");
                    } else {
                        c.next = id;
                    }
                }));

        set(18, Gui.button(plugin, Material.BEACON, "gui.editor.milestones", Messages.ph("count", c.milestones.size())),
                () -> new MilestonesMenu(plugin, viewer, draft).open());
        set(19, Gui.button(plugin, Material.PLAYER_HEAD, "gui.editor.personal", Messages.ph("count", c.personal.size())),
                () -> new PersonalMenu(plugin, viewer, draft).open());
        set(20, Gui.button(plugin, Material.GOLDEN_HELMET, "gui.editor.top", Messages.ph("count", c.topRewards.size())),
                () -> new TopMenu(plugin, viewer, draft).open());

        set(27, Gui.button(plugin, c.announceStart ? Material.LIME_DYE : Material.GRAY_DYE, "gui.editor.announce-start",
                Messages.ph("value", Gui.onOff(plugin, c.announceStart))), () -> {
            c.announceStart = !c.announceStart;
            refresh();
        });
        set(28, Gui.button(plugin, c.announceEnd ? Material.LIME_DYE : Material.GRAY_DYE, "gui.editor.announce-end",
                Messages.ph("value", Gui.onOff(plugin, c.announceEnd))), () -> {
            c.announceEnd = !c.announceEnd;
            refresh();
        });
        set(29, Gui.button(plugin, Material.OAK_SIGN, "gui.editor.start-message",
                Messages.parsed("value", c.startMessage.isEmpty() ? m.raw("gui.common.default") : c.startMessage)),
                () -> text("gui.input.message", t -> c.startMessage = t.equals("-") ? "" : t));
        set(30, Gui.button(plugin, Material.OAK_SIGN, "gui.editor.complete-message",
                Messages.parsed("value", c.completeMessage.isEmpty() ? m.raw("gui.common.default") : c.completeMessage)),
                () -> text("gui.input.message", t -> c.completeMessage = t.equals("-") ? "" : t));
        set(32, Gui.button(plugin, c.bossbar ? Material.DRAGON_HEAD : Material.SKELETON_SKULL, "gui.editor.bossbar",
                Messages.ph("value", Gui.onOff(plugin, c.bossbar))), () -> {
            c.bossbar = !c.bossbar;
            refresh();
        });
        set(33, Gui.button(plugin, Material.WHITE_DYE, "gui.editor.bossbar-color", Messages.ph("value", c.bossbarColor)), () -> {
            c.bossbarColor = cycle(COLORS, c.bossbarColor);
            refresh();
        });
        set(34, Gui.button(plugin, Material.IRON_BARS, "gui.editor.bossbar-overlay", Messages.ph("value", c.bossbarOverlay)), () -> {
            c.bossbarOverlay = cycle(OVERLAYS, c.bossbarOverlay);
            refresh();
        });

        set(45, Gui.button(plugin, Material.BARRIER, "gui.editor.discard"), () -> {
            draft.finish();
            plugin.messages().send(viewer, draft.creating ? "gui.draft.create-cancelled" : "gui.draft.edit-cancelled");
            new AdminMenu(plugin, viewer, 0).open();
        });
        set(53, Gui.button(plugin, Material.WRITABLE_BOOK, "gui.editor.review"), () -> new SummaryMenu(plugin, viewer, draft).open());
    }

    private static String cycle(List<String> list, String current) {
        int i = list.indexOf(current);
        return list.get((i + 1) % list.size());
    }

    private static String summary(List<String> list) {
        return list.size() <= 3 ? String.join(", ", list) : String.join(", ", list.subList(0, 3)) + " +" + (list.size() - 3);
    }

    /** Pide un texto por el chat, lo aplica y vuelve al editor. */
    void text(String prompt, Consumer<String> apply) {
        ChatInput.ask(plugin, viewer, prompt, t -> {
            apply.accept(t);
            open();
        }, this::open);
    }

    /** Pide un número entre {@code min} y {@code max}, lo aplica y vuelve al editor. */
    void number(String prompt, long min, long max, java.util.function.LongConsumer apply) {
        ChatInput.ask(plugin, viewer, prompt, t -> {
            try {
                long v = Long.parseLong(t.strip());
                if (v < min || v > max) {
                    throw new NumberFormatException();
                }
                apply.accept(v);
            } catch (NumberFormatException e) {
                plugin.messages().send(viewer, "gui.input.bad-range", Messages.ph("min", min), Messages.ph("max", max));
            }
            open();
        }, this::open);
    }

    /** Lista separada por espacios o comas, en mayúsculas. */
    static List<String> split(String text) {
        List<String> out = new ArrayList<>();
        for (String s : text.split("[,\\s]+")) {
            if (!s.isBlank()) {
                out.add(s.strip().toUpperCase(Locale.ROOT));
            }
        }
        return out;
    }
}
