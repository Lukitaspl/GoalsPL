package com.plgroup.goalspl.domain;

import com.plgroup.goalspl.util.Durations;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Comprueba una campaña antes de guardarla o publicarla. Los errores impiden guardar; los avisos no. Lo que
 * depende del servidor (si existe una entidad, un bloque, un mundo…) se pregunta a {@link Env}, así el validador
 * se prueba sin servidor.
 */
public final class CampaignValidator {

    /** Lo que el validador necesita saber del servidor. */
    public interface Env {
        boolean entity(String name);

        boolean block(String name);

        boolean item(String name);

        boolean world(String name);

        boolean campaign(String id);

        boolean eventsPl();

        boolean blockedCommand(String command);

        long now();
    }

    public static final Set<String> COLORS = Set.of("PINK", "BLUE", "RED", "GREEN", "YELLOW", "PURPLE", "WHITE");
    public static final Set<String> OVERLAYS = Set.of("PROGRESS", "NOTCHED_6", "NOTCHED_10", "NOTCHED_12", "NOTCHED_20");
    public static final long MAX_GOAL = 1_000_000_000_000L;

    private CampaignValidator() {
    }

    public static List<Problem> validate(Campaign c, Env env) {
        List<Problem> out = new ArrayList<>();
        if (!Campaign.validId(c.id)) {
            out.add(Problem.error("id-invalid", c.id));
        }
        if (c.name == null || c.name.isBlank()) {
            out.add(Problem.error("name-empty", ""));
        }
        if (c.goal < 1 || c.goal > MAX_GOAL) {
            out.add(Problem.error("goal-invalid", String.valueOf(c.goal)));
        }
        if (!env.item(c.icon)) {
            out.add(Problem.warning("icon-unknown", c.icon));
        }
        if (c.type.needsTargets() && c.targets.isEmpty()) {
            out.add(Problem.error("targets-empty", ""));
        } else if (c.type.usesTargets() && c.targets.isEmpty()) {
            out.add(Problem.warning("targets-any", ""));
        }
        if (!c.type.usesTargets() && !c.targets.isEmpty()) {
            out.add(Problem.warning("targets-ignored", ""));
        }
        for (String t : c.targets) {
            boolean ok = switch (c.type) {
                case KILL_ENTITY -> env.entity(t);
                case BREAK_BLOCK -> env.block(t);
                case DELIVER_ITEM -> env.item(t);
                case ADMIN -> true;
            };
            if (!ok) {
                out.add(Problem.error("target-unknown", t));
            }
        }
        for (String w : c.worlds) {
            if (!env.world(w)) {
                out.add(Problem.warning("world-unknown", w));
            }
        }
        if (c.durationSeconds < 0) {
            out.add(Problem.error("duration-invalid", String.valueOf(c.durationSeconds)));
        }
        if (c.deadline > 0 && c.deadline <= env.now()) {
            out.add(Problem.warning("deadline-past", ""));
        }
        if (c.minContribution < 0) {
            out.add(Problem.error("min-invalid", String.valueOf(c.minContribution)));
        }
        if (c.maxContribution < 0) {
            out.add(Problem.error("max-invalid", String.valueOf(c.maxContribution)));
        }
        if (c.maxContribution > 0 && c.minContribution > c.maxContribution) {
            out.add(Problem.error("min-above-max", c.minContribution + " > " + c.maxContribution));
        }
        if (c.maxContribution > 0 && c.maxContribution < c.goal) {
            long players = (c.goal + c.maxContribution - 1) / c.maxContribution;
            out.add(Problem.warning("max-needs-players", String.valueOf(players)));
        }
        if (!c.next.isEmpty()) {
            if (c.next.equals(c.id)) {
                out.add(Problem.error("next-self", c.next));
            } else if (!env.campaign(c.next)) {
                out.add(Problem.warning("next-unknown", c.next));
            }
        }
        if (!COLORS.contains(c.bossbarColor)) {
            out.add(Problem.error("bossbar-color-invalid", c.bossbarColor));
        }
        if (!OVERLAYS.contains(c.bossbarOverlay)) {
            out.add(Problem.error("bossbar-overlay-invalid", c.bossbarOverlay));
        }
        List<Integer> seen = new ArrayList<>();
        for (Milestone m : c.milestones) {
            if (m.percent < 1 || m.percent > 100) {
                out.add(Problem.error("milestone-invalid", String.valueOf(m.percent)));
            }
            if (seen.contains(m.percent)) {
                out.add(Problem.error("milestone-duplicate", String.valueOf(m.percent)));
            }
            seen.add(m.percent);
            rewards(m.rewards, "" + m.percent + "%", env, out);
        }
        for (PersonalMilestone p : c.personal) {
            if (p.amount < 1) {
                out.add(Problem.error("personal-invalid", String.valueOf(p.amount)));
            } else if (c.maxContribution > 0 && p.amount > c.maxContribution) {
                out.add(Problem.warning("personal-unreachable", String.valueOf(p.amount)));
            }
            rewards(p.rewards, "#" + p.amount, env, out);
            for (Reward r : p.rewards) {
                if (!r.type().perPlayer()) {
                    out.add(Problem.warning("personal-global", r.serialize()));
                }
            }
        }
        c.topRewards.forEach((rank, list) -> {
            rewards(list, "top " + rank, env, out);
            for (Reward r : list) {
                if (!r.type().perPlayer()) {
                    out.add(Problem.warning("top-global", r.serialize()));
                }
            }
        });
        for (String text : List.of(c.startMessage, c.completeMessage)) {
            for (String unknown : Placeholders.unknown(text)) {
                out.add(Problem.warning("placeholder-unknown", "{" + unknown + "}"));
            }
        }
        if (c.milestones.isEmpty() && c.personal.isEmpty() && c.topRewards.isEmpty()) {
            out.add(Problem.warning("no-rewards", ""));
        }
        return out;
    }

    private static void rewards(List<Reward> rewards, String where, Env env, List<Problem> out) {
        for (Reward r : rewards) {
            String detail = where + ": " + r.serialize();
            if (r.value().isBlank()) {
                out.add(Problem.error("reward-empty", detail));
                continue;
            }
            for (String unknown : Placeholders.unknown(r.value())) {
                out.add(Problem.warning("placeholder-unknown", detail));
            }
            switch (r.type()) {
                case COMMAND, GLOBAL_COMMAND -> {
                    String cmd = r.value().startsWith("/") ? r.value().substring(1) : r.value();
                    if (env.blockedCommand(cmd)) {
                        out.add(Problem.error("command-blocked", detail));
                    }
                    if (r.type() == RewardType.GLOBAL_COMMAND && cmd.contains("{player}")) {
                        out.add(Problem.warning("global-player", detail));
                    }
                }
                case ITEM -> {
                    String[] parts = r.value().split("\\s+");
                    if (parts.length != 2 || !env.item(parts[0].toUpperCase(Locale.ROOT)) || parseAmount(parts[1]) < 1) {
                        out.add(Problem.error("item-invalid", detail));
                    }
                }
                case PERMISSION -> {
                    String[] parts = r.value().split("\\s+");
                    if (parts.length != 2 || !parts[0].matches("[A-Za-z0-9_.\\-*]+") || Durations.parse(parts[1]) <= 0) {
                        out.add(Problem.error("permission-invalid", detail));
                    }
                }
                case EVENT -> {
                    if (!env.eventsPl()) {
                        out.add(Problem.warning("events-missing", detail));
                    }
                }
                case MESSAGE, BROADCAST -> {
                }
            }
        }
    }

    public static int parseAmount(String s) {
        try {
            int n = Integer.parseInt(s.strip());
            return n > 0 && n <= 2304 ? n : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
